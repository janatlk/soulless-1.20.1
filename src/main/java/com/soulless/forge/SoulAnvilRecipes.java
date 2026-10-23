package com.soulless.forge;

import com.soulless.registry.ModItems;
import com.soulless.upgrade.SoulVessels;
import com.soulless.upgrade.Upgrades;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Everything the Soul Anvil forges, by category, in the order its screens list them. A recipe takes items (laid on
 * the anvil one by one) and souls (from the player's vessels); the souls spent are written on the result, so
 * salvaging it later gives half back. The katana needs nothing from a boss; the rest do - but all are always shown,
 * so the player sees what lies ahead.
 */
public final class SoulAnvilRecipes {
    /** The anvil's categories; SOULS is not forging but salvaging (artifact -> souls). */
    public enum Category { MELEE, RANGED, ARMOR, ACCESSORY, SUMMON, SOULS }

    public record Cost(Supplier<Item> item, int count) {}

    public record Recipe(String id, Category category, Supplier<Item> result, int souls, List<Cost> costs) {
        public Item item() {
            return result.get();
        }

        public boolean affordable(Player player) {
            if (player.getAbilities().instabuild) return true;
            if (SoulVessels.total(player) < souls) return false;
            for (Cost c : costs) {
                if (count(player, c.item.get()) < c.count) return false;
            }
            return true;
        }
    }

    public static final List<Recipe> ALL = List.of(
            new Recipe("soul_katana", Category.MELEE, ModItems.SOUL_KATANA::get, 20, List.of(
                    new Cost(ModItems.WRAITH_IRON_INGOT::get, 6),
                    new Cost(() -> Items.STICK, 2))),
            new Recipe("soul_forged", Category.MELEE, ModItems.SOUL_FORGED::get, 200, List.of(
                    new Cost(ModItems.REAPER_SOUL::get, 1),
                    new Cost(ModItems.WRAITH_IRON_INGOT::get, 4),
                    new Cost(() -> Items.NETHERITE_INGOT, 1))),
            // the Leviathan's armour: its scales over netherite (a fight gives 2-4; the set takes 8)
            new Recipe("leviathan_helmet", Category.ARMOR, ModItems.LEVIATHAN_HELMET::get, 60, List.of(
                    new Cost(ModItems.LEVIATHAN_SCALE::get, 2),
                    new Cost(() -> Items.NETHERITE_HELMET, 1),
                    new Cost(() -> Items.PRISMARINE_CRYSTALS, 4))),
            new Recipe("leviathan_chestplate", Category.ARMOR, ModItems.LEVIATHAN_CHESTPLATE::get, 100, List.of(
                    new Cost(ModItems.LEVIATHAN_SCALE::get, 3),
                    new Cost(() -> Items.NETHERITE_CHESTPLATE, 1),
                    new Cost(ModItems.WRAITH_IRON_INGOT::get, 4))),
            new Recipe("leviathan_leggings", Category.ARMOR, ModItems.LEVIATHAN_LEGGINGS::get, 80, List.of(
                    new Cost(ModItems.LEVIATHAN_SCALE::get, 2),
                    new Cost(() -> Items.NETHERITE_LEGGINGS, 1),
                    new Cost(ModItems.WRAITH_IRON_INGOT::get, 3))),
            new Recipe("leviathan_boots", Category.ARMOR, ModItems.LEVIATHAN_BOOTS::get, 50, List.of(
                    new Cost(ModItems.LEVIATHAN_SCALE::get, 1),
                    new Cost(() -> Items.NETHERITE_BOOTS, 1),
                    new Cost(() -> Items.NAUTILUS_SHELL, 1))),
            new Recipe("soul_vessel", Category.ACCESSORY, ModItems.SOUL_VESSEL::get, 0, List.of(
                    new Cost(ModItems.WRAITH_IRON_INGOT::get, 3),
                    new Cost(() -> Items.GLASS_BOTTLE, 1))),
            // the Soul Ring: a vessel's making and three diamonds; it holds 1000
            new Recipe("soul_ring", Category.ACCESSORY, ModItems.SOUL_RING::get, 0, List.of(
                    new Cost(ModItems.WRAITH_IRON_INGOT::get, 3),
                    new Cost(() -> Items.GLASS_BOTTLE, 1),
                    new Cost(() -> Items.DIAMOND, 3))),
            new Recipe("eye_of_lament", Category.SUMMON, ModItems.EYE_OF_LAMENT::get, 30, List.of(
                    new Cost(() -> Items.NETHERITE_SCRAP, 2),
                    new Cost(() -> Items.ENDER_EYE, 1),
                    new Cost(() -> Items.SOUL_SOIL, 4),
                    new Cost(ModItems.WRAITH_IRON_INGOT::get, 2))),
            new Recipe("eye_of_drowned", Category.SUMMON, ModItems.EYE_OF_DROWNED::get, 30, List.of(
                    new Cost(() -> Items.NETHERITE_SCRAP, 2),
                    new Cost(() -> Items.ENDER_EYE, 1),
                    new Cost(() -> Items.NAUTILUS_SHELL, 1),
                    new Cost(() -> Items.PRISMARINE_SHARD, 4))),
            new Recipe("drowned_gills", Category.ACCESSORY, ModItems.DROWNED_GILLS::get, 60, List.of(
                    new Cost(() -> Items.HEART_OF_THE_SEA, 1),
                    new Cost(() -> Items.NETHERITE_INGOT, 1),
                    new Cost(() -> Items.NAUTILUS_SHELL, 2),
                    new Cost(() -> Items.PRISMARINE_CRYSTALS, 4),
                    new Cost(ModItems.WRAITH_IRON_INGOT::get, 3))),
            new Recipe("renegade_sigil", Category.SUMMON, ModItems.RENEGADE_SIGIL::get, 50, List.of(
                    new Cost(ModItems.WRAITH_IRON_INGOT::get, 3),
                    new Cost(() -> Items.ECHO_SHARD, 1),
                    new Cost(() -> Items.REDSTONE, 4))));

    public static List<Recipe> in(Category c) {
        List<Recipe> out = new ArrayList<>();
        for (Recipe r : ALL) if (r.category == c) out.add(r);
        return out;
    }

    public static Recipe byId(String id) {
        for (Recipe r : ALL) if (r.id.equals(id)) return r;
        return null;
    }

    /** The recipe that makes this item (null: the anvil does not make it). */
    public static Recipe of(ItemStack stack) {
        for (Recipe r : ALL) if (stack.is(r.item())) return r;
        return null;
    }

    /** Can be melted down into souls at the anvil: anything it forges that holds souls (not summons or the vessel). */
    public static boolean salvageable(ItemStack stack) {
        Recipe r = of(stack);
        return r != null && r.category != Category.SUMMON && !(stack.getItem() instanceof com.soulless.item.SoulVesselItem);
    }

    /** What salvaging gives: half of every soul spent on it, forging and upgrades. */
    public static int salvageValue(ItemStack stack) {
        return Upgrades.invested(stack) / 2;
    }

    public static int count(Player player, Item item) {
        int n = 0;
        for (ItemStack s : player.getInventory().items) {
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    /** The finished item: the souls it cost are written on it. */
    public static ItemStack make(Recipe r) {
        ItemStack out = new ItemStack(r.item());
        if (r.souls > 0) Upgrades.addInvested(out, r.souls);
        return out;
    }

    private SoulAnvilRecipes() {}
}
