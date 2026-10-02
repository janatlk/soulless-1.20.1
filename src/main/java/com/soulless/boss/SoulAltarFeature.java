package com.soulless.boss;

import com.mojang.serialization.Codec;
import com.soulless.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * The Soul Altar shrine found (very rarely) in soul sand valleys: a stepped obsidian / crying obsidian
 * platform, four crying-obsidian spires with soul fire, and the Soul Altar block in the middle.
 * The column is scanned downward for a floor with headroom, since the Nether has a ceiling.
 */
public class SoulAltarFeature extends Feature<NoneFeatureConfiguration> {
    public SoulAltarFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos origin = ctx.origin();
        BlockPos floor = null;
        for (int y = 100; y > 32; y--) {
            BlockPos p = new BlockPos(origin.getX(), y, origin.getZ());
            if (level.getBlockState(p).isSolid() && headroom(level, p.above(), 7)) {
                floor = p;
                break;
            }
        }
        if (floor == null) return false;
        build(level, floor.above());
        return true;
    }

    private static boolean headroom(WorldGenLevel level, BlockPos from, int h) {
        for (int i = 0; i < h; i++) {
            if (!level.getBlockState(from.above(i)).isAir()) return false;
        }
        return true;
    }

    /** {@code base} = the block position the altar block stands on top of (+1). Public for /soulless altar. */
    public static void build(net.minecraft.world.level.LevelAccessor level, BlockPos base) {
        BlockState obs = Blocks.OBSIDIAN.defaultBlockState();
        BlockState cry = Blocks.CRYING_OBSIDIAN.defaultBlockState();
        int r = SoulAltarBlockEntity.PLATFORM_RADIUS;
        // foundation down to the ground, then two stepped tiers
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 0.4) continue;
                for (int y = -1; y >= -6; y--) {
                    BlockPos p = base.offset(dx, y, dz);
                    if (level.getBlockState(p).isSolid()) break;
                    level.setBlock(p, obs, 2);
                }
                boolean ring = d > r - 0.6;
                level.setBlock(base.offset(dx, -1, dz), ring && ((dx + dz) & 1) == 0 ? cry : obs, 2);
                for (int y = 0; y < SoulAltarBlockEntity.ARENA_HEIGHT; y++) level.setBlock(base.offset(dx, y, dz), Blocks.AIR.defaultBlockState(), 2);
                if (d <= 2.4) level.setBlock(base.offset(dx, 0, dz), d <= 1.2 ? cry : obs, 2);
            }
        }
        // the altar on the raised dais
        level.setBlock(base.above(), ModBlocks.SOUL_ALTAR.get().defaultBlockState(), 2);
        // four spires at the corners of the platform
        int s = r - 1;
        for (int[] c : new int[][]{{s, s}, {-s, s}, {s, -s}, {-s, -s}}) {
            for (int y = 0; y < 4; y++) {
                level.setBlock(base.offset(c[0], y, c[1]), y == 3 ? Blocks.SOUL_SOIL.defaultBlockState() : (y % 2 == 0 ? cry : obs), 2);
            }
            level.setBlock(base.offset(c[0], 4, c[1]), Blocks.SOUL_FIRE.defaultBlockState(), 2);
        }
    }
}
