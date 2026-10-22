package com.soulless.world;

import com.mojang.serialization.Codec;
import com.soulless.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.Optional;

/**
 * Drowned Souls: a sunken arena on the floor of a deep ocean, very rare, always under water and flooded through -
 * a round floor of dark prismarine, a broken ring wall, toppled and standing pillars with drowned soul lanterns,
 * a seal in the middle. A boss will live here; for now it stands empty. The Eye of the Drowned leads to it.
 */
public class DrownedSoulsStructure extends Structure {
    public static final Codec<DrownedSoulsStructure> CODEC = simpleCodec(DrownedSoulsStructure::new);
    public static final int RADIUS = 18, HEIGHT = 14;

    public DrownedSoulsStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        ChunkPos chunk = ctx.chunkPos();
        int x = chunk.getMiddleBlockX(), z = chunk.getMiddleBlockZ();
        var gen = ctx.chunkGenerator();
        int floor = gen.getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(), ctx.randomState());
        int surface = gen.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
        // deep enough that the whole arena stays under water
        if (surface - floor < HEIGHT + 4 || surface < gen.getSeaLevel() - 1) return Optional.empty();
        BlockPos base = new BlockPos(x, floor, z);
        return Optional.of(new GenerationStub(base, b -> b.addPiece(new Piece(base))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.DROWNED_SOULS.get();
    }

    public static class Piece extends StructurePiece {
        private final BlockPos base;

        public Piece(BlockPos base) {
            super(ModStructures.DROWNED_SOULS_PIECE.get(), 0, new BoundingBox(base.getX() - RADIUS - 2, base.getY() - 8, base.getZ() - RADIUS - 2,
                    base.getX() + RADIUS + 2, base.getY() + HEIGHT, base.getZ() + RADIUS + 2));
            this.base = base;
        }

        public Piece(StructurePieceSerializationContext ctx, CompoundTag tag) {
            super(ModStructures.DROWNED_SOULS_PIECE.get(), tag);
            this.base = NbtUtils.readBlockPos(tag.getCompound("Base"));
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
            tag.put("Base", NbtUtils.writeBlockPos(base));
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator gen, RandomSource random, BoundingBox box,
                                ChunkPos chunk, BlockPos pivot) {
            build(level, base, box);
        }
    }

    /** Builds the arena around {@code base} (the floor's centre), writing only inside {@code clip}. */
    public static void build(WorldGenLevel level, BlockPos base, BoundingBox clip) {
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState floor = Blocks.DARK_PRISMARINE.defaultBlockState(), rim = Blocks.PRISMARINE_BRICKS.defaultBlockState();
        BlockState tile = Blocks.DEEPSLATE_TILES.defaultBlockState(), cracked = Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
        BlockState brick = Blocks.DEEPSLATE_BRICKS.defaultBlockState(), cbrick = Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
        long seed = base.asLong();
        for (int dx = -RADIUS - 1; dx <= RADIUS + 1; dx++) {
            for (int dz = -RADIUS - 1; dz <= RADIUS + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > RADIUS + 1.2) continue;
                int h = hash(seed, dx, dz);
                // foundation down to the sea floor
                for (int y = -1; y >= -8; y--) {
                    BlockPos p = base.offset(dx, y, dz);
                    if (!clip.isInside(p)) break;
                    BlockState here = level.getBlockState(p);
                    if (here.isSolid() && !here.is(Blocks.WATER)) break;
                    level.setBlock(p, h % 3 == 0 ? cracked : tile, 2);
                }
                // the floor: rings of prismarine, deepslate in between, a seal in the middle
                BlockState f;
                if (d > RADIUS - 0.6) f = rim;
                else if (d < 2.6) f = d < 1.2 ? Blocks.CHISELED_DEEPSLATE.defaultBlockState() : Blocks.POLISHED_DEEPSLATE.defaultBlockState();
                else if (Math.abs(d - 8) < 0.6 || Math.abs(d - 13) < 0.6) f = rim;
                else f = h % 11 == 0 ? Blocks.PRISMARINE.defaultBlockState() : h % 7 == 0 ? cracked : floor;
                put(level, clip, base.offset(dx, 0, dz), f);
                // flooded through
                for (int y = 1; y <= HEIGHT; y++) put(level, clip, base.offset(dx, y, dz), water);
                // the ring wall, broken in places
                if (d > RADIUS - 0.6 && d <= RADIUS + 1.2) {
                    int top = 2 + h % 5;
                    if (h % 4 == 0) top = 1;
                    for (int y = 1; y <= top; y++) put(level, clip, base.offset(dx, y, dz), y == top && h % 3 == 0 ? cbrick : brick);
                }
                if (d < 2.6 && d > 1.2 && h % 5 == 0) put(level, clip, base.offset(dx, 1, dz), Blocks.SEA_PICKLE.defaultBlockState()
                        .setValue(BlockStateProperties.PICKLES, 1 + h % 4));
            }
        }
        // eight pillars round the middle: some standing with a drowned soul lantern, some broken off
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8 + 0.2;
            int px = Mth.floor(Math.cos(a) * 11), pz = Mth.floor(Math.sin(a) * 11);
            int h = hash(seed, i, 99);
            boolean standing = h % 3 != 0;
            int top = standing ? 7 + h % 4 : 2 + h % 3;
            for (int y = 1; y <= top; y++) {
                for (int[] o : new int[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}}) {
                    put(level, clip, base.offset(px + o[0], y, pz + o[1]), y == 1 || y == top ? Blocks.POLISHED_DEEPSLATE.defaultBlockState()
                            : (y + o[0] + h) % 4 == 0 ? cbrick : brick);
                }
            }
            if (standing) {
                put(level, clip, base.offset(px, top + 1, pz), Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.WATERLOGGED, true));
                put(level, clip, base.offset(px + 1, top + 1, pz + 1), Blocks.CHAIN.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
            } else {
                // its broken top lies on the floor beside it
                for (int k = 1; k <= 3; k++) put(level, clip, base.offset(px + k + 1, 1, pz), cbrick);
            }
        }
    }

    private static void put(WorldGenLevel level, BoundingBox clip, BlockPos p, BlockState s) {
        if (clip.isInside(p)) level.setBlock(p, s, 2);
    }

    private static int hash(long seed, int a, int b) {
        long h = seed * 31 + a * 73856093L ^ b * 19349663L;
        h ^= h >>> 17;
        h *= 0x9E3779B97F4A7C15L;
        return (int) ((h >>> 33) & 0x7FFFFFFF);
    }
}
