package com.soulless.item;

import com.soulless.Soulless;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Passive "Soul Speed I" while Soul Forged is held: the same speed bonus the boot enchantment gives on
 * soul sand / soul soil, including ignoring soul sand's drag, with soul wisps at the feet.
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class SoulSpeed {
    /** Vanilla Soul Speed: +0.03 * (1 + level * 0.35) movement speed. */
    public static final double BONUS = 0.03 * (1 + 0.35);
    /** Soul sand multiplies speed by 0.4; Soul Speed ignores that. 1 / 0.4 = 2.5, i.e. +150%. */
    private static final double SOUL_SAND_COMPENSATION = 1.5;
    private static final UUID BONUS_UUID = UUID.fromString("9e4c2a1b-5d3f-4e8a-b7c6-1a2b3c4d5e6f");
    private static final UUID SAND_UUID = UUID.fromString("0f1e2d3c-4b5a-4968-8776-5a4b3c2d1e0f");

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        BlockPos on = player.getOnPos();
        BlockState state = player.level().getBlockState(on);
        boolean active = player.getMainHandItem().getItem() instanceof SoulForgedItem
                && player.onGround() && state.is(BlockTags.SOUL_SPEED_BLOCKS);
        boolean onSand = active && state.is(Blocks.SOUL_SAND);

        set(speed, BONUS_UUID, active ? BONUS : 0, AttributeModifier.Operation.ADDITION);
        set(speed, SAND_UUID, onSand ? SOUL_SAND_COMPENSATION : 0, AttributeModifier.Operation.MULTIPLY_TOTAL);

        if (active && player.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4 && player.getRandom().nextInt(4) == 0) {
            ((ServerLevel) player.level()).sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 0.1, player.getZ(),
                    1, 0.2, 0, 0.2, 0.02);
        }
    }

    private static void set(AttributeInstance attr, UUID id, double amount, AttributeModifier.Operation op) {
        AttributeModifier existing = attr.getModifier(id);
        if (amount == 0) {
            if (existing != null) attr.removeModifier(id);
        } else if (existing == null) {
            attr.addTransientModifier(new AttributeModifier(id, "Soul Forged soul speed", amount, op));
        }
    }

    private SoulSpeed() {}
}
