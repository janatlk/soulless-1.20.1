package com.soulless.world;

import com.soulless.Soulless;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The astral dream is gone, but old worlds still list its dimension (kept registered, empty, so they open). Anyone
 * who logs in there is sent home to the world spawn.
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class LegacyAstral {
    private static final ResourceKey<Level> ASTRAL = ResourceKey.create(Registries.DIMENSION, Soulless.id("astral"));

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.level().dimension() != ASTRAL) return;
        var home = p.server.overworld();
        var spawn = home.getSharedSpawnPos();
        p.teleportTo(home, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, p.getYRot(), 0);
    }

    private LegacyAstral() {}
}
