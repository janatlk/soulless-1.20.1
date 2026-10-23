package com.soulless.client;

import com.soulless.Soulless;
import com.soulless.boss.LeviathanEntity;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * The Leviathan's model. On top of the keyframes (breathing, roar, bite) everything that should never stop moving is
 * procedural, from the entity's age:
 * <ul>
 *   <li>the body and tail: a travelling wave from neck to tail fin, growing toward the tail, as a swimming dragon's,
 *       and bending into its turns;</li>
 *   <li>the head tentacles: each a slow wave of its own, out of step with the others;</li>
 *   <li>the whiskers: no motion of their own - they trail the head with inertia;</li>
 *   <li>the eyes: shut like sliding doors, one after another from the front, both sides together.</li>
 * </ul>
 */
public class LeviathanModel extends DefaultedEntityGeoModel<LeviathanEntity> {
    private static final int SEGMENTS = 12, ARMS = 6, ARM_LINKS = 5, WHISKERS = 4, WHISKER_LINKS = 3;

    public LeviathanModel() {
        super(Soulless.id("leviathan"));
    }

    @Override
    public void setCustomAnimations(LeviathanEntity e, long instanceId, AnimationState<LeviathanEntity> state) {
        super.setCustomAnimations(e, instanceId, state);
        float t = e.tickCount + state.getPartialTick();
        float turn = Mth.lerp(state.getPartialTick(), e.turnO, e.turn);
        float d = Mth.DEG_TO_RAD;
        float roar = roarAmount(e, instanceId, t);

        // how fast it swims, smoothed; the wave runs faster and swings wider the harder it swims, and never quite stops
        float dtLive = Float.isNaN(e.lastLiveT) ? 0 : Mth.clamp(t - e.lastLiveT, 0, 3);
        e.lastLiveT = t;
        float speed = (float) Math.sqrt(Mth.square(e.getX() - e.xo) + Mth.square(e.getY() - e.yo) + Mth.square(e.getZ() - e.zo));
        e.swimSpeed += (Mth.clamp(speed / 0.6F, 0, 1.5F) - e.swimSpeed) * (1 - (float) Math.pow(0.92, dtLive));
        float effort = e.swimSpeed;
        e.swimPhase += dtLive * (0.06F + 0.16F * effort);
        float ph = e.swimPhase;
        // a slow, uneven drift of moods: sometimes it coils lazily, sometimes it shivers along its whole length
        float mood = 0.5F + 0.5F * Mth.sin(t * 0.011F) * Mth.sin(t * 0.0073F + 1.1F);

        // the body: a wave running back along it, bigger toward the tail, plus the bend of a turn
        // the body follows the path its head swam (the same points its hit boxes lie on): each segment turns by the
        // bend between it and the one before, and a smaller wave of its own rides on top
        var parts = e.bodyParts();
        float pt = state.getPartialTick();
        float prevYaw = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot), prevPitch = Mth.lerp(pt, e.xRotO, e.getXRot());
        net.minecraft.world.phys.Vec3 front = e.getPosition(pt).add(0, 1.5, 0);
        float dtBend = Float.isNaN(e.bendT) ? 100 : Mth.clamp(t - e.bendT, 0, 4);
        e.bendT = t;
        for (int i = 0; i < SEGMENTS; i++) {
            float f = (i + 1f) / SEGMENTS;
            var p = parts[i];
            net.minecraft.world.phys.Vec3 c = new net.minecraft.world.phys.Vec3(Mth.lerp(pt, p.xo, p.getX()),
                    Mth.lerp(pt, p.yo, p.getY()) + p.getBbHeight() / 2, Mth.lerp(pt, p.zo, p.getZ()));
            net.minecraft.world.phys.Vec3 dir = front.subtract(c);
            float yaw = dir.lengthSqr() < 1e-6 ? prevYaw : (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90;
            float pitch = dir.lengthSqr() < 1e-6 ? prevPitch : (float) (-Mth.atan2(dir.y, dir.horizontalDistance()) * Mth.RAD_TO_DEG);
            float bendY = Mth.clamp(Mth.wrapDegrees(yaw - prevYaw), -28, 28), bendX = Mth.clamp(pitch - prevPitch, -22, 22);
            // eased, so a jolt in the path (a bump on a block, a correction from the server) becomes a slow ripple
            float ease = 1 - (float) Math.pow(0.9, dtBend);
            e.bend[i * 2] += (bendX - e.bend[i * 2]) * ease;
            e.bend[i * 2 + 1] += (bendY - e.bend[i * 2 + 1]) * ease;
            bendX = e.bend[i * 2];
            bendY = e.bend[i * 2 + 1];
            float amp = (1F + 6F * f) * effort * (1 - 0.5F * roar);
            float wave = Mth.sin(ph * 1.5F - i * 0.55F) * amp + Mth.sin(t * 0.21F - i * 1.3F) * f * (0.6F + 1.4F * mood);
            float rise = Mth.sin(ph * 0.9F - i * 0.4F + 1.3F) * (0.8F + 2.6F * f) + Mth.sin(t * 0.037F - i * 0.25F) * 1.5F * f;
            // a roar heaves the front of the body: rearing back, then thrown forward, and rolling back down it
            float heave = i < 5 ? roar * (5 - i) * 1.6F * Mth.sin(Math.min(1, roar * 2) * Mth.PI - 0.6F) : 0;
            float roll = Mth.sin(ph * 1.5F - i * 0.55F - 1.2F) * (1.5F + 3F * f) * (0.5F + 0.5F * effort);
            add("body_" + i, (rise + heave + BEND_PITCH * bendX) * d, (wave + BEND_YAW * bendY) * d, roll * d);
            prevYaw = yaw;
            prevPitch = pitch;
            front = c;
        }
        // the fin lags the last segment and snaps through each stroke, with a flick now and then
        float flick = Math.max(0, Mth.sin(t * 0.023F + 2)) ;
        flick = flick > 0.97F ? (flick - 0.97F) / 0.03F : 0;
        add("tail_fin", Mth.sin(ph * 0.9F - SEGMENTS * 0.4F) * 6 * d,
                (Mth.sin(ph * 1.5F - SEGMENTS * 0.55F - 0.8F) * (16 + 10 * effort) + Mth.sin(t * 0.9F) * 18 * flick) * d, 0);

        // the head is never still: it glances about, and turns its gaze toward the nearest player near it
        CoreGeoBone head = getAnimationProcessor().getBone("head");
        if (head != null) {
            float gy = Mth.sin(t * 0.031F) * 6 + Mth.sin(t * 0.083F + 2) * 3, gp = Mth.sin(t * 0.027F + 1) * 4;
            var pl = e.level().getNearestPlayer(e, 28);
            if (pl != null) {
                var to = pl.getEyePosition(state.getPartialTick()).subtract(e.getEyePosition(state.getPartialTick()));
                float want = Mth.wrapDegrees((float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90 - Mth.rotLerp(pt, e.yRotO, e.getYRot()));
                gy = Mth.clamp(want, -35, 35);
                gp = Mth.clamp((float) (-Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG) - e.getXRot(), -20, 20);
            }
            float k = 1 - (float) Math.pow(0.9, dtLive);
            e.gazeYaw += (gy - e.gazeYaw) * k;
            e.gazePitch += (gp - e.gazePitch) * k;
            // on top of the keyframes (the idle keys the head every frame, so nothing piles up)
            head.setRotY(head.getRotY() + BEND_YAW * e.gazeYaw * d * (1 - roar));
            head.setRotX(head.getRotX() + BEND_PITCH * e.gazePitch * d * (1 - roar));
        }

        // the crown's breathing and curling are keyframed (the idle); on top: a drift of each arm's own, the swim
        // sweeping them back and a roar flaring them wide
        for (int a = 0; a < ARMS; a++) {
            float side = a % 2 == 0 ? 1 : -1, up = a < 2 ? 1 : a < 4 ? 0.3F : -1;
            for (int k = 0; k < ARM_LINKS; k++) {
                float f = (k + 1f) / ARM_LINKS;
                float sway = Mth.sin(t * 0.07F + a * 1.7F - k * 0.7F) * (1F + 3F * f);
                float lift = Mth.sin(t * 0.055F + a * 2.3F - k * 0.6F) * (3F + 6F * f);
                // a curl that creeps along the arm on its own slow clock: the tips coil in and uncoil
                float curl = Mth.sin(t * 0.045F + a * 0.9F) * k * 4F + Mth.sin(t * 0.17F + a * 3.1F - k) * 1.5F * f;
                // swimming hard sweeps them back; a roar flares the crown wide
                float back = effort * 10F * f, flare = roar * (24F - k * 3F);
                add("arm_" + a + "_" + k, (lift + curl - back * up) * d,
                        (sway + flare * side - back * side * 0.4F) * d, 0);
            }
        }

        // the whiskers do not move by themselves: they trail the head. Each link is a damped spring that lags behind
        // the head's turning (its body's yaw and pitch plus the keyframed head), and each link follows the one before,
        // so a turn sweeps them out and they swing back and settle; a little drag sweeps them back as it swims
        whiskers(e, t, state);

        // the eyes shut like sliding doors, the two leaves meeting in the middle - one eye after another from the front,
        // both sides together, every few seconds
        // (each blink cycle its own length, from a hash of its number, so they never fall into a rhythm; now and then a
        // double blink). A roar opens them all wide.
        long seed = e.getUUID().getLeastSignificantBits();
        float tb = t + Math.floorMod(seed, 97L);
        int cycle = (int) (tb / 70);
        int h = (int) ((cycle * 0x9E3779B1L + seed) >>> 7);
        float since = tb - cycle * 70 - Math.floorMod(h, 40);
        boolean twice = Math.floorMod(h >> 8, 5) == 0;
        for (int n = 0; n < 3; n++) {
            float s = since - n * 3.5F;
            if (twice && s > 9) s -= 9;
            if (roar > 0) s = -1;
            float shut = s < 0 ? 0 : s < 2.5F ? s / 2.5F : s < 4 ? 1 : s < 6.5F ? 1 - (s - 4) / 2.5F : 0;
            for (String side : new String[]{"l", "r"}) {
                for (String leaf : new String[]{"f", "b"}) {
                    CoreGeoBone door = getAnimationProcessor().getBone("door_" + side + n + "_" + leaf);
                    if (door != null) door.setScaleZ(Math.max(0.02F, shut));
                }
            }
        }
    }

    /** How far into its roar it is (0..1, 0 when not roaring), from the roar controller; it rises fast and fades. */
    private float roarAmount(LeviathanEntity e, long id, float t) {
        var ctrl = e.getAnimatableInstanceCache().getManagerForId(id).getAnimationControllers().get("roar");
        boolean on = ctrl != null && ctrl.getAnimationState() == software.bernie.geckolib.core.animation.AnimationController.State.RUNNING;
        if (!on) {
            e.roarStart = Float.NaN;
            return 0;
        }
        if (Float.isNaN(e.roarStart)) e.roarStart = t;
        float s = (t - e.roarStart) / 52F;   // the roar's 2.6 s
        if (s < 0.25F) return s / 0.25F * 0.4F;           // the wind-up
        if (s < 0.35F) return 0.4F + (s - 0.25F) / 0.1F * 0.6F;
        return s < 0.75F ? 1 : Math.max(0, 1 - (s - 0.75F) / 0.25F);
    }

    /** How a bend in the swum path turns a body bone (the sign maps world yaw/pitch onto the bone's rotation). */
    private static final float BEND_YAW = -1, BEND_PITCH = -1;
    private static final float WHISKER_GAIN = 5.5F, WHISKER_K = 0.16F, WHISKER_DAMP = 0.78F, WHISKER_MAX = 45;

    private void whiskers(LeviathanEntity e, float t, AnimationState<LeviathanEntity> state) {
        float ptw = state.getPartialTick();
        CoreGeoBone head = getAnimationProcessor().getBone("head");
        float headYaw = Mth.rotLerp(ptw, e.yRotO, e.getYRot()) - (head == null ? 0 : head.getRotY() * Mth.RAD_TO_DEG);
        float headPitch = Mth.lerp(ptw, e.xRotO, e.getXRot()) - (head == null ? 0 : head.getRotX() * Mth.RAD_TO_DEG);
        float dt = Float.isNaN(e.lastHeadYaw) ? 0 : Mth.clamp(t - e.lastWhiskerT, 0, 2);
        float wYaw = dt > 0 ? Mth.wrapDegrees(headYaw - e.lastHeadYaw) / dt : 0;
        float wPitch = dt > 0 ? (headPitch - e.lastHeadPitch) / dt : 0;
        e.lastHeadYaw = headYaw;
        e.lastHeadPitch = headPitch;
        e.lastWhiskerT = t;
        float drag = (float) e.getDeltaMovement().length() * 20;
        for (int w = 0; w < 6; w++) {
            int links = w < 4 ? WHISKER_LINKS : 4;
            float[] s = e.whisker[w];
            for (int k = 0; k < links; k++) {
                // what this link is pulled toward: against the head's turn, and after the link before it
                float pullP = -wPitch * WHISKER_GAIN + (w < 4 ? drag : -drag) * 0.6F + (k > 0 ? s[(k - 1) * 4] : 0);
                float pullY = -wYaw * WHISKER_GAIN + (k > 0 ? s[(k - 1) * 4 + 1] : 0);
                if (dt > 0) {
                    s[k * 4 + 2] = (s[k * 4 + 2] + (pullP - s[k * 4]) * WHISKER_K * dt) * (float) Math.pow(WHISKER_DAMP, dt);
                    s[k * 4 + 3] = (s[k * 4 + 3] + (pullY - s[k * 4 + 1]) * WHISKER_K * dt) * (float) Math.pow(WHISKER_DAMP, dt);
                    s[k * 4] = Mth.clamp(s[k * 4] + s[k * 4 + 2] * dt, -WHISKER_MAX, WHISKER_MAX);
                    s[k * 4 + 1] = Mth.clamp(s[k * 4 + 1] + s[k * 4 + 3] * dt, -WHISKER_MAX, WHISKER_MAX);
                }
                // each link carries only its share of the bend (the chain adds them up toward the tip)
                float share = 1F / links;
                addRest("whisker_" + w + "_" + k, s[k * 4] * share * Mth.DEG_TO_RAD, s[k * 4 + 1] * share * Mth.DEG_TO_RAD, 0);
            }
        }
    }

    /** Adds to whatever the keyframes did this frame: for bones the idle keys every frame (body, tail fin, arms). */
    private void add(String name, float x, float y, float z) {
        CoreGeoBone b = getAnimationProcessor().getBone(name);
        if (b == null) return;
        b.setRotX(b.getRotX() + x);
        b.setRotY(b.getRotY() + y);
        b.setRotZ(b.getRotZ() + z);
    }

    /** Sets the bone to its rest pose plus this: for bones no animation keys (the whiskers). */
    private void addRest(String name, float x, float y, float z) {
        CoreGeoBone b = getAnimationProcessor().getBone(name);
        if (b == null) return;
        var rest = b.getInitialSnapshot();
        b.setRotX(rest.getRotX() + x);
        b.setRotY(rest.getRotY() + y);
        b.setRotZ(rest.getRotZ() + z);
    }
}
