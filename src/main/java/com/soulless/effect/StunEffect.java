package com.soulless.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Stunned: cannot move, jump or attack. Movement is zeroed here; jumping, AI and attacks are blocked in
 * {@link Stun}.
 */
public class StunEffect extends MobEffect {
    public StunEffect() {
        super(MobEffectCategory.HARMFUL, 0xE8D25A);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, "6f0d1c3a-9b2e-4c7a-8d51-2e3f4a5b6c7d", -1.0,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public double getAttributeModifierValue(int amplifier, AttributeModifier modifier) {
        return modifier.getAmount();
    }
}
