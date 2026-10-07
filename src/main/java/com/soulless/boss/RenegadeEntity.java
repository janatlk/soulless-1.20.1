package com.soulless.boss;

import com.soulless.registry.ModEffects;
import com.soulless.registry.ModEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import org.joml.Vector3f;
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
    public static final float MAX_HEALTH = 160;
    /** He kneels, beaten, at this much health; nothing takes him lower. */
    private static final float DEFEAT_AT = 16;

    // animation ids (synced; the renderer picks the animation, a new start tick replays it)
    public static final int A_IDLE = 0, A_WALK = 1, A_TALK = 2, A_APPEAR = 3, A_IAI_READY = 4, A_IAI_STRIKE = 5,
            A_STEP1 = 6, A_STEP2 = 7, A_STEP3 = 8, A_THRUST = 9, A_WHIRL = 10, A_SUMMON = 11, A_COMBO = 12, A_DROP = 13,
            A_STAGGER = 14, A_DEFEAT = 15;
    public static final String[] ANIMS = {"idle", "walk", "talk", "appear", "iai_ready", "iai_strike", "step_cut1", "step_cut2",
            "step_cut3", "thrust", "whirl", "summon", "combo", "drop", "stagger", "defeat"};
    private static final EntityDataAccessor<Integer> ANIM = SynchedEntityData.defineId(RenegadeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ANIM_AT = SynchedEntityData.defineId(RenegadeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> HIDDEN = SynchedEntityData.defineId(RenegadeEntity.class, EntityDataSerializers.BOOLEAN);

    private enum Attack { IAI, STEPS, THRUST, WHIRL, BLADES, COMBO, DROP }

    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.9F, 0.08F, 0.14F), 1.3F);
    private static final DustParticleOptions DARK = new DustParticleOptions(new Vector3f(0.12F, 0.05F, 0.16F), 1.8F);

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

    // ---- the fight

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        setNoGravity(attack == Attack.DROP && t < 20);
        if (level.dimension() == RenegadeEncounter.LEVEL && getY() < FLOOR - 8) {
            moveTo(0.5, FLOOR, -4.5, getYRot(), getXRot());
            setDeltaMovement(Vec3.ZERO);
            fallDistance = 0;
        }
        Player p = foe();
        if (p == null || !p.isAlive() || defeated) {
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
            endAttack();
            hide(false);
            play(A_STAGGER);
            level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 1.4, getZ(), 12, 0.3, 0.3, 0.3, 0.3);
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

    /** Between attacks: circle the foe at a few blocks, guard up. */
    private void stalk(Player p) {
        Vec3 to = p.position().subtract(position());
        double d = to.horizontalDistance();
        Vec3 dir = new Vec3(to.x, 0, to.z).normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(strafeDir);
        Vec3 want = position().add(side.scale(1.2)).add(dir.scale(d > 5.5 ? 1 : d < 3.5 ? -0.8 : 0));
        getMoveControl().setWantedPosition(want.x, want.y, want.z, 0.9);
        if (random.nextInt(80) == 0) strafeDir = -strafeDir;
        if (anim() != A_WALK && anim() != A_STAGGER && anim() != A_APPEAR) play(A_WALK);
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
            do {
                a = all[random.nextInt(all.length)];
            } while (a == lastAttack);
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
        rest = 30 + random.nextInt(25);   // a breath between attacks: time to answer
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
        level.sendParticles(DARK, getX(), getY() + 1, getZ(), 18, 0.3, 0.6, 0.3, 0);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8F, 0.6F);
        moveTo(to.x, to.y, to.z, getYRot(), getXRot());
        level.sendParticles(DARK, to.x, to.y + 1, to.z, 18, 0.3, 0.6, 0.3, 0);
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
            if (t % 3 == 0) level.sendParticles(RED, getX(), getY() + 0.9, getZ(), 2, 0.25, 0.15, 0.25, 0);
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
            strike(p, 3.4, 110, 5);
            slashArc(level, 2.2);
        }
        if (t >= 50) endAttack();
    }

    // 2. STEPS: three blinks toward the foe, zigzagging; a red slash left where each cut was, biting 0.5 s later
    private void steps(ServerLevel level, Player p) {
        int k = t / 10;
        if (t % 10 == 0 && k < 3) {
            hide(true);
            Vec3 to = p.position().subtract(position());
            double d = to.horizontalDistance();
            Vec3 dir = new Vec3(to.x, 0, to.z).normalize();
            Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(k == 1 ? -0.5 : 0.5);
            double hop = k == 2 ? Math.max(0, d - 1.4) : Math.max(0, (d - 1.4) / (3 - k));
            blink(level, position().add(dir.scale(hop)).add(side));
        }
        if (t % 10 == 2 && k < 3) {
            hide(false);
            face(p);
            play(k == 0 ? A_STEP1 : k == 1 ? A_STEP2 : A_STEP3);
            swing(InteractionHand.MAIN_HAND);
            RedSlashEntity slash = ModEntities.RED_SLASH.get().create(level);
            if (slash != null) {
                Vec3 at = position().add(Vec3.directionFromRotation(0, getYRot()).scale(1.2)).add(0, 1.1, 0);
                slash.setup(this, p, at, getYRot(), k * 50 - 50, 10, 3);
                level.addFreshEntity(slash);
            }
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 0.8F, 1.3F);
        }
        if (t >= 46) endAttack();
    }

    // 3. THRUST: drawn back at the hip, then a lunge straight through the foe
    private void thrust(ServerLevel level, Player p) {
        if (t == 0) {
            face(p);
            play(A_THRUST);
            mark = new Vec3(p.getX() - getX(), 0, p.getZ() - getZ()).normalize();
        }
        if (t < 12) {
            face(p);
            mark = new Vec3(p.getX() - getX(), 0, p.getZ() - getZ()).normalize();
            if (t % 2 == 0) level.sendParticles(RED, getX(), getY() + 1, getZ(), 1, 0.2, 0.2, 0.2, 0);
        }
        if (t == 12) level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1, 0.8F);
        if (t >= 12 && t < 20) {
            setDeltaMovement(mark.x * 1.1, getDeltaMovement().y, mark.z * 1.1);
            level.sendParticles(DARK, getX(), getY() + 1, getZ(), 3, 0.2, 0.4, 0.2, 0);
            if (blowsLeft == 0 && t == 12) blowsLeft = 1;
            if (blowsLeft > 0 && distanceTo(p) < 1.8) {
                strike(p, 2.2, 120, 4);
                blowsLeft = 0;
            }
        }
        if (t == 20) setDeltaMovement(Vec3.ZERO);
        if (t >= 32) endAttack();
    }

    private int blowsLeft;

    // 4. WHIRL: a crouch, a leap with two turns onto the marked circle, a shockwave
    private void whirl(ServerLevel level, Player p) {
        if (t == 0) {
            face(p);
            play(A_WHIRL);
            mark = p.position();
        }
        if (t < 23 && t % 2 == 0) ring(level, mark, 2.8, RED, 24);
        if (t == 9) {
            Vec3 to = mark.subtract(position());
            setDeltaMovement(to.x / 13.0, 0.62, to.z / 13.0);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1, 0.7F);
        }
        if (t == 23) {
            strikeAt(p, mark, 2.9, 4);
            ring(level, mark, 2.9, RED, 48);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, mark.x, mark.y + 0.4, mark.z, 6, 1.4, 0.1, 1.4, 0);
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
            if (t == 20 + i * 10) {
                face(p);
                blades.get(i).launch(p);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_THROW, SoundSource.HOSTILE, 1, 1.2F);
            }
        }
        if (t >= 56) {
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
            strike(p, 3.2, 80, 3);
            slashArc(level, 1.8);
        }
        if (t == 3 + 15) {
            strike(p, 3.2, 80, 3);
            slashArc(level, 1.8);
        }
        if (t == 3 + 24) {
            strike(p, 3.2, 60, 4);
            level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 0.2, getZ(), 14, 0.6, 0.1, 0.6, 0.2);
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
            if (t % 2 == 0) ring(level, mark, 2.2, RED, 20);
        }
        if (t == 20) setDeltaMovement(0, -1.6, 0);
        if (t == 24) {
            moveTo(mark.x, mark.y, mark.z, getYRot(), getXRot());
            strikeAt(p, mark, 2.3, 5);
            ring(level, mark, 2.3, RED, 40);
            level.sendParticles(ParticleTypes.EXPLOSION, mark.x, mark.y + 0.3, mark.z, 1, 0, 0, 0, 0);
            level.playSound(null, mark.x, mark.y, mark.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.6F, 0.7F);
        }
        if (t >= 38) endAttack();
    }

    private void slashArc(ServerLevel level, double r) {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        for (int i = -6; i <= 6; i++) {
            double a = yaw + i * 0.15;
            level.sendParticles(RED, getX() - Math.sin(a) * r, getY() + 1.1 + i * 0.03, getZ() + Math.cos(a) * r, 1, 0, 0, 0, 0);
        }
    }

    private static void ring(ServerLevel level, Vec3 c, double r, DustParticleOptions p, int n) {
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
            boolean loop = a == A_IDLE || a == A_WALK || a == A_TALK;
            String name = "animation.renegade." + ANIMS[a];
            event.setAnimation(loop ? RawAnimation.begin().thenLoop(name) : RawAnimation.begin().thenPlayAndHold(name));
            if (event.getController().getCurrentAnimation() != null && lastStart != animStart() && !loop) {
                event.getController().forceAnimationReset();
            }
            lastStart = animStart();
            return PlayState.CONTINUE;
        }));
    }

    private int lastStart = -1;

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
