package com.soulless.upgrade;

import com.soulless.Soulless;
import com.soulless.item.SoulBonus;
import com.soulless.item.SoulVesselItem;
import com.soulless.network.ModNetwork;
import com.soulless.network.SoulWispPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Souls are the mod's currency, and only a Soul Vessel holds them. Every creature with a soul (SoulBonus.hasSoul,
 * not spawner mobs) a player kills - with any weapon - lets one go: with a vessel on the killer it flies into it,
 * without one it drifts up and away (SoulWispPacket draws both). Souls go into the fullest vessel that still has
 * room (between equal ones, a random one); spending takes from the fullest first.
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class SoulVessels {
    public static List<ItemStack> vessels(Player player) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : player.getInventory().items) if (s.getItem() instanceof SoulVesselItem) out.add(s);
        for (ItemStack s : player.getInventory().offhand) if (s.getItem() instanceof SoulVesselItem) out.add(s);
        return out;
    }

    public static int total(Player player) {
        int n = 0;
        for (ItemStack s : vessels(player)) n += SoulVesselItem.souls(s);
        return n;
    }

    /** Takes up to {@code n} souls, the fullest vessels first; returns how many were taken. */
    public static int take(Player player, int n) {
        int left = n;
        List<ItemStack> vs = vessels(player);
        vs.sort(Comparator.comparingInt(SoulVesselItem::souls).reversed());
        for (ItemStack s : vs) {
            if (left == 0) break;
            int t = Math.min(left, SoulVesselItem.souls(s));
            SoulVesselItem.set(s, SoulVesselItem.souls(s) - t);
            left -= t;
        }
        return n - left;
    }

    /** Puts up to {@code n} souls into the vessels; returns how many found room. */
    public static int give(Player player, int n) {
        int given = 0;
        while (given < n) {
            ItemStack into = target(player);
            if (into == null) break;
            int room = Math.min(n - given, SoulVesselItem.MAX - SoulVesselItem.souls(into));
            SoulVesselItem.set(into, SoulVesselItem.souls(into) + room);
            given += room;
        }
        return given;
    }

    /** The vessel the next soul goes into: the fullest not yet full, a random one between equals. */
    private static ItemStack target(Player player) {
        ItemStack best = null;
        int bestN = -1, ties = 0;
        for (ItemStack s : vessels(player)) {
            int n = SoulVesselItem.souls(s);
            if (n >= SoulVesselItem.MAX) continue;
            if (n > bestN) {
                best = s;
                bestN = n;
                ties = 1;
            } else if (n == bestN && player.getRandom().nextInt(++ties) == 0) {
                best = s;
            }
        }
        return best;
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide || !(event.getSource().getEntity() instanceof ServerPlayer p)) return;
        if (!SoulBonus.hasSoul(dead) || SoulBonus.fromSpawner(dead)) return;
        boolean caught = give(p, 1) == 1;
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new SoulWispPacket(dead.getX(), dead.getY(0.6), dead.getZ(), caught ? p.getId() : -1));
    }

    private SoulVessels() {}
}
