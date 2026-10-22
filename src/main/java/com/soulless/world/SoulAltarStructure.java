package com.soulless.world;

import com.mojang.serialization.Codec;
import com.soulless.boss.SoulAltarBlockEntity;
import com.soulless.boss.SoulAltarFeature;
import com.soulless.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.Optional;

/**
 * The Wailing Reaper's altar as a real structure (so the Eye of Lament and /locate can find it): soul sand valleys,
 * on a floor with room above it under the Nether's ceiling. Built by {@link SoulAltarFeature#build}.
 */
public class SoulAltarStructure extends Structure {
    public static final Codec<SoulAltarStructure> CODEC = simpleCodec(SoulAltarStructure::new);

    public SoulAltarStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        ChunkPos chunk = ctx.chunkPos();
        int x = chunk.getMiddleBlockX(), z = chunk.getMiddleBlockZ();
        NoiseColumn col = ctx.chunkGenerator().getBaseColumn(x, z, ctx.heightAccessor(), ctx.randomState());
        for (int y = 100; y > 32; y--) {
            if (!col.getBlock(y).isAir() && clear(col, y + 1, SoulAltarBlockEntity.ARENA_HEIGHT)) {
                BlockPos base = new BlockPos(x, y + 1, z);
                return Optional.of(new GenerationStub(base, b -> b.addPiece(new Piece(base))));
            }
        }
        return Optional.empty();
    }

    private static boolean clear(NoiseColumn col, int from, int h) {
        for (int i = 0; i < h; i++) if (!col.getBlock(from + i).isAir()) return false;
        return true;
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.SOUL_ALTAR.get();
    }

    public static class Piece extends StructurePiece {
        private final BlockPos base;

        public Piece(BlockPos base) {
            super(ModStructures.SOUL_ALTAR_PIECE.get(), 0, new BoundingBox(base.getX() - SoulAltarFeature.REACH, base.getY() - 7,
                    base.getZ() - SoulAltarFeature.REACH, base.getX() + SoulAltarFeature.REACH, base.getY() + SoulAltarBlockEntity.ARENA_HEIGHT,
                    base.getZ() + SoulAltarFeature.REACH));
            this.base = base;
        }

        public Piece(StructurePieceSerializationContext ctx, CompoundTag tag) {
            super(ModStructures.SOUL_ALTAR_PIECE.get(), tag);
            this.base = NbtUtils.readBlockPos(tag.getCompound("Base"));
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
            tag.put("Base", NbtUtils.writeBlockPos(base));
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator gen, RandomSource random, BoundingBox chunkBox,
                                ChunkPos chunk, BlockPos pivot) {
            SoulAltarFeature.build(level, base, chunkBox);
        }
    }
}
