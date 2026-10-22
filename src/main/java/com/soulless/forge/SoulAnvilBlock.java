package com.soulless.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The Soul Anvil: right click opens the forge (SoulAnvilMenu). Shaped like an anvil, facing the one who placed it. */
public class SoulAnvilBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape NS = Shapes.or(box(2, 0, 2, 14, 4, 14), box(4, 4, 3, 12, 5, 13), box(6, 5, 4, 10, 10, 12),
            box(3, 10, 0, 13, 16, 16));
    private static final VoxelShape EW = Shapes.or(box(2, 0, 2, 14, 4, 14), box(3, 4, 4, 13, 5, 12), box(4, 5, 6, 12, 10, 10),
            box(0, 10, 3, 16, 16, 13));

    public SoulAnvilBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getClockWise());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? EW : NS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) {
            sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new SoulAnvilMenu(id, inv, ContainerLevelAccess.create(level, pos)),
                    Component.translatable("container.soulless.soul_anvil")));
        }
        return InteractionResult.CONSUME;
    }
}
