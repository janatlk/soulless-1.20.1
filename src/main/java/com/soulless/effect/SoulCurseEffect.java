package com.soulless.effect;

import com.soulless.registry.ModDamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Soul Curse: purple soul fire. Damage over time that ignores armour, slows, and stops jumping
 * (the jump block lives in {@link SoulCurse#onJump}). Replaces vanilla fire instead of stacking with it.
 */
public class SoulCurseEffect extends MobEffect {
    public SoulCurseEffect() {
        super(MobEffectCategory.HARMFUL, 0x9A2CF0);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, "0b6f3a52-2c8e-4f0e-b1a7-6a3f0f3c9d11",
                -SoulCurse.SLOWNESS, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) {
            entity.hurt(ModDamageTypes.soulCurse(entity.level()), SoulCurse.DAMAGE_PER_SECOND + amplifier);
        }
    }

    // Slowness stays at its listed value at every amplifier; only the burn grows stronger.
    @Override
    public double getAttributeModifierValue(int amplifier, AttributeModifier modifier) {
        return modifier.getAmount();
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }
}
