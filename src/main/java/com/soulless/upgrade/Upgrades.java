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

    /** The Leviathan armour's upgrades: not a path but a set, any of them bought once on each piece (in any order);
     * Breath of the Deep counts them over the four pieces worn (ability.DeepDash). */
    public static final String DASH_WATER = "dash.water", DASH_LAND = "dash.land", DASH_COOLDOWN = "dash.cooldown", DASH_DAMAGE = "dash.damage";
    public record Free(String id, int souls) {}
    private static final List<Free> ARMOR_FREE = List.of(new Free(DASH_WATER, 60), new Free(DASH_LAND, 160),
            new Free(DASH_COOLDOWN, 120), new Free(DASH_DAMAGE, 70));

    /** The free upgrades this item can take (null: none - it has a tree, or nothing). */
    public static List<Free> free(ItemStack stack) {
        return stack.getItem() instanceof com.soulless.item.LeviathanArmorItem ? ARMOR_FREE : null;
    }

    /** How many of the pieces this player wears have the option. */
    public static int worn(Player player, String option) {
        int n = 0;
        for (var s : new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD,
                net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET})
            if (has(player.getItemBySlot(s), option)) n++;
        return n;
    }

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

    /** The tooltip's account of the anvil's work on it: each upgrade bought, marked, with what it gives. */
    public static void describePicked(ItemStack stack, List<net.minecraft.network.chat.Component> tooltip) {
        for (String opt : picked(stack)) {
            tooltip.add(net.minecraft.network.chat.Component.literal(" \u2714 ").append(net.minecraft.network.chat.Component.translatable("upgrade.soulless." + opt))
                    .withStyle(net.minecraft.ChatFormatting.AQUA));
            tooltip.add(net.minecraft.network.chat.Component.literal("    ").append(net.minecraft.network.chat.Component.translatable("upgrade.soulless." + opt + ".desc"))
                    .withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
        }
        int souls = invested(stack);
        if (souls > 0) tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.soulless.upgrade.invested", souls)
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
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
        List<Free> free = free(stack);
        if (free != null) {
            if (option < 0 || option >= free.size() || has(stack, free.get(option).id())) return "maxed";
            if (!player.getAbilities().instabuild && SoulVessels.total(player) < free.get(option).souls()) return "souls";
            return null;
        }
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
        List<Free> free = free(stack);
        if (free != null) {
            Free f = free.get(option);
            if (!player.getAbilities().instabuild) SoulVessels.take(player, f.souls());
            addInvested(stack, f.souls());
            List<String> picked = picked(stack);
            picked.add(f.id());
            setPicked(stack, picked);
            return true;
        }
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
        List<Free> free = free(stack);
        if (tree == null && free == null) return 0;
        int spent = 0, n = tier(stack);
        if (free != null) {
            for (Free f : free) if (has(stack, f.id())) spent += f.souls();
        } else {
            for (int i = 0; i < n; i++) spent += tree.tiers().get(i).souls();
        }
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
