package com.soulless.item;

import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;

public enum SoulForgedTier implements Tier {
    INSTANCE;

    @Override public int getUses() { return 3200; }
    @Override public float getSpeed() { return 9.0F; }
    @Override public float getAttackDamageBonus() { return 5.0F; }
    @Override public int getLevel() { return 4; }
    @Override public int getEnchantmentValue() { return 18; }
    @Override public Ingredient getRepairIngredient() { return Ingredient.of(net.minecraft.world.item.Items.NETHERITE_INGOT); }
}
