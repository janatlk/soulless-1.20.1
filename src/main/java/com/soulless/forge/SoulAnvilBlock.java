package com.soulless.forge;

import com.soulless.network.AnvilOpenPacket;
import com.soulless.network.ModNetwork;
import com.soulless.upgrade.Upgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The Soul Anvil. Right click:
 * <ul>
 *   <li>idle, with an artifact of the mod in hand - it is laid on, and the upgrade screen opens;</li>
 *   <li>idle, otherwise - the forge screen (categories, every artifact, the details of one);</li>
 *   <li>forging - the held ingredient (or a vessel's souls) goes in; Shift takes everything back;</li>
 *   <li>an artifact on it - the upgrade screen again; Shift takes it back.</li>
 * </ul>
 */
public class SoulAnvilBlock extends BaseEntityBlock {
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final int SCREEN_FORGE = 0, SCREEN_UPGRADE = 1;
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
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SoulAnvilBlockEntity(pos, state);
    }

    /** What may be laid on the anvil: an artifact with upgrades, or one the anvil can melt into souls. */
    public static boolean workable(ItemStack stack) {
        return Upgrades.tree(stack) != null || Upgrades.free(stack) != null || SoulAnvilRecipes.salvageable(stack);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || !(level.getBlockEntity(pos) instanceof SoulAnvilBlockEntity be)) return InteractionResult.PASS;
        ItemStack inHand = player.getMainHandItem();
        if (be.recipe() != null) {
            if (player.isShiftKeyDown()) be.cancel(player);
            else if (!be.feed(sp, inHand)) open(sp, pos, SCREEN_FORGE);
            return InteractionResult.CONSUME;
        }
        if (!be.held().isEmpty()) {
            if (player.isShiftKeyDown()) be.takeBack(player);
            else open(sp, pos, SCREEN_UPGRADE);
            return InteractionResult.CONSUME;
        }
        if (workable(inHand)) {
            be.place(inHand.split(1));
            open(sp, pos, SCREEN_UPGRADE);
        } else {
            open(sp, pos, SCREEN_FORGE);
        }
        return InteractionResult.CONSUME;
    }

    private static void open(ServerPlayer p, BlockPos pos, int screen) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new AnvilOpenPacket(pos, screen));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SoulAnvilBlockEntity be) be.dropAll();
        super.onRemove(state, level, pos, newState, moving);
    }
}
