package com.soulless.world;

import com.mojang.serialization.Codec;
import com.soulless.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * The abandoned shrine of the sensei's family (STORY_KATANA.md, act 1): a small ruin in forests and taigas, on level
 * ground above the sea. The sensei's map leads to the nearest one. Built by {@link AbandonedShrinePiece}.
 */
public class AbandonedShrineStructure extends Structure {
    public static final Codec<AbandonedShrineStructure> CODEC = simpleCodec(AbandonedShrineStructure::new);
    private static final int MAX_SLOPE = 4;

    public AbandonedShrineStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        ChunkPos chunk = ctx.chunkPos();
        int x0 = chunk.getMinBlockX(), z0 = chunk.getMinBlockZ();
        int w = AbandonedShrinePiece.WIDTH - 1, d = AbandonedShrinePiece.DEPTH - 1;
        int[][] samples = {{0, 0}, {w, 0}, {0, d}, {w, d}, {w / 2, d / 2}};
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, sum = 0;
        for (int[] s : samples) {
            int y = ctx.chunkGenerator().getFirstOccupiedHeight(x0 + s[0], z0 + s[1], Heightmap.Types.WORLD_SURFACE_WG,
                    ctx.heightAccessor(), ctx.randomState());
            min = Math.min(min, y);
            max = Math.max(max, y);
            sum += y;
        }
        if (min <= ctx.chunkGenerator().getSeaLevel() || max - min > MAX_SLOPE) return Optional.empty();
        BlockPos origin = new BlockPos(x0, Math.round(sum / (float) samples.length) - 1, z0);
        return Optional.of(new GenerationStub(origin, builder -> builder.addPiece(new AbandonedShrinePiece(origin))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.ABANDONED_SHRINE.get();
    }
}
