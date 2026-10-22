package com.soulless.forge;

import com.soulless.registry.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Supplier;

/**
 * What the Soul Anvil forges, in the order its screen lists them. The katana needs only Wraith Iron; everything
 * else needs something taken from a boss - the screen still shows it all, so the player sees what is ahead.
 */
public final class SoulAnvilRecipes {
    public record Cost(Supplier<Item> item, int count) {}

    public record Recipe(Supplier<Item> result, List<Cost> costs) {
        public boolean affordable(Player player) {
            for (Cost c : costs) {
                if (count(player, c.item.get()) < c.count) return false;
            }
            return true;
        }
    }

    public static final List<Recipe> ALL = List.of(
            new Recipe(ModItems.SOUL_KATANA::get, List.of(
                    new Cost(ModItems.WRAITH_IRON_INGOT::get, 6),
                    new Cost(() -> Items.STICK, 2))),
            new Recipe(ModItems.SOUL_FORGED::get, List.of(
                    new Cost(ModItems.REAPER_SOUL::get, 1),
                    new Cost(ModItems.WRAITH_IRON_INGOT::get, 4),
                    new Cost(() -> Items.NETHERITE_INGOT, 1))));

    public static int count(Player player, Item item) {
        int n = 0;
        for (ItemStack s : player.getInventory().items) {
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    /** Takes the costs from the player's inventory and gives the result; false (nothing taken) if they lack any. */
    public static boolean forge(Player player, int index) {
        if (index < 0 || index >= ALL.size()) return false;
        Recipe r = ALL.get(index);
        if (!player.getAbilities().instabuild && !r.affordable(player)) return false;
        if (!player.getAbilities().instabuild) {
            for (Cost c : r.costs) {
                int left = c.count;
                for (ItemStack s : player.getInventory().items) {
                    if (left == 0) break;
                    if (!s.is(c.item.get())) continue;
                    int take = Math.min(left, s.getCount());
                    s.shrink(take);
                    left -= take;
                }
            }
        }
        ItemStack out = new ItemStack(r.result.get());
        if (!player.getInventory().add(out)) player.drop(out, false);
        return true;
    }

    private SoulAnvilRecipes() {}
}
