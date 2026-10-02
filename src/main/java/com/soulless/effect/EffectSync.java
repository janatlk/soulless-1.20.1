package com.soulless.effect;

import com.soulless.Soulless;
import com.soulless.network.EffectSyncPacket;
import com.soulless.network.ModNetwork;
import com.soulless.registry.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Tells clients when Soul Curse / Stun start or end on any entity, so they can draw flames and stun stars. */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class EffectSync {
    private static int kind(MobEffect effect) {
        if (effect == ModEffects.SOUL_CURSE.get()) return EffectSyncPacket.SOUL_CURSE;
        if (effect == ModEffects.STUN.get()) return EffectSyncPacket.STUN;
        return -1;
    }

    private static void send(LivingEntity entity, MobEffect effect, int duration) {
        int kind = kind(effect);
        if (kind >= 0 && !entity.level().isClientSide) {
            ModNetwork.sendTrackingAndSelf(entity, new EffectSyncPacket(entity.getId(), kind, duration));
        }
    }

    @SubscribeEvent
    public static void onAdded(MobEffectEvent.Added event) {
        send(event.getEntity(), event.getEffectInstance().getEffect(), event.getEffectInstance().getDuration());
    }

    @SubscribeEvent
    public static void onRemoved(MobEffectEvent.Remove event) {
        if (event.getEffect() != null) send(event.getEntity(), event.getEffect(), 0);
    }

    @SubscribeEvent
    public static void onExpired(MobEffectEvent.Expired event) {
        if (event.getEffectInstance() != null) send(event.getEntity(), event.getEffectInstance().getEffect(), 0);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity living && event.getEntity() instanceof ServerPlayer player) {
            for (MobEffect effect : new MobEffect[]{ModEffects.SOUL_CURSE.get(), ModEffects.STUN.get()}) {
                MobEffectInstance inst = living.getEffect(effect);
                if (inst != null) {
                    ModNetwork.sendToPlayer(player, new EffectSyncPacket(living.getId(), kind(effect), inst.getDuration()));
                }
            }
        }
    }

    private EffectSync() {}
}
