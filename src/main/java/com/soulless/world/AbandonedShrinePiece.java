package com.soulless.world;

import com.soulless.registry.ModItems;
import com.soulless.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * The abandoned shrine of the sensei's family, built block by block. Local coordinates: x 0..12, z 0..14 (the gate at
 * z = 14, the hall to the north), y 0 = the ground. A cracked, mossy stone platform; a broken torii (one pillar
 * standing, the other snapped, its beam fallen); one stone lantern standing, one toppled; a small dark-oak hall, roof
 * half fallen in, cobwebs and vines; inside, on a stone altar, a chest with the Scroll of the Family History.
 */
public class AbandonedShrinePiece extends StructurePiece {
    public static final int WIDTH = 13, DEPTH = 15;
    private static final int UP = 9, DOWN = 4;
    private final BlockPos origin;

    public AbandonedShrinePiece(BlockPos origin) {
        super(ModStructures.ABANDONED_SHRINE_PIECE.get(), 0,
                new BoundingBox(origin.getX(), origin.getY() - DOWN, origin.getZ(), origin.getX() + WIDTH - 1, origin.getY() + UP, origin.getZ() + DEPTH - 1));
        this.origin = origin;
    }

    public AbandonedShrinePiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
        super(ModStructures.ABANDONED_SHRINE_PIECE.get(), tag);
        this.origin = new BlockPos(tag.getInt("OX"), tag.getInt("OY"), tag.getInt("OZ"));
    }

    /** Builds the whole shrine at once with its north-west corner (ground level) at {@code origin} ({@code /soulless shrine}). */
    public static void buildAt(net.minecraft.server.level.ServerLevel level, BlockPos origin) {
        AbandonedShrinePiece piece = new AbandonedShrinePiece(origin);
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.random,
                piece.getBoundingBox(), new ChunkPos(origin), origin);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        tag.putInt("OX", origin.getX());
        tag.putInt("OY", origin.getY());
        tag.putInt("OZ", origin.getZ());
    }

    // ------------------------------------------------------------------ placement helpers (clipped to the chunk)

    private WorldGenLevel level;
    private BoundingBox box;
    private RandomSource random;

    private BlockPos at(int x, int y, int z) {
        return origin.offset(x, y, z);
    }

    private void set(int x, int y, int z, BlockState s) {
        BlockPos p = at(x, y, z);
        if (box.isInside(p)) level.setBlock(p, s, 2);
    }

    private void set(int x, int y, int z, Block b) {
        set(x, y, z, b.defaultBlockState());
    }

    private void fill(int x0, int y0, int z0, int x1, int y1, int z1, Block b) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(x, y, z, b.defaultBlockState());
    }

    private boolean chance(float p) {
        return random.nextFloat() < p;
    }

    private Block weathered() {
        float r = random.nextFloat();
        return r < 0.35F ? Blocks.MOSSY_STONE_BRICKS : r < 0.55F ? Blocks.CRACKED_STONE_BRICKS : r < 0.7F ? Blocks.MOSSY_COBBLESTONE : Blocks.STONE_BRICKS;
    }

    private static BlockState stair(Block b, Direction facing) {
        return b.defaultBlockState().setValue(StairBlock.FACING, facing);
    }

    private static BlockState log(Block b, Direction.Axis axis) {
        return b.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunk, BlockPos pivot) {
        this.level = level;
        this.box = box;
        this.random = RandomSource.create(origin.asLong());   // the same ruin in every chunk it spans
        ground();
        torii();
        lanterns();
        hall();
        overgrowth();
    }

    /** A platform of old stone over solid footing; the air above it cleared. */
    private void ground() {
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < DEPTH; z++) {
                for (int y = 1; y <= UP; y++) set(x, y, z, Blocks.AIR);
                for (int y = -DOWN; y < 0; y++) {
                    BlockPos p = at(x, y, z);
                    if (box.isInside(p) && !level.getBlockState(p).isSolid()) set(x, y, z, Blocks.DIRT);
                }
                boolean edge = x == 0 || z == 0 || x == WIDTH - 1 || z == DEPTH - 1;
                if (edge && chance(0.45F)) set(x, 0, z, Blocks.GRASS_BLOCK);
                else set(x, 0, z, chance(0.12F) ? Blocks.COARSE_DIRT : weathered());
            }
        }
        // the old path from the gate to the hall
        for (int z = 8; z < DEPTH; z++) {
            for (int x = 5; x <= 7; x++) set(x, 0, z, chance(0.3F) ? Blocks.GRAVEL : Blocks.COBBLESTONE);
        }
    }

    /** The gate: the left pillar still up with a stub of the beam, the right one snapped, the beam fallen beside it. */
    private void torii() {
        BlockState pillar = log(Blocks.STRIPPED_MANGROVE_LOG, Direction.Axis.Y);
        for (int y = 1; y <= 5; y++) set(3, y, 13, pillar);
        set(9, 1, 13, pillar);
        set(9, 2, 13, Blocks.MANGROVE_FENCE);
        set(2, 5, 13, Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        for (int x = 2; x <= 5; x++) set(x, 6, 13, Blocks.DARK_OAK_SLAB);
        set(4, 5, 13, log(Blocks.STRIPPED_MANGROVE_LOG, Direction.Axis.X));
        // the fallen beam
        for (int x = 8; x <= 11; x++) set(x, 1, 11, log(Blocks.STRIPPED_MANGROVE_LOG, Direction.Axis.X));
        set(12, 1, 12, Blocks.DARK_OAK_SLAB);
    }

    /** Stone lanterns: one still standing (its candle long out), one knocked over. */
    private void lanterns() {
        set(2, 1, 9, Blocks.STONE_BRICK_WALL);
        set(2, 2, 9, Blocks.CHISELED_STONE_BRICKS);
        set(2, 3, 9, Blocks.CANDLE);
        set(2, 3, 9, Blocks.STONE_BRICK_SLAB);
        set(2, 4, 9, Blocks.CANDLE);
        set(10, 1, 9, stair(Blocks.STONE_BRICK_STAIRS, Direction.EAST));
        set(11, 1, 9, Blocks.CHISELED_STONE_BRICKS);
        set(11, 1, 10, Blocks.STONE_BRICK_SLAB);
    }

    /** The hall: dark oak on a stone sill, gaps in the walls, the roof fallen in on the east side. */
    private void hall() {
        int x0 = 3, x1 = 9, z0 = 1, z1 = 7;
        fill(x0, 0, z0, x1, 0, z1, Blocks.STONE_BRICKS);
        for (int x = x0 + 1; x < x1; x++) for (int z = z0 + 1; z < z1; z++) set(x, 0, z, chance(0.25F) ? Blocks.MOSSY_STONE_BRICKS : Blocks.DARK_OAK_PLANKS);
        BlockState post = log(Blocks.DARK_OAK_LOG, Direction.Axis.Y);
        for (int y = 1; y <= 4; y++) {
            set(x0, y, z0, post);
            set(x1, y, z0, post);
            set(x0, y, z1, post);
            if (y <= 2) set(x1, y, z1, post);   // this corner snapped
        }
        for (int y = 1; y <= 3; y++) {
            for (int x = x0 + 1; x < x1; x++) {
                if (!chance(y == 3 ? 0.45F : 0.2F)) set(x, y, z0, Blocks.DARK_OAK_PLANKS);
            }
            for (int z = z0 + 1; z < z1; z++) {
                if (!chance(0.25F)) set(x0, y, z, Blocks.DARK_OAK_PLANKS);
                if (!chance(y >= 2 ? 0.6F : 0.3F)) set(x1, y, z, Blocks.DARK_OAK_PLANKS);
            }
            // the front: a doorway in the middle, the rest half gone
            for (int x = x0 + 1; x < x1; x++) {
                if (Math.abs(x - 6) <= 1) continue;
                if (!chance(0.4F)) set(x, y, z1, Blocks.DARK_OAK_PLANKS);
            }
        }
        // the roof: stairs sloping both ways; the east half has fallen in
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            boolean fallen = x >= 7 && chance(0.7F);
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                int rise = Math.min(z - (z0 - 1), (z1 + 1) - z);
                if (rise > 3 || fallen) continue;
                Direction face = z <= (z0 + z1) / 2 ? Direction.SOUTH : Direction.NORTH;
                set(x, 4 + rise, z, rise == 3 ? Blocks.DARK_OAK_SLAB.defaultBlockState() : stair(Blocks.DARK_OAK_STAIRS, face));
            }
        }
        // rubble from the fallen roof
        set(8, 1, 5, stair(Blocks.DARK_OAK_STAIRS, Direction.WEST));
        set(7, 1, 3, Blocks.DARK_OAK_SLAB);
        // the altar and the chest with the scroll
        set(6, 1, 2, Blocks.CHISELED_STONE_BRICKS);
        set(5, 1, 2, stair(Blocks.STONE_BRICK_STAIRS, Direction.EAST));
        set(7, 1, 2, stair(Blocks.STONE_BRICK_STAIRS, Direction.WEST));
        set(6, 2, 2, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
        set(5, 2, 2, Blocks.CANDLE);
        set(7, 2, 2, Blocks.CANDLE);
        BlockPos chest = at(6, 2, 2);
        if (box.isInside(chest) && level.getBlockEntity(chest) instanceof ChestBlockEntity c) {
            c.setItem(13, new ItemStack(ModItems.FAMILY_SCROLL.get()));
            c.setItem(4, new ItemStack(Items.CANDLE, 3));
            c.setItem(21, new ItemStack(Items.PAPER, 5));
            c.setItem(10, new ItemStack(Items.BONE, 2));
        }
        for (int i = 0; i < 6; i++) set(x0 + 1 + random.nextInt(5), 3, z0 + 1 + random.nextInt(5), Blocks.COBWEB);
    }

    /** Moss and ferns on the ground, vines on what still stands. */
    private void overgrowth() {
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < DEPTH; z++) {
                BlockPos p = at(x, 1, z);
                if (!box.isInside(p) || !level.getBlockState(p).isAir()) continue;
                float r = random.nextFloat();
                if (r < 0.12F) set(x, 1, z, Blocks.MOSS_CARPET);
                else if (r < 0.18F) set(x, 1, z, Blocks.FERN);
            }
        }
        for (int y = 1; y <= 4; y++) {
            if (chance(0.7F)) set(2, y, 1, Blocks.VINE.defaultBlockState().setValue(VineBlock.EAST, true));
            if (chance(0.6F)) set(10, y, 3, Blocks.VINE.defaultBlockState().setValue(VineBlock.WEST, true));
            if (chance(0.5F)) set(4, y, 12, Blocks.VINE.defaultBlockState().setValue(VineBlock.WEST, true));
        }
    }
}
