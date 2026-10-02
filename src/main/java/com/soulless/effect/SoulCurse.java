package com.soulless.effect;

import com.soulless.Soulless;
import com.soulless.registry.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/** Soul Curse rules. Client sync of the purple flame lives in {@link EffectSync}. */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class SoulCurse {
    public static final float DAMAGE_PER_SECOND = 2.0F;
    public static final double SLOWNESS = 0.35;

    public static void apply(LivingEntity target, int ticks, int amplifier, @Nullable Entity source) {
        target.clearFire();
        // Re-applying refreshes duration/strength; it never stacks a second instance.
        target.addEffect(new MobEffectInstance(ModEffects.SOUL_CURSE.get(), ticks, amplifier, false, false, true), source);
    }

    public static boolean isCursed(LivingEntity entity) {
        return entity.hasEffect(ModEffects.SOUL_CURSE.get());
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity e = event.getEntity();
        if (isCursed(e)) {
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(v.x, Math.min(v.y, 0.0), v.z);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        // Soul fire smothers ordinary fire: the two never burn at once.
        if (e.getRemainingFireTicks() > 0 && isCursed(e)) {
            e.clearFire();
        }
    }

    private SoulCurse() {}
}
