package com.soulless.network;

import com.soulless.forge.SoulAnvilBlockEntity;
import com.soulless.forge.SoulAnvilRecipes;
import com.soulless.upgrade.SoulVessels;
import com.soulless.upgrade.Upgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: something done on a Soul Anvil screen. {@code arg} is a recipe id, {@code n} an option index or
 * an inventory slot, depending on the action. Everything is checked again here.
 */
public record AnvilActionPacket(BlockPos pos, int action, String arg, int n) {
    public static final int CHOOSE = 0, BUY = 1, RESET = 2, TAKE = 3, SALVAGE_HELD = 4, SALVAGE_SLOT = 5, CANCEL = 6;

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(action);
        buf.writeUtf(arg, 64);
        buf.writeVarInt(n);
    }

    public static AnvilActionPacket decode(FriendlyByteBuf buf) {
        return new AnvilActionPacket(buf.readBlockPos(), buf.readVarInt(), buf.readUtf(64), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null || p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64
                || !(p.level().getBlockEntity(pos) instanceof SoulAnvilBlockEntity be)) return;
        run(p, be, action, arg, n);
    }

    /** Also called directly by the autotest. */
    public static void run(ServerPlayer p, SoulAnvilBlockEntity be, int action, String arg, int n) {
        var level = p.serverLevel();
        BlockPos pos = be.getBlockPos();
        switch (action) {
            case CHOOSE -> {
                var r = SoulAnvilRecipes.byId(arg);
                if (r != null && r.category() != SoulAnvilRecipes.Category.SOULS && be.held().isEmpty()) be.choose(p, r);
            }
            case BUY -> {
                ItemStack held = be.held();
                if (!held.isEmpty() && Upgrades.buy(p, held, n)) {
                    be.heldChanged();
                    level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8F, 1.2F);
                    level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 0.8F);
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 30, 0.3, 0.15, 0.3, 0.05);
                }
            }
            case RESET -> {
                if (!be.held().isEmpty() && Upgrades.tier(be.held()) > 0) {
                    Upgrades.reset(p, be.held());
                    be.heldChanged();
                    level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 1.0F, 0.8F);
                }
            }
            case TAKE -> be.takeBack(p);
            case CANCEL -> be.cancel(p);
            case SALVAGE_HELD -> {
                ItemStack held = be.held();
                if (!SoulAnvilRecipes.salvageable(held)) return;
                SoulVessels.give(p, SoulAnvilRecipes.salvageValue(held));
                be.clearHeld();
                melted(p, pos);
            }
            case SALVAGE_SLOT -> {
                var inv = p.getInventory();
                if (n < 0 || n >= inv.items.size()) return;
                ItemStack s = inv.items.get(n);
                if (!SoulAnvilRecipes.salvageable(s)) return;
                SoulVessels.give(p, SoulAnvilRecipes.salvageValue(s));
                inv.items.set(n, ItemStack.EMPTY);
                melted(p, pos);
            }
            default -> {}
        }
    }

    private static void melted(ServerPlayer p, BlockPos pos) {
        var level = p.serverLevel();
        level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.8F, 0.6F);
        level.playSound(null, pos, SoundEvents.SOUL_ESCAPE, SoundSource.BLOCKS, 1.2F, 0.6F);
        level.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 30, 0.3, 0.3, 0.3, 0.05);
    }
}
