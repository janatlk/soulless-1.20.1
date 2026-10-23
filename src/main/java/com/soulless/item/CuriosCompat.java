package com.soulless.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import top.theillusivec4.curios.api.CuriosApi;

/** Curios calls, kept in their own class so nothing loads Curios classes when the mod is absent. */
public final class CuriosCompat {
    static boolean wearing(LivingEntity entity, Item item) {
        return CuriosApi.getCuriosInventory(entity).map(inv -> inv.findFirstCurio(item).isPresent()).orElse(false);
    }

    public static net.minecraft.world.item.ItemStack find(LivingEntity entity, Item item) {
        return CuriosApi.getCuriosInventory(entity).resolve().flatMap(inv -> inv.findFirstCurio(item))
                .map(r -> r.stack()).orElse(net.minecraft.world.item.ItemStack.EMPTY);
    }

    private CuriosCompat() {}
}
