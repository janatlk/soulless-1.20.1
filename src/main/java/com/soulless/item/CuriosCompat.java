package com.soulless.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import top.theillusivec4.curios.api.CuriosApi;

/** Curios calls, kept in their own class so nothing loads Curios classes when the mod is absent. */
final class CuriosCompat {
    static boolean wearing(LivingEntity entity, Item item) {
        return CuriosApi.getCuriosInventory(entity).map(inv -> inv.findFirstCurio(item).isPresent()).orElse(false);
    }

    private CuriosCompat() {}
}
