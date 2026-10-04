package com.soulless.effect;

import com.soulless.Soulless;
import com.soulless.registry.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/** Stun rules: frozen in place, no jumping, no AI, no attacks of any kind. */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class Stun {
    public static void apply(LivingEntity target, int ticks, @Nullable Entity source) {
        target.addEffect(new MobEffectInstance(ModEffects.STUN.get(), ticks, 0, false, false, true), source);
    }

    /** Like {@link #apply} but ignores immunities (bosses such as the Wither refuse ordinary effects). */
    public static void force(LivingEntity target, int ticks, @Nullable Entity source) {
        target.forceAddEffect(new MobEffectInstance(ModEffects.STUN.get(), ticks, 0, false, false, true), source);
    }

    public static boolean isStunned(@Nullable Entity entity) {
        return entity instanceof LivingEntity l && l.hasEffect(ModEffects.STUN.get());
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (isStunned(event.getEntity())) {
            Vec3 v = event.getEntity().getDeltaMovement();
            event.getEntity().setDeltaMovement(v.x, Math.min(v.y, 0.0), v.z);
        }
    }

    private static final String HELD = "soulless_stun_held";

    /**
     * Stunned mobs: their goals can't move, look, jump or pick targets, and brain-driven mobs (piglins, wardens,
     * hoglins...) are kept "cooling down" - so nothing even starts an attack (a golem used to swing its arms, the hit
     * then cancelled). Everything is given back the tick the stun ends.
     */
    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide) return;
        boolean held = mob.getPersistentData().getBoolean(HELD);
        if (isStunned(mob)) {
            if (!held) {
                for (var flag : net.minecraft.world.entity.ai.goal.Goal.Flag.values()) {
                    mob.goalSelector.disableControlFlag(flag);
                    mob.targetSelector.disableControlFlag(flag);
                }
                mob.getPersistentData().putBoolean(HELD, true);
            }
            mob.getNavigation().stop();
            mob.setTarget(null);
            mob.setAggressive(false);
            var brain = mob.getBrain();
            brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
            brain.setMemoryWithExpiry(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_COOLING_DOWN, true, 5);
        } else if (held) {
            for (var flag : net.minecraft.world.entity.ai.goal.Goal.Flag.values()) {
                mob.goalSelector.enableControlFlag(flag);
                mob.targetSelector.enableControlFlag(flag);
            }
            mob.getPersistentData().remove(HELD);
        }
    }

    /**
     * Stunned attackers cannot strike directly. Projectiles and spells already released before the stun
     * still land (the source entity is the attacker, but the direct entity is the projectile).
     */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (isStunned(attacker) && event.getSource().getDirectEntity() == attacker) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (isStunned(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private Stun() {}
}
