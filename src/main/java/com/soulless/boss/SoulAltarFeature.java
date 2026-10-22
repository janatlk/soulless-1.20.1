package com.soulless.boss;

import com.soulless.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * The Soul Altar shrine (world.SoulAltarStructure places it in soul sand valleys): a stepped obsidian / crying
 * obsidian platform, four crying-obsidian spires with soul fire, and the Soul Altar block in the middle.
 */
public final class SoulAltarFeature {
    /** How far the shrine reaches from its centre (for the structure's bounding box). */
    public static final int REACH = SoulAltarBlockEntity.PLATFORM_RADIUS + 1;

    /** {@code base} = the block position the altar block stands on top of (+1). Public for /soulless altar. */
    public static void build(LevelAccessor level, BlockPos base) {
        build(level, base, null);
    }

    /** As above, writing only inside {@code clip} (world generation goes chunk by chunk). */
    public static void build(LevelAccessor level, BlockPos base, @Nullable BoundingBox clip) {
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
                    set(level, clip, p, obs);
                }
                boolean ring = d > r - 0.6;
                set(level, clip, base.offset(dx, -1, dz), ring && ((dx + dz) & 1) == 0 ? cry : obs);
                for (int y = 0; y < SoulAltarBlockEntity.ARENA_HEIGHT; y++) set(level, clip, base.offset(dx, y, dz), Blocks.AIR.defaultBlockState());
                if (d <= 2.4) set(level, clip, base.offset(dx, 0, dz), d <= 1.2 ? cry : obs);
            }
        }
        // the altar on the raised dais
        set(level, clip, base.above(), ModBlocks.SOUL_ALTAR.get().defaultBlockState());
        // four spires at the corners of the platform
        int s = r - 1;
        for (int[] c : new int[][]{{s, s}, {-s, s}, {s, -s}, {-s, -s}}) {
            for (int y = 0; y < 4; y++) {
                set(level, clip, base.offset(c[0], y, c[1]), y == 3 ? Blocks.SOUL_SOIL.defaultBlockState() : (y % 2 == 0 ? cry : obs));
            }
            set(level, clip, base.offset(c[0], 4, c[1]), Blocks.SOUL_FIRE.defaultBlockState());
        }
    }

    private static void set(LevelAccessor level, @Nullable BoundingBox clip, BlockPos p, BlockState s) {
        if (clip == null || clip.isInside(p)) level.setBlock(p, s, 2);
    }

    private SoulAltarFeature() {}
}
