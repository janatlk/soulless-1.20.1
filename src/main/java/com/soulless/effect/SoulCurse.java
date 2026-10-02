package com.soulless.effect;

import com.soulless.Soulless;
import com.soulless.network.ModNetwork;
import com.soulless.network.SoulCurseSyncPacket;
import com.soulless.registry.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/** Soul Curse rules and the server -> client sync that lets clients draw the purple flame on any entity. */
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

    @SubscribeEvent
    public static void onAdded(MobEffectEvent.Added event) {
        if (event.getEffectInstance().getEffect() == ModEffects.SOUL_CURSE.get() && !event.getEntity().level().isClientSide) {
            ModNetwork.sendTrackingAndSelf(event.getEntity(),
                    new SoulCurseSyncPacket(event.getEntity().getId(), event.getEffectInstance().getDuration()));
        }
    }

    @SubscribeEvent
    public static void onRemoved(MobEffectEvent.Remove event) {
        if (event.getEffect() == ModEffects.SOUL_CURSE.get() && !event.getEntity().level().isClientSide) {
            ModNetwork.sendTrackingAndSelf(event.getEntity(), new SoulCurseSyncPacket(event.getEntity().getId(), 0));
        }
    }

    @SubscribeEvent
    public static void onExpired(MobEffectEvent.Expired event) {
        if (event.getEffectInstance() != null && event.getEffectInstance().getEffect() == ModEffects.SOUL_CURSE.get()
                && !event.getEntity().level().isClientSide) {
            ModNetwork.sendTrackingAndSelf(event.getEntity(), new SoulCurseSyncPacket(event.getEntity().getId(), 0));
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity living && event.getEntity() instanceof ServerPlayer player) {
            MobEffectInstance inst = living.getEffect(ModEffects.SOUL_CURSE.get());
            if (inst != null) {
                ModNetwork.sendToPlayer(player, new SoulCurseSyncPacket(living.getId(), inst.getDuration()));
            }
        }
    }

    private SoulCurse() {}
}
