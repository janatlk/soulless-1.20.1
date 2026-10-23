package com.soulless.client;

import net.minecraft.client.Minecraft;

/** The Leviathan armour's two models (helmet, chest and boots; leggings), baked once from their layers. */
public final class LeviathanArmorModels {
    private static LeviathanArmorModel outer, inner;

    public static LeviathanArmorModel get(boolean legs) {
        if (outer == null) {
            var models = Minecraft.getInstance().getEntityModels();
            outer = new LeviathanArmorModel(models.bakeLayer(LeviathanArmorModel.OUTER));
            inner = new LeviathanArmorModel(models.bakeLayer(LeviathanArmorModel.INNER));
        }
        return legs ? inner : outer;
    }

    /** Resource reload: bake them again. */
    public static void reset() {
        outer = inner = null;
    }

    private LeviathanArmorModels() {}
}
