package com.soulless.boss;

import com.soulless.registry.ModEffects;
import com.soulless.registry.ModEntities;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * The Renegade: the sensei's former student, weak with the blade, who found forbidden dark magic instead and now wants
 * the Legendary Katana (STORY_KATANA.md, act 6). Fought in the dark astral (RenegadeEncounter). Seven attacks, every
 * one announced and every one parryable (KatanaParry treats any blow of his as a swung strike; his blades are
 * projectiles):
 * <ol>
 * <li>IAI - hand on the hilt, 1.5 s of stillness, gone, and behind the foe with a draw cut;</li>
 * <li>STEPS - three short blinks toward the foe, a red slash left at each that cuts 0.5 s later;</li>
 * <li>THRUST - drawn back at the hip, then a lunge straight through;</li>
 * <li>WHIRL - a leap with two turns onto a marked circle, a shockwave on landing;</li>
 * <li>BLADES - three red blades called over his head and hurled one by one;</li>
 * <li>COMBO - in close: kesa, gyaku, and straight down from overhead;</li>
 * <li>DROP - over the foe in the air, blade high, then down on a marked spot.</li>
 * </ol>
 * Gentle on purpose: low damage, long tells, pauses between attacks; the player cannot die here (RenegadeEncounter).
 * A parry staggers him; at low health he goes down on one knee and the fight ends.
 */
public class RenegadeEntity extends PathfinderMob implements GeoEntity {
    public static final float MAX_HEALTH = 400;
    /** He kneels, beaten, at this much health; nothing takes him lower. */
    private static final float DEFEAT_AT = 40;

    // animation ids (synced; the renderer picks the animation, a new start tick replays it)
    public static final int A_IDLE = 0, A_WALK = 1, A_TALK = 2, A_APPEAR = 3, A_IAI_READY = 4, A_IAI_STRIKE = 5,
            A_STEP1 = 6, A_STEP2 = 7, A_STEP3 = 8, A_THRUST = 9, A_WHIRL = 10, A_SUMMON = 11, A_COMBO = 12, A_DROP = 13,
            A_STAGGER = 14, A_DEFEAT = 15, A_WALK_BACK = 16, A_STRAFE_L = 17, A_STRAFE_R = 18;
    public static final String[] ANIMS = {"idle", "walk", "talk", "appear", "iai_ready", "iai_strike", "step_cut1", "step_cut2",
            "step_cut3", "thrust", "whirl", "summon", "combo", "drop", "stagger", "defeat", "walk_back", "strafe_l", "strafe_r"};
    /** How fast (blocks a tick) each stepping animation is made for: played faster or slower to match his real speed. */
    private static final double STEP_SPEED = 16.7 / 16 / 20, STRAFE_SPEED = 15.0 / 16 / 20;
    private static final EntityDataAccessor<Integer> ANIM = SynchedEntityData.defineId(RenegadeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ANIM_AT = SynchedEntityData.defineId(RenegadeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> HIDDEN = SynchedEntityData.defineId(RenegadeEntity.class, EntityDataSerializers.BOOLEAN);

    private enum Attack { IAI, STEPS, THRUST, WHIRL, BLADES, COMBO, DROP }


    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private UUID foe;
    /** Talking before the fight: no attacks until this runs out. */
    private int intro;
    @Nullable private Attack attack;
    @Nullable private Attack lastAttack;
    private int t;
    private int rest = 40;
    private boolean defeated;
    private boolean wasStunned;
    private Vec3 mark = Vec3.ZERO;
    private int strafeDir = 1;
    private final java.util.List<SpectralBladeEntity> blades = new java.util.ArrayList<>();

    public RenegadeEntity(EntityType<? extends RenegadeEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, MAX_HEALTH).add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 4).add(Attributes.KNOCKBACK_RESISTANCE, 0.7).add(Attributes.FOLLOW_RANGE, 48);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ANIM, A_IDLE);
        entityData.define(ANIM_AT, 0);
        entityData.define(HIDDEN, false);
    }

    @Override
    protected void registerGoals() {}

    /** The intro is over (the player answered him): the fight begins. */
    public void endIntro() {
        if (intro > 1) intro = 1;
    }

    /** The fight is over without his defeat (the player has lost): he stops, lowers the blade and talks. */
    public void standDown() {
        calm = true;
        endAttack();
        getNavigation().stop();
        talk();
    }

    private boolean calm;
    /** How long a parry stuns him (ticks). */
    private static final int STUN = 22;
    /** The chance he slips aside from a blow. */
    private static final float DODGE = 0.1F;
    /** The attacks not yet used this round: each comes once before any comes again. */
    private final java.util.List<Attack> bag = new java.util.ArrayList<>();

    public void setFoe(ServerPlayer p, int introTicks) {
        foe = p.getUUID();
        intro = introTicks;
        play(A_APPEAR);
    }

    @Nullable
    public Player foe() {
        return foe == null ? null : level().getPlayerByUUID(foe);
    }

    public boolean defeated() {
        return defeated;
    }

    public int anim() {
        return entityData.get(ANIM);
    }

    public int animStart() {
        return entityData.get(ANIM_AT);
    }

    public boolean hidden() {
        return entityData.get(HIDDEN);
    }

    private void play(int anim) {
        entityData.set(ANIM, anim);
        entityData.set(ANIM_AT, tickCount);
    }

    private void hide(boolean h) {
        entityData.set(HIDDEN, h);
    }

    // ---- a preview statue (/soulless preview renegade <animation>): stands still and replays one animation

    /** Animation lengths in ticks (tools/gen_renegade.py) - how long to wait before replaying a one-shot one. */
    private static final int[] LENGTH = {80, 12, 50, 25, 30, 18, 8, 8, 24, 38, 32, 48, 34, 38, 22, 60, 12, 12, 12};
    /** Whole attacks for the preview: the iai (ready, then the draw) and the three steps. */
    public static final String[] PREVIEWS = {"idle", "walk", "walk_back", "strafe_l", "strafe_r", "talk", "appear", "iai",
            "steps", "thrust", "whirl", "summon", "combo", "drop", "stagger", "defeat"};
    private String preview = "";
    private int previewStep;

    public void makePreview(String name) {
        preview = name;
        previewStep = 0;
        setInvulnerable(true);
    }

    public boolean isPreview() {
        return !preview.isEmpty();
    }

    private int[] previewSequence() {
        return switch (preview) {
            case "iai" -> new int[]{A_IAI_READY, A_IAI_STRIKE};
            case "steps" -> new int[]{A_STEP1, A_STEP2, A_STEP3};
            default -> {
                int i = java.util.Arrays.asList(ANIMS).indexOf(preview.equals("thrust") ? "thrust" : preview);
                yield new int[]{Math.max(0, i)};
            }
        };
    }

    private void previewTick() {
        int[] seq = previewSequence();
        int a = seq[previewStep % seq.length];
        boolean loop = loops(a);
        if (loop) {
            if (anim() != a) play(a);
            return;
        }
        // one-shot: play each step of the sequence, then a pause, and again
        if (tickCount - animStart() >= LENGTH[a] + (previewStep % seq.length == seq.length - 1 ? 25 : 0) || anim() != a && previewStep == 0) {
            if (anim() == a) previewStep++;
            play(seq[previewStep % seq.length]);
        }
    }

    // ---- the fight

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (isPreview()) {
            setDeltaMovement(Vec3.ZERO);
            previewTick();
            return;
        }
        setNoGravity(attack == Attack.DROP && t < 20);
        if (level.dimension() == RenegadeEncounter.LEVEL && getY() < FLOOR - 8) {
            moveTo(0.5, FLOOR, -4.5, getYRot(), getXRot());
            setDeltaMovement(Vec3.ZERO);
            fallDistance = 0;
        }
        Player p = foe();
        if (p == null || !p.isAlive() || defeated || calm) {
            getNavigation().stop();
            return;
        }
        getLookControl().setLookAt(p, 40, 40);
        if (intro > 0) {
            intro--;
            if (intro == 0) play(A_IDLE);
            return;
        }
        // a parry throws him back: whatever he was doing is lost
        boolean stunned = hasEffect(ModEffects.STUN.get());
        if (stunned && !wasStunned) {
            var st = getEffect(ModEffects.STUN.get());
            if (st != null && st.getDuration() > STUN) {
                removeEffect(ModEffects.STUN.get());
                forceAddEffect(new net.minecraft.world.effect.MobEffectInstance(ModEffects.STUN.get(), STUN, 0, false, false, true), null);
            }
            // parried: the cuts he left hanging in the air are gone too
            for (RedSlashEntity s : level.getEntitiesOfClass(RedSlashEntity.class, getBoundingBox().inflate(24), s -> s.from(this))) s.discard();
            endAttack();
            hide(false);
            play(A_STAGGER);
            level.sendParticles(com.soulless.registry.ModParticles.RED_SPARK.get(), getX(), getY() + 1.4, getZ(), 14, 0.3, 0.3, 0.3, 0.25);
        }
        wasStunned = stunned;
        if (stunned) return;
        if (attack != null) {
            attackTick(level, p);
            t++;
            return;
        }
        if (--rest > 0) {
            stalk(p);
            return;
        }
        startAttack(p);
    }

    /** Between attacks: circle the foe at a few blocks, slowly, guard up, always facing him - stepping forward, back or
     * sideways as he moves. */
    private void stalk(Player p) {
        Vec3 to = p.position().subtract(position());
        double d = to.horizontalDistance();
        Vec3 dir = new Vec3(to.x, 0, to.z).normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(strafeDir);
        Vec3 move = side.scale(1.2).add(dir.scale(d > 5.5 ? 1 : d < 3.5 ? -0.8 : 0));
        Vec3 want = position().add(move);
        getMoveControl().setWantedPosition(want.x, want.y, want.z, 0.45);
        if (random.nextInt(80) == 0) strafeDir = -strafeDir;
        // which way that is for him (he faces the foe): forward, back, to his right or his left
        double fwd = move.dot(dir);
        double right = move.dot(new Vec3(-dir.z, 0, dir.x));   // facing south (+z), his right is west (-x)
        int step = Math.abs(fwd) > Math.abs(right) * 1.1 ? (fwd > 0 ? A_WALK : A_WALK_BACK) : (right > 0 ? A_STRAFE_R : A_STRAFE_L);
        boolean stepping = anim() == A_WALK || anim() == A_WALK_BACK || anim() == A_STRAFE_L || anim() == A_STRAFE_R;
        if (anim() == A_STAGGER || anim() == A_APPEAR) return;
        if (!stepping || step != anim() && tickCount - animStart() > 8) play(step);
    }

    private static boolean loops(int a) {
        return a == A_IDLE || a == A_WALK || a == A_TALK || a == A_WALK_BACK || a == A_STRAFE_L || a == A_STRAFE_R;
    }

    /** He always squares up to whoever he faces: the body turns with the head, never toward where he walks. */
    @Override
    protected net.minecraft.world.entity.ai.control.BodyRotationControl createBodyControl() {
        return new net.minecraft.world.entity.ai.control.BodyRotationControl(this) {
            @Override
            public void clientTick() {
                yBodyRot = yHeadRot;
            }
        };
    }

    /** Scripted runs (the dev autotest): these attacks next, in this order (ordinals of the list in the class doc). */
    private final java.util.ArrayDeque<Integer> script = new java.util.ArrayDeque<>();

    public void script(int... attacks) {
        for (int a : attacks) script.add(a);
    }

    /** The attack under way (0..6, -1: none) and its tick. */
    public int attackId() {
        return attack == null ? -1 : attack.ordinal();
    }

    public int attackTick() {
        return t;
    }

    private void startAttack(Player p) {
        Attack[] all = Attack.values();
        Attack a;
        if (!script.isEmpty()) {
            a = all[script.poll()];
        } else {
            if (bag.isEmpty()) {
                bag.addAll(java.util.List.of(all));
                java.util.Collections.shuffle(bag, new java.util.Random(random.nextLong()));
                if (bag.get(0) == lastAttack) java.util.Collections.swap(bag, 0, bag.size() - 1);
            }
            a = bag.remove(0);
        }
        // close in for the combo first if far
        attack = a;
        lastAttack = a;
        t = 0;
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
    }

    private void endAttack() {
        attack = null;
        t = 0;
        rest = 14 + random.nextInt(14);   // a short breath between attacks
        hide(false);
        setNoGravity(false);
        for (SpectralBladeEntity b : blades) if (b.isAlive() && !b.launched()) b.discard();
        blades.clear();
        play(A_IDLE);
    }

    private void attackTick(ServerLevel level, Player p) {
        switch (attack) {
            case IAI -> iai(level, p);
            case STEPS -> steps(level, p);
            case THRUST -> thrust(level, p);
            case WHIRL -> whirl(level, p);
            case BLADES -> summon(level, p);
            case COMBO -> combo(level, p);
            case DROP -> drop(level, p);
        }
    }

    /** One blow at whoever stands within {@code reach} in front (within {@code arc} degrees off his facing). */
    private void strike(Player p, double reach, double arc, float damage) {
        swing(InteractionHand.MAIN_HAND);
        Vec3 to = p.position().subtract(position());
        double ang = Math.toDegrees(Math.acos(Mth.clamp(new Vec3(to.x, 0, to.z).normalize().dot(Vec3.directionFromRotation(0, getYRot())), -1, 1)));
        if (to.length() <= reach && ang <= arc) p.hurt(damageSources().mobAttack(this), damage);
    }

    /** A blow on everyone near a spot (landings, the slashes left behind). */
    void strikeAt(Player p, Vec3 at, double radius, float damage) {
        swing(InteractionHand.MAIN_HAND);
        if (p.position().distanceTo(at) <= radius) p.hurt(damageSources().mobAttack(this), damage);
    }

    private void face(Player p) {
        Vec3 to = p.position().subtract(position());
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90;
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
    }

    private void blink(ServerLevel level, Vec3 to) {
        if (level.dimension() == RenegadeEncounter.LEVEL && attack != Attack.DROP) to = new Vec3(to.x, FLOOR, to.z);
        level.sendParticles(com.soulless.registry.ModParticles.DARK_SMOKE.get(), getX(), getY() + 1, getZ(), 18, 0.3, 0.6, 0.3, 0);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8F, 0.6F);
        moveTo(to.x, to.y, to.z, getYRot(), getXRot());
        level.sendParticles(com.soulless.registry.ModParticles.DARK_SMOKE.get(), to.x, to.y + 1, to.z, 18, 0.3, 0.6, 0.3, 0);
    }

    /** A spot on the floor beside {@code from}, toward {@code dir}, {@code dist} blocks off. */
    private Vec3 beside(Vec3 from, Vec3 dir, double dist) {
        Vec3 d = new Vec3(dir.x, 0, dir.z).normalize();
        return new Vec3(from.x + d.x * dist, from.y, from.z + d.z * dist);
    }

    // 1. IAI: hand on the hilt, 1.5 s of stillness, then behind the foe with a draw cut
    private void iai(ServerLevel level, Player p) {
        if (t == 0) {
            face(p);
            play(A_IAI_READY);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.HOSTILE, 1, 0.6F);
        }
        if (t < 30) {
            getNavigation().stop();
            if (t % 3 == 0) level.sendParticles(com.soulless.registry.ModParticles.BLOOD_EMBER.get(), getX(), getY() + 0.9, getZ(), 2, 0.25, 0.15, 0.25, 0);
        }
        if (t == 30) hide(true);
        if (t == 32) {
            Vec3 behind = beside(p.position(), p.getLookAngle().scale(-1), 2.2);
            blink(level, behind);
            face(p);
            hide(false);
            play(A_IAI_STRIKE);
        }
        if (t == 35) {
            strike(p, 3.4, 110, 8);
            slashArc(level, 2.2);
        }
        if (t >= 50) endAttack();
    }

    // 2. STEPS: three blinks toward the foe, zigzagging; a red slash left where each cut was, biting 0.5 s later
    private void steps(ServerLevel level, Player p) {
        int k = t / STEP_EVERY;
        if (t % STEP_EVERY == 0 && k < 3) {
            hide(true);
            // round the foe: from where he stood, swung 70 degrees one way, then behind the foe, then 70 the other way
            Vec3 from = new Vec3(getX() - p.getX(), 0, getZ() - p.getZ());
            if (from.lengthSqr() < 1e-4) from = new Vec3(0, 0, 1);
            if (k == 0) stepsFrom = from.normalize();
            double ang = Math.toRadians(k == 0 ? 70 : k == 1 ? 180 : -70) * strafeDir;
            Vec3 d = stepsFrom;
            Vec3 at = new Vec3(d.x * Math.cos(ang) - d.z * Math.sin(ang), 0, d.x * Math.sin(ang) + d.z * Math.cos(ang)).scale(2.3);
            blink(level, p.position().add(at));
        }
        if (t % STEP_EVERY == 1 && k < 3) {
            hide(false);
            face(p);
            play(k == 0 ? A_STEP1 : k == 1 ? A_STEP2 : A_STEP3);
            swing(InteractionHand.MAIN_HAND);
            RedSlashEntity slash = ModEntities.RED_SLASH.get().create(level);
            if (slash != null) {
                Vec3 at = position().add(Vec3.directionFromRotation(0, getYRot()).scale(1.2));
                slash.setup(this, p, at, getYRot(), k * 50 - 50, 10, 5);
                level.addFreshEntity(slash);
            }
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 0.8F, 1.3F);
        }
        if (t >= STEP_EVERY * 2 + 1 + 24) endAttack();
    }

    /** The steps come this often (ticks): hidden one tick, then the cut. */
    private static final int STEP_EVERY = 7;
    private Vec3 stepsFrom = new Vec3(0, 0, 1);

    // 3. THRUST: drawn back at the hip, then a lunge straight through the foe
    private void thrust(ServerLevel level, Player p) {
        if (t == 0) {
            face(p);
            play(A_THRUST);
            mark = new Vec3(p.getX() - getX(), 0, p.getZ() - getZ()).normalize();
        }
        if (t < 18) {   // the gathering: 0.9 s
            face(p);
            mark = new Vec3(p.getX() - getX(), 0, p.getZ() - getZ()).normalize();
            if (t % 2 == 0) level.sendParticles(com.soulless.registry.ModParticles.BLOOD_EMBER.get(), getX(), getY() + 1, getZ(), 1, 0.2, 0.2, 0.2, 0);
        }
        if (t == 18) level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1, 0.8F);
        if (t >= 18 && t < 26) {
            setDeltaMovement(mark.x * 1.1, getDeltaMovement().y, mark.z * 1.1);
            level.sendParticles(com.soulless.registry.ModParticles.DARK_SMOKE.get(), getX(), getY() + 1, getZ(), 3, 0.2, 0.4, 0.2, 0);
            if (blowsLeft == 0 && t == 18) blowsLeft = 1;
            if (blowsLeft > 0 && distanceTo(p) < 1.8) {
                strike(p, 2.2, 120, 7);
                blowsLeft = 0;
            }
        }
        if (t == 26) setDeltaMovement(Vec3.ZERO);
        if (t >= 38) endAttack();
    }

    private int blowsLeft;

    // 4. WHIRL: a crouch, a leap with two turns onto the marked circle, a shockwave
    private void whirl(ServerLevel level, Player p) {
        if (t == 0) {
            face(p);
            play(A_WHIRL);
            mark = p.position();
        }
        if (t < 23 && t % 2 == 0) ring(level, mark, 2.8, com.soulless.registry.ModParticles.BLOOD_EMBER.get(), 24);
        if (t == 9) {
            Vec3 to = mark.subtract(position());
            setDeltaMovement(to.x / 13.0, 0.62, to.z / 13.0);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1, 0.7F);
        }
        if (t == 23) {
            strikeAt(p, mark, 2.9, 7);
            ring(level, mark, 2.9, com.soulless.registry.ModParticles.BLOOD_EMBER.get(), 48);
            level.sendParticles(com.soulless.registry.ModParticles.CRIMSON_SLASH.get(), mark.x, mark.y + 0.6, mark.z, 6, 1.4, 0.2, 1.4, 0);
            level.playSound(null, mark.x, mark.y, mark.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.5F, 1.6F);
        }
        if (t >= 32) endAttack();
    }

    // 5. BLADES: three red blades called over his head, hurled one by one
    private void summon(ServerLevel level, Player p) {
        if (t == 0) {
            face(p);
            play(A_SUMMON);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.HOSTILE, 1, 0.8F);
        }
        if (t == 10) {
            for (int i = 0; i < 3; i++) {
                SpectralBladeEntity b = ModEntities.SPECTRAL_BLADE.get().create(level);
                if (b == null) continue;
                b.hover(this, i);
                level.addFreshEntity(b);
                blades.add(b);
            }
        }
        for (int i = 0; i < blades.size(); i++) {
            if (t == 20 + i * 6) {
                face(p);
                blades.get(i).launch(p);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_THROW, SoundSource.HOSTILE, 1, 1.2F);
            }
        }
        if (t >= 48) {
            blades.clear();
            endAttack();
        }
    }

    // 6. COMBO: in close (a blink if needed), kesa, gyaku, then straight down
    private void combo(ServerLevel level, Player p) {
        if (t == 0) {
            if (distanceTo(p) > 2.6) {
                hide(true);
                Vec3 toMe = position().subtract(p.position());
                blink(level, beside(p.position(), toMe, 1.8));
            }
        }
        if (t == 3) {
            hide(false);
            face(p);
            play(A_COMBO);
        }
        if (t > 3 && t < 30) face(p);
        if (t == 3 + 7) {
            strike(p, 3.2, 80, 5);
            slashArc(level, 1.8);
        }
        if (t == 3 + 15) {
            strike(p, 3.2, 80, 5);
            slashArc(level, 1.8);
        }
        if (t == 3 + 24) {
            strike(p, 3.2, 60, 7);
            level.sendParticles(com.soulless.registry.ModParticles.RED_SPARK.get(), getX(), getY() + 0.2, getZ(), 16, 0.6, 0.1, 0.6, 0.2);
        }
        if (t >= 3 + 34) endAttack();
    }

    // 7. DROP: over the foe in the air, blade high, then down on the marked spot
    private void drop(ServerLevel level, Player p) {
        if (t == 0) {
            hide(true);
            mark = p.position();
            blink(level, mark.add(0, 5.5, 0));
            setDeltaMovement(Vec3.ZERO);
        }
        if (t == 2) {
            hide(false);
            face(p);
            play(A_DROP);
        }
        if (t < 20) {
            setDeltaMovement(Vec3.ZERO);
            if (t % 2 == 0) ring(level, mark, 2.2, com.soulless.registry.ModParticles.BLOOD_EMBER.get(), 20);
        }
        if (t == 20) setDeltaMovement(0, -1.6, 0);
        if (t == 24) {
            moveTo(mark.x, mark.y, mark.z, getYRot(), getXRot());
            strikeAt(p, mark, 2.3, 8);
            ring(level, mark, 2.3, com.soulless.registry.ModParticles.BLOOD_EMBER.get(), 40);
            level.sendParticles(com.soulless.registry.ModParticles.CRIMSON_BURST.get(), mark.x, mark.y + 0.4, mark.z, 1, 0, 0, 0, 0);
            level.sendParticles(com.soulless.registry.ModParticles.RED_SPARK.get(), mark.x, mark.y + 0.2, mark.z, 20, 0.8, 0.1, 0.8, 0.3);
            level.playSound(null, mark.x, mark.y, mark.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.6F, 0.7F);
        }
        if (t >= 38) endAttack();
    }

    private void slashArc(ServerLevel level, double r) {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        for (int i = -6; i <= 6; i++) {
            double a = yaw + i * 0.15;
            level.sendParticles(com.soulless.registry.ModParticles.BLOOD_EMBER.get(), getX() - Math.sin(a) * r, getY() + 1.1 + i * 0.03, getZ() + Math.cos(a) * r, 1, 0, 0, 0, 0);
        }
    }

    private static void ring(ServerLevel level, Vec3 c, double r, net.minecraft.core.particles.ParticleOptions p, int n) {
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            level.sendParticles(p, c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    // ---- taking blows

    /** The floor of the dark astral (RenegadeEncounter): his blinks land on it, and the void sends him back. */
    public static final double FLOOR = 100;

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            // fell off the floor: back onto it, unhurt
            moveTo(0.5, FLOOR, -4.5, getYRot(), getXRot());
            setDeltaMovement(Vec3.ZERO);
            fallDistance = 0;
            return false;
        }
        if (defeated || intro > 0 || hidden()) return false;
        if (source.getEntity() instanceof Player attacker && !hasEffect(ModEffects.STUN.get()) && random.nextFloat() < DODGE
                && level() instanceof ServerLevel level) {
            // slips aside: a blink 2.5 blocks to one side of the blow
            Vec3 look = position().subtract(attacker.position());
            look = new Vec3(look.x, 0, look.z).normalize();
            Vec3 side = new Vec3(-look.z, 0, look.x).scale(random.nextBoolean() ? 2.5 : -2.5);
            blink(level, position().add(side).add(look.scale(0.6)));
            face(attacker);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.HOSTILE, 1, 0.7F);
            return false;
        }
        if (getHealth() - amount <= DEFEAT_AT) {
            setHealth(DEFEAT_AT);
            defeated = true;
            endAttack();
            play(A_DEFEAT);
            playSound(SoundEvents.PLAYER_HURT, 1, 0.6F);
            return true;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (isPreview()) tag.putString("preview", preview);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("preview")) makePreview(tag.getString("preview"));
        else discard();   // a fight is never resumed from a save (RenegadeEncounter starts it afresh)
    }

    @Override
    public boolean causeFallDamage(float distance, float mult, DamageSource source) {
        return false;
    }

    public void talk() {
        play(A_TALK);
    }

    // ---- animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, event -> {
            int a = anim();
            boolean loop = loops(a);
            String name = "animation.renegade." + ANIMS[a];
            if (a == A_DEFEAT) {
                event.setAnimation(RawAnimation.begin().thenPlay(name).thenLoop("animation.renegade.defeat_breath"));
            } else {
                event.setAnimation(loop ? RawAnimation.begin().thenLoop(name) : RawAnimation.begin().thenPlayAndHold(name));
            }
            // steps played as fast as he really moves, so the feet do not slide
            double speed = 1;
            if (a == A_WALK || a == A_WALK_BACK || a == A_STRAFE_L || a == A_STRAFE_R) {
                double v = Math.sqrt((getX() - xo) * (getX() - xo) + (getZ() - zo) * (getZ() - zo));
                double made = a == A_WALK || a == A_WALK_BACK ? STEP_SPEED : STRAFE_SPEED;
                if (v > 0.004) speed = Mth.clamp(v / made, 0.5, 2.2);
            }
            event.getController().setAnimationSpeed(speed);
            if (event.getController().getCurrentAnimation() != null && lastStart != animStart() && !loop) {
                event.getController().forceAnimationReset();
            }
            if (lastStart != animStart() && lastStart != -1) startEffects(a);
            lastStart = animStart();
            return PlayState.CONTINUE;
        }));
    }

    private int lastStart = -1;
    /** Client: where he was last seen (the steps' wind lines run from there). */
    private Vec3 lastSeen = Vec3.ZERO;

    /** Client side of the animation controller: effects that go with an animation's start. */
    private void startEffects(int a) {
        if (a != A_STEP1 && a != A_STEP2 && a != A_STEP3) return;
        int kind = a == A_STEP1 ? 0 : 1;   // kesa down to the left; the others swept back the other way
        com.soulless.client.KatanaTrails.add(this, kind, 1.6F);
        com.soulless.client.KatanaTrails.whoosh(this, kind);
        com.soulless.client.KatanaTrails.gust(this, lastSeen);
    }

    @Override
    public void baseTick() {
        super.baseTick();
        if (level().isClientSide && !hidden()) lastSeen = position();
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
