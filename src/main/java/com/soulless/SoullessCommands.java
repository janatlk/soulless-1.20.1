package com.soulless;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
 * {@code /soulless zones [targets] [on|off]} - show where Soul Crush and Soul Rend would hit (no argument
 * toggles; anyone for themselves, operators for other players).
 * {@code /soulless resetcd [targets]} - clear both ability cooldowns (operators / cheats).
 * {@code /soulless arena [size|stop]} - a wave of mixed mobs around you, then a combat report (balance check).
 * {@code /soulless ult [radius]} - the Wailing Reaper's ice rain around you (no Reaper), to practise dodging it.
 * {@code /soulless dummy [clear]} - place / remove immortal training dummies that report damage in chat.
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class SoullessCommands {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("soulless")
                .then(Commands.literal("zones")
                        .executes(ctx -> zones(ctx.getSource(), self(ctx.getSource()), null))
                        .then(Commands.literal("on").executes(ctx -> zones(ctx.getSource(), self(ctx.getSource()), true)))
                        .then(Commands.literal("off").executes(ctx -> zones(ctx.getSource(), self(ctx.getSource()), false)))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> zones(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), null))
                                .then(Commands.literal("on").executes(ctx -> zones(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), true)))
                                .then(Commands.literal("off").executes(ctx -> zones(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), false)))))
                .then(Commands.literal("dojo")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            var p = ctx.getSource().getPlayerOrException();
                            net.minecraft.core.BlockPos o = p.blockPosition().offset(-6, 0, 4);
                            com.soulless.world.SenseiDojoPiece.buildAt(p.serverLevel(), o);
                            ctx.getSource().sendSuccess(() -> Component.literal("Dojo built at " + o.toShortString()), true);
                            return 1;
                        }))
                .then(Commands.literal("arena")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> CombatTrial.start(ctx.getSource().getPlayerOrException(), 1))
                        .then(Commands.literal("stop").executes(ctx -> {
                            CombatTrial.stop(ctx.getSource().getPlayerOrException(), true);
                            return 1;
                        }))
                        .then(Commands.argument("size", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 5))
                                .executes(ctx -> CombatTrial.start(ctx.getSource().getPlayerOrException(),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "size")))))
                .then(Commands.literal("ult")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> ult(ctx.getSource(), com.soulless.boss.SoulAltarBlockEntity.ARENA_RADIUS - 0.8))
                        .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(4, 48))
                                .executes(ctx -> ult(ctx.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius")))))
                .then(Commands.literal("dummy")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            TrainingDummy.spawn(ctx.getSource().getPlayerOrException());
                            ctx.getSource().sendSuccess(() -> Component.translatable("command.soulless.dummy.spawned"), true);
                            return 1;
                        })
                        .then(Commands.literal("clear").executes(ctx -> {
                            int n = TrainingDummy.clear(ctx.getSource().getLevel());
                            ctx.getSource().sendSuccess(() -> Component.translatable("command.soulless.dummy.cleared", n), true);
                            return n;
                        })))
                .then(Commands.literal("preview")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("reaper")
                                .executes(ctx -> preview(ctx.getSource(), "idle"))
                                .then(Commands.argument("animation", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                                new String[]{"idle", "fly", "bolt", "erupt", "ult", "stunned", "emerge", "death"}, b))
                                        .executes(ctx -> preview(ctx.getSource(),
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "animation")))))
                        .then(Commands.literal("clear").executes(ctx -> {
                            var level = ctx.getSource().getLevel();
                            int n = 0;
                            for (var e : level.getAllEntities()) {
                                if (e instanceof com.soulless.boss.WailingReaperEntity r && r.isPreview()) {
                                    r.discard();
                                    n++;
                                }
                            }
                            int removed = n;
                            ctx.getSource().sendSuccess(() -> Component.translatable("command.soulless.preview.cleared", removed), true);
                            return n;
                        })))
                .then(Commands.literal("altar")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            var p = ctx.getSource().getPlayerOrException();
                            var look = net.minecraft.world.phys.Vec3.directionFromRotation(0, p.getYRot()).scale(7);
                            com.soulless.boss.SoulAltarFeature.build(p.serverLevel(),
                                    net.minecraft.core.BlockPos.containing(p.getX() + look.x, p.getY(), p.getZ() + look.z));
                            ctx.getSource().sendSuccess(() -> Component.translatable("command.soulless.altar"), true);
                            return 1;
                        }))
                .then(Commands.literal("resetcd")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> resetCooldowns(ctx.getSource(), self(ctx.getSource())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> resetCooldowns(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"))))));
    }

    /** Re-send the zone preview setting on login (the client forgets it). */
    @SubscribeEvent
    public static void onLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && p.getPersistentData().getBoolean("soulless_zones")) {
            ModNetwork.sendToPlayer(p, new ClientControlPacket(ClientControlPacket.SHOW_ZONES, true));
        }
        if (event.getEntity() instanceof ServerPlayer p) {
            ModNetwork.sendToPlayer(p, new ClientControlPacket(ClientControlPacket.PARRY_KNOWN, com.soulless.item.ParryScrollItem.knows(p)));
        }
    }

    /** A motionless, harmless copy of the Reaper 6 blocks ahead, facing you, looping one animation. */
    private static int preview(CommandSourceStack src, String animation) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        var level = p.serverLevel();
        var r = com.soulless.registry.ModEntities.WAILING_REAPER.get().create(level);
        if (r == null) return 0;
        var look = net.minecraft.world.phys.Vec3.directionFromRotation(0, p.getYRot()).scale(6);
        r.moveTo(p.getX() + look.x, p.getY(), p.getZ() + look.z, p.getYRot() + 180, 0);
        r.setYHeadRot(p.getYRot() + 180);
        r.setYBodyRot(p.getYRot() + 180);
        r.makePreview(animation);
        level.addFreshEntity(r);
        src.sendSuccess(() -> Component.translatable("command.soulless.preview", animation), false);
        return 1;
    }

    private static Collection<ServerPlayer> self(CommandSourceStack src) throws CommandSyntaxException {
        return List.of(src.getPlayerOrException());
    }

    /** {@code show == null} toggles each player's own setting. */
    /** {@code /soulless ult [radius]}: the Wailing Reaper's ice rain around the caller, without the Reaper. */
    private static int ult(CommandSourceStack src, double radius) {
        net.minecraft.server.level.ServerLevel level = src.getLevel();
        net.minecraft.world.phys.Vec3 c = src.getPosition();
        com.soulless.boss.WailingReaperEntity.iceRain(level, c, radius, -1, level.getRandom());
        level.playSound(null, c.x, c.y, c.z, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, net.minecraft.sounds.SoundSource.HOSTILE, 3.0F, 0.5F);
        level.playSound(null, c.x, c.y, c.z, net.minecraft.sounds.SoundEvents.PLAYER_HURT_FREEZE, net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 0.6F);
        src.sendSuccess(() -> Component.translatable("command.soulless.ult"), true);
        return 1;
    }

    private static int zones(CommandSourceStack src, Collection<ServerPlayer> players, Boolean show) {
        for (ServerPlayer player : players) {
            boolean on = show != null ? show : !player.getPersistentData().getBoolean("soulless_zones");
            player.getPersistentData().putBoolean("soulless_zones", on);
            ModNetwork.sendToPlayer(player, new ClientControlPacket(ClientControlPacket.SHOW_ZONES, on));
            src.sendSuccess(() -> Component.translatable(on ? "command.soulless.zones.on" : "command.soulless.zones.off",
                    player.getDisplayName()), false);
        }
        return players.size();
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
