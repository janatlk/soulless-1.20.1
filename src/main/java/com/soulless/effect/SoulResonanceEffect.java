package com.soulless.effect;

import com.soulless.item.KatanaCombat;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Soul Resonance, earned with the katana's ritual (item.ResonanceRitual): the katana moves 25% faster
 * (animations, hits, and the attack recharge via this attack speed bonus), its thrust dashes further, hits
 * heal 5% of the damage dealt, and physical blows aimed at the player miss one time in four
 * (item.ResonanceRitual handles the last two).
 */
public class SoulResonanceEffect extends MobEffect {
    public SoulResonanceEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x4FB6FF);
        addAttributeModifier(Attributes.ATTACK_SPEED, "3b8f1e52-6c0d-4a7e-9f21-8d4c2b7a5e13", KatanaCombat.BLESSED_RATE - 1,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public double getAttributeModifierValue(int amplifier, AttributeModifier modifier) {
        return modifier.getAmount();
    }
}
