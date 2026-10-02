package com.soulless.boss;

import com.soulless.registry.ModBlocks;
import com.soulless.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Soul Altar: an obsidian block with a socket on top. Placing an Abyssal Heart in it starts the ritual
 * (see {@link SoulAltarBlockEntity}): the arena rises, the boss bar fills and the Wailing Reaper appears.
 */
public class SoulAltarBlock extends BaseEntityBlock {
    public static final BooleanProperty FILLED = BooleanProperty.create("filled");

    public SoulAltarBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FILLED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILLED);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (state.getValue(FILLED) || !stack.is(ModItems.ABYSSAL_HEART.get())) {
            if (!level.isClientSide && !state.getValue(FILLED) && hand == InteractionHand.MAIN_HAND) {
                player.displayClientMessage(Component.translatable("message.soulless.altar_needs_heart"), true);
            }
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SoulAltarBlockEntity altar) {
            if (!player.getAbilities().instabuild) stack.shrink(1);
            level.setBlock(pos, state.setValue(FILLED, true), 3);
            level.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.2F, 0.6F);
            level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6F, 1.6F);
            ((ServerLevel) level).sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 40, 0.4, 0.4, 0.4, 0.08);
            altar.startRitual();
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rnd) {
        int n = state.getValue(FILLED) ? 3 : 1;
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 0.6 + rnd.nextDouble() * 0.5;
            level.addParticle(ParticleTypes.SOUL, pos.getX() + 0.5 + Math.cos(a) * r, pos.getY() + 0.6 + rnd.nextDouble() * 0.6,
                    pos.getZ() + 0.5 + Math.sin(a) * r, 0, 0.02 + rnd.nextDouble() * 0.02, 0);
        }
        if (state.getValue(FILLED)) {
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 0, 0.03, 0);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SoulAltarBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlocks.SOUL_ALTAR_BE.get(), SoulAltarBlockEntity::serverTick);
    }
}
