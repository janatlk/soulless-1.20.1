package com.soulless.upgrade;

import com.soulless.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The artifacts' upgrades, kept on the stack: the options picked so far (one per bought tier, in order) and every
 * soul ever spent on that very item - forging it included - which is what salvaging gives half of back.
 */
public final class Upgrades {
    private static final String TAG = "soulless_upgrades", PICKED = "Picked", INVESTED = "Invested";

    /** Katana option ids (item.SoulKatanaItem, KatanaCombat, KatanaParry, ResonanceRitual). */
    public static final String KATANA_PARRY = "katana.parry", KATANA_LEECH = "katana.parry_leech", KATANA_STUN = "katana.parry_stun",
            KATANA_THRUST = "katana.thrust", KATANA_RESONANCE = "katana.resonance", KATANA_EDGE = "katana.edge", KATANA_FLOW = "katana.flow";

    /** Soul Forged option ids (ability.SlamManager, ability.RendManager, ability.SoulSlashEntity, item.SoulForgedItem). */
    public static final String FORGED_SLAM = "forged.slam", FORGED_QUAKE = "forged.quake", FORGED_HASTE = "forged.haste",
            FORGED_REND = "forged.rend", FORGED_HUNGER = "forged.hunger", FORGED_SPLIT = "forged.split",
            FORGED_CURSE = "forged.curse", FORGED_WEIGHT = "forged.weight";

    private static final Map<Supplier<Item>, UpgradeTree> TREES = Map.of(
            ModItems.SOUL_KATANA::get, new UpgradeTree(List.of(
                    UpgradeTree.Tier.of(50, KATANA_PARRY),
                    UpgradeTree.Tier.of(150, KATANA_LEECH, KATANA_STUN),
                    UpgradeTree.Tier.of(300, KATANA_THRUST),
                    UpgradeTree.Tier.of(500, KATANA_RESONANCE),
                    UpgradeTree.Tier.of(800, KATANA_EDGE, KATANA_FLOW))),
            ModItems.SOUL_FORGED::get, new UpgradeTree(List.of(
                    UpgradeTree.Tier.of(200, FORGED_SLAM),
                    UpgradeTree.Tier.of(350, FORGED_QUAKE, FORGED_HASTE),
                    UpgradeTree.Tier.of(500, FORGED_REND),
                    UpgradeTree.Tier.of(700, FORGED_HUNGER, FORGED_SPLIT),
                    UpgradeTree.Tier.of(1000, FORGED_CURSE, FORGED_WEIGHT))));

    public static UpgradeTree tree(ItemStack stack) {
        for (var e : TREES.entrySet()) {
            if (stack.is(e.getKey().get())) return e.getValue();
        }
        return null;
    }

    private static CompoundTag data(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t == null ? new CompoundTag() : t.getCompound(TAG);
    }

    public static List<String> picked(ItemStack stack) {
        List<String> out = new ArrayList<>();
        ListTag l = data(stack).getList(PICKED, 8);
        for (int i = 0; i < l.size(); i++) out.add(l.getString(i));
        return out;
    }

    /** Tiers bought so far. */
    public static int tier(ItemStack stack) {
        return picked(stack).size();
    }

    public static boolean has(ItemStack stack, String option) {
        return picked(stack).contains(option);
    }

    /** The item in this player's main hand has the option. */
    public static boolean has(Player player, String option) {
        return has(player.getMainHandItem(), option);
    }

    public static int invested(ItemStack stack) {
        return data(stack).getInt(INVESTED);
    }

    public static void addInvested(ItemStack stack, int souls) {
        CompoundTag d = stack.getOrCreateTag().getCompound(TAG);
        d.putInt(INVESTED, d.getInt(INVESTED) + souls);
        stack.getOrCreateTag().put(TAG, d);
    }

    private static void setPicked(ItemStack stack, List<String> picked) {
        CompoundTag d = stack.getOrCreateTag().getCompound(TAG);
        ListTag l = new ListTag();
        for (String s : picked) l.add(StringTag.valueOf(s));
        d.put(PICKED, l);
        stack.getOrCreateTag().put(TAG, d);
    }

    /** Why a tier option cannot be bought right now (null: it can). */
    public static String refusal(Player player, ItemStack stack, int option) {
        UpgradeTree tree = tree(stack);
        int tier = tier(stack);
        if (tree == null || tier >= tree.size()) return "maxed";
        UpgradeTree.Tier t = tree.tiers().get(tier);
        if (option < 0 || option >= t.options().size()) return "maxed";
        if (!player.getAbilities().instabuild && SoulVessels.total(player) < t.souls()) return "souls";
        return null;
    }

    /** Buys the next tier with the given option, paying from the player's vessels. */
    public static boolean buy(ServerPlayer player, ItemStack stack, int option) {
        if (refusal(player, stack, option) != null) return false;
        UpgradeTree.Tier t = tree(stack).tiers().get(tier(stack));
        if (!player.getAbilities().instabuild) SoulVessels.take(player, t.souls());
        addInvested(stack, t.souls());
        List<String> picked = picked(stack);
        picked.add(t.options().get(option));
        setPicked(stack, picked);
        return true;
    }

    /** Takes every upgrade off the item and gives back half the souls they cost (what does not fit is lost). */
    public static int reset(ServerPlayer player, ItemStack stack) {
        UpgradeTree tree = tree(stack);
        if (tree == null) return 0;
        int spent = 0, n = tier(stack);
        for (int i = 0; i < n; i++) spent += tree.tiers().get(i).souls();
        setPicked(stack, List.of());
        CompoundTag d = stack.getOrCreateTag().getCompound(TAG);
        d.putInt(INVESTED, Math.max(0, d.getInt(INVESTED) - spent));
        stack.getOrCreateTag().put(TAG, d);
        int back = spent / 2;
        SoulVessels.give(player, back);
        return back;
    }

    private Upgrades() {}
}
