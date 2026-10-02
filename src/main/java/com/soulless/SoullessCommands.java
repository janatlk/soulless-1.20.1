package com.soulless;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.soulless.ability.RendManager;
import com.soulless.network.ClientControlPacket;
import com.soulless.network.ModNetwork;
import com.soulless.registry.ModItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.List;

/**
 * {@code /soulless zones [on|off]} - show where Soul Crush and Soul Rend would hit (anyone, for themselves).
 * {@code /soulless resetcd [targets]} - clear both ability cooldowns (operators / cheats).
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class SoullessCommands {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("soulless")
                .then(Commands.literal("zones")
                        .executes(ctx -> zones(ctx.getSource(), null))
                        .then(Commands.argument("show", BoolArgumentType.bool())
                                .executes(ctx -> zones(ctx.getSource(), BoolArgumentType.getBool(ctx, "show")))))
                .then(Commands.literal("resetcd")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> resetCooldowns(ctx.getSource(), List.of(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> resetCooldowns(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"))))));
    }

    /** Re-send the zone preview setting on login (the client forgets it). */
    @SubscribeEvent
    public static void onLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && p.getPersistentData().getBoolean("soulless_zones")) {
            ModNetwork.sendToPlayer(p, new ClientControlPacket(ClientControlPacket.SHOW_ZONES, true));
        }
    }

    /** {@code show == null} toggles. */
    private static int zones(CommandSourceStack src, Boolean show) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        boolean on = show != null ? show : !player.getPersistentData().getBoolean("soulless_zones");
        player.getPersistentData().putBoolean("soulless_zones", on);
        ModNetwork.sendToPlayer(player, new ClientControlPacket(ClientControlPacket.SHOW_ZONES, on));
        src.sendSuccess(() -> Component.translatable(on ? "command.soulless.zones.on" : "command.soulless.zones.off"), false);
        return 1;
    }

    private static int resetCooldowns(CommandSourceStack src, Collection<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            p.getCooldowns().removeCooldown(ModItems.SOUL_FORGED.get());
            RendManager.resetCooldown(p);
            ModNetwork.sendToPlayer(p, new ClientControlPacket(ClientControlPacket.RESET_COOLDOWNS, true));
        }
        src.sendSuccess(() -> Component.translatable("command.soulless.resetcd", players.size()), true);
        return players.size();
    }

    private SoullessCommands() {}
}
