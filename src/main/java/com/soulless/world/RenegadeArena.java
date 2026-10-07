package com.soulless.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.core.Direction;

/**
 * The Renegade's arena in the dark astral (boss.RenegadeEncounter): a great floating slab of black stone in the void,
 * its top at {@code y}. The duelling circle in the middle - blackstone brick paving, a seal at the centre, a ring of
 * red stone at radius 13 - eight stone lanterns round it, then a wide paved field of stone and basalt with broken torii
 * standing in it, the edge ragged and crumbling, stone hanging below like roots, a few fragments drifting off.
 * Built once (the centre seal marks it) over whatever was there - the old invisible floor.
 */
public final class RenegadeArena {
    public static final int RADIUS = 56;
    /** The red ring that bounds the duel. */
    public static final int RING = 13;

    public static boolean built(ServerLevel level, int y) {
        return level.getBlockState(new BlockPos(0, y, 0)).is(Blocks.CHISELED_POLISHED_BLACKSTONE);
    }

    public static void build(ServerLevel level, int y) {
        for (int x = -RADIUS - 8; x <= RADIUS + 8; x++) {
            for (int z = -RADIUS - 8; z <= RADIUS + 8; z++) {
                column(level, x, y, z);
            }
        }
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8 + Math.PI / 8;
            lantern(level, new BlockPos(Mth.floor(Math.cos(a) * (RING + 3.5)), y + 1, Mth.floor(Math.sin(a) * (RING + 3.5))));
        }
        // torii: the gate he waits beyond, and two more out in the field, one fallen
        torii(level, new BlockPos(0, y + 1, -30), false, false);
        torii(level, new BlockPos(30, y + 1, 12), true, false);
        torii(level, new BlockPos(-27, y + 1, 18), true, true);
        // fragments drifting off the edge
        java.util.Random r = new java.util.Random(77);
        for (int i = 0; i < 18; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = RADIUS + 6 + r.nextDouble() * 14;
            BlockPos c = new BlockPos(Mth.floor(Math.cos(a) * d), y - 6 + r.nextInt(14), Mth.floor(Math.sin(a) * d));
            int s = 1 + r.nextInt(3);
            for (int dx = -s; dx <= s; dx++)
                for (int dz = -s; dz <= s; dz++)
                    for (int dy = -s; dy <= 0; dy++)
                        if (dx * dx + dz * dz + dy * dy * 2 <= s * s + r.nextInt(2)) set(level, c.offset(dx, dy, dz), stone(r.nextInt(10)));
        }
    }

    /** Smooth value noise, 0..1. */
    private static double noise(double x, double z, int seed) {
        int x0 = Mth.floor(x), z0 = Mth.floor(z);
        double fx = x - x0, fz = z - z0;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = Mth.lerp(fx, hash(x0, z0, seed), hash(x0 + 1, z0, seed));
        double b = Mth.lerp(fx, hash(x0, z0 + 1, seed), hash(x0 + 1, z0 + 1, seed));
        return Mth.lerp(fz, a, b);
    }

    private static double hash(int x, int z, int seed) {
        long h = x * 341873128712L + z * 132897987541L + seed * 1000003L;
        h = (h ^ (h >>> 13)) * 0x5DEECE66DL;
        return ((h ^ (h >>> 17)) & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    private static BlockState stone(int k) {
        return switch (k % 10) {
            case 0, 1, 2 -> Blocks.BLACKSTONE.defaultBlockState();
            case 3, 4 -> Blocks.POLISHED_BLACKSTONE.defaultBlockState();
            case 5 -> Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
            case 6, 7 -> Blocks.SMOOTH_BASALT.defaultBlockState();
            default -> Blocks.DEEPSLATE.defaultBlockState();
        };
    }

    private static void column(ServerLevel level, int x, int y, int z) {
        double r = Math.sqrt(x * x + z * z);
        // the ragged edge: the radius wanders with the angle, and holes open near it
        double ang = Math.atan2(z, x);
        double edge = RADIUS - 7 * noise(Math.cos(ang) * 4 + 9, Math.sin(ang) * 4 + 9, 3) - 3 * noise(x * 0.3, z * 0.3, 5);
        if (r > edge) return;
        double near = (r - (edge - 8)) / 8;   // 0..1 over the last 8 blocks
        if (near > 0 && noise(x * 0.45, z * 0.45, 7) < near * 0.55) return;
        int h = (int) Math.floor(hash(x, z, 11) * 10);
        BlockState top;
        if (r < 2.6) {
            top = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();                        // the seal
        } else if (r < 3.6) {
            top = Blocks.RED_NETHER_BRICKS.defaultBlockState();
        } else if (r < RING) {
            boolean seam = Math.abs(x) <= 0 || Math.abs(z) <= 0;                                // a cross through the seal
            top = seam ? Blocks.POLISHED_BLACKSTONE.defaultBlockState()
                    : h == 0 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        } else if (r < RING + 1) {
            top = Blocks.RED_NETHER_BRICKS.defaultBlockState();                                   // the duel's bound
        } else if (r < RING + 1.8) {
            top = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        } else {
            double n = noise(x * 0.12, z * 0.12, 1);
            if (near > 0) top = stone(h);                                                          // rubble at the edge
            else if (n < 0.3) top = Blocks.SMOOTH_BASALT.defaultBlockState();
            else if (n < 0.62) top = h < 2 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
            else top = h < 3 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        }
        set(level, new BlockPos(x, y, z), top);
        // the slab's body, thicker toward the middle, hanging in rough roots below
        int depth = 2 + (int) ((1 - r / RADIUS) * 5) + (int) (noise(x * 0.25, z * 0.25, 13) * 6 * (1 - r / RADIUS));
        for (int d = 1; d <= depth; d++) set(level, new BlockPos(x, y - d, z), stone(h + d));
    }

    private static void lantern(ServerLevel level, BlockPos at) {
        set(level, at, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
        set(level, at.above(), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
        set(level, at.above(2), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
        set(level, at.above(3), Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
    }

    /** A torii facing along z (or x when {@code alongX}), 5 wide: two pillars, a tie beam, the curved top; fallen, one
     * pillar and the top lie broken on the ground. */
    private static void torii(ServerLevel level, BlockPos at, boolean alongX, boolean fallen) {
        Direction side = alongX ? Direction.SOUTH : Direction.EAST;
        BlockState post = Blocks.STRIPPED_CRIMSON_STEM.defaultBlockState();
        BlockState beam = Blocks.RED_NETHER_BRICKS.defaultBlockState();
        BlockState cap = Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockPos left = at.relative(side, -2), right = at.relative(side, 2);
        int h = 6;
        for (int i = 0; i < (fallen ? 2 : h); i++) set(level, left.above(i), post);
        for (int i = 0; i < h; i++) set(level, right.above(i), post);
        if (fallen) {
            // the left pillar lies across the ground, the top beside it
            BlockState lying = post.setValue(RotatedPillarBlock.AXIS, alongX ? Direction.Axis.X : Direction.Axis.Z);
            for (int i = 1; i <= 4; i++) set(level, left.relative(alongX ? Direction.WEST : Direction.NORTH, i), lying);
            for (int i = -1; i <= 3; i++) set(level, at.relative(alongX ? Direction.EAST : Direction.SOUTH, 2).relative(side, i), beam);
            set(level, right.above(h), cap);
            return;
        }
        for (int i = -2; i <= 2; i++) set(level, at.relative(side, i).above(h - 2), beam);           // the tie beam (nuki)
        for (int i = -3; i <= 3; i++) set(level, at.relative(side, i).above(h), beam);               // the top (kasagi)
        set(level, at.relative(side, -3).above(h + 1), cap);
        set(level, at.relative(side, 3).above(h + 1), cap);
        set(level, at.above(h - 1), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
    }

    private static void set(ServerLevel level, BlockPos p, BlockState s) {
        level.setBlock(p, s, 2);
    }

    private RenegadeArena() {}
}
