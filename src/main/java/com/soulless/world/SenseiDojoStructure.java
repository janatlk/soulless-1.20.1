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
 * The sensei's dojo: a small Japanese house with a training yard, high up in cherry groves (worldgen/structure +
 * structure_set: about half as common as villages, and only where the ground is high and fairly level).
 * Built by {@link SenseiDojoPiece}.
 */
public class SenseiDojoStructure extends Structure {
    public static final Codec<SenseiDojoStructure> CODEC = simpleCodec(SenseiDojoStructure::new);
    /** "High mountains": the site's lowest corner must be at least this high. */
    public static final int MIN_Y = 100;
    /** The site must be fairly level: at most this much between its highest and lowest corner. */
    private static final int MAX_SLOPE = 9;

    public SenseiDojoStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        ChunkPos chunk = ctx.chunkPos();
        int x0 = chunk.getMinBlockX(), z0 = chunk.getMinBlockZ();
        int[][] samples = {{0, 0}, {SenseiDojoPiece.WIDTH - 1, 0}, {0, SenseiDojoPiece.DEPTH - 1},
                {SenseiDojoPiece.WIDTH - 1, SenseiDojoPiece.DEPTH - 1}, {SenseiDojoPiece.WIDTH / 2, SenseiDojoPiece.DEPTH / 2}};
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, sum = 0;
        for (int[] s : samples) {
            int y = ctx.chunkGenerator().getFirstOccupiedHeight(x0 + s[0], z0 + s[1], Heightmap.Types.WORLD_SURFACE_WG,
                    ctx.heightAccessor(), ctx.randomState());
            min = Math.min(min, y);
            max = Math.max(max, y);
            sum += y;
        }
        if (min < MIN_Y || max - min > MAX_SLOPE) return Optional.empty();
        int floorY = Math.round(sum / (float) samples.length);
        BlockPos origin = new BlockPos(x0, floorY, z0);
        return Optional.of(new GenerationStub(origin, builder -> builder.addPiece(new SenseiDojoPiece(origin))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.SENSEI_DOJO.get();
    }
}
