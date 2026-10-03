package com.soulless.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Wailing Reaper's arena, as an ordered list of block placements around the altar (deterministic, so a
 * half-built arena can resume after a reload):
 * <ul>
 *   <li>the space is cleared, ring by ring outward;</li>
 *   <li>a patterned blackstone floor spreads outward from the altar platform: eight glowing crying-obsidian
 *       spokes, two rune rings, soul soil creeping in near the edge, a foundation filled in underneath;</li>
 *   <li>then everything else rises together course by course: a crenellated ring wall with barred slits and
 *       soul braziers, eight colossal obsidian pillars (two of them broken, rubble at their feet) crowned with
 *       soul campfires and hung with chained soul lanterns, and four giant bone arches that vault over the
 *       whole arena and meet in a glowing crown with a chandelier of lanterns.</li>
 * </ul>
 */
public final class ArenaPlan {
    public static final int RADIUS = 24;
    private static final int CLEAR_HEIGHT = 27;
    private static final int WALL_HEIGHT = 8;
    private static final int APEX = 25;
    private static final double PILLAR_RING = RADIUS - 3.5;

    /** One placement. {@code soft}: only fill air/replaceable blocks (the foundation under the floor). */
    public record Op(BlockPos pos, BlockState state, boolean soft) {}

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState PB = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
    private static final BlockState PBB = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    private static final BlockState CRACKED = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    private static final BlockState CHISELED = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
    private static final BlockState BLACKSTONE = Blocks.BLACKSTONE.defaultBlockState();
    private static final BlockState OBSIDIAN = Blocks.OBSIDIAN.defaultBlockState();
    private static final BlockState CRYING = Blocks.CRYING_OBSIDIAN.defaultBlockState();
    private static final BlockState SOUL_SOIL = Blocks.SOUL_SOIL.defaultBlockState();
    private static final BlockState BARS = Blocks.IRON_BARS.defaultBlockState();
    private static final BlockState CHAIN = Blocks.CHAIN.defaultBlockState();
    private static final BlockState LANTERN = Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
    private static final BlockState CAMPFIRE = Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true);

    private ArenaPlan() {}

    public static List<Op> make(BlockPos altar) {
        List<Op> clear = new ArrayList<>(), floor = new ArrayList<>(), rise = new ArrayList<>();
        int cx = altar.getX(), cz = altar.getZ();
        int f = altar.getY() - 2;   // the floor is level with the altar platform
        int outer = RADIUS + 3;

        // --- clearing and floor, ring by ring outward from the platform
        for (int r = 0; r <= outer; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > outer + 0.5 || d <= SoulAltarBlockEntity.PLATFORM_RADIUS + 0.5) continue;
                    for (int y = 1; y <= CLEAR_HEIGHT; y++) clear.add(new Op(new BlockPos(cx + dx, f + y, cz + dz), AIR, false));
                    for (int y = 1; y <= 3; y++) floor.add(new Op(new BlockPos(cx + dx, f - y, cz + dz), BLACKSTONE, true));
                    floor.add(new Op(new BlockPos(cx + dx, f, cz + dz), floorBlock(cx + dx, cz + dz, dx, dz, d), false));
                }
            }
        }

        // --- the ring wall: 2 thick, crenellated, barred slits, soul braziers on the merlons
        for (int dx = -outer; dx <= outer; dx++) {
            for (int dz = -outer; dz <= outer; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= RADIUS + 0.5 || d > RADIUS + 2.5) continue;
                boolean inner = d <= RADIUS + 1.5;
                int arc = (int) Math.round(Math.atan2(dz, dx) * (RADIUS + 1.5));
                for (int y = 1; y <= WALL_HEIGHT; y++) {
                    BlockState s = y == 1 ? PB : y == WALL_HEIGHT ? CHISELED : (hash(cx + dx, f + y, cz + dz) % 7 == 0 ? CRACKED : PBB);
                    if (inner && (y == 4 || y == 5) && Math.floorMod(arc, 7) == 0) s = BARS;
                    if (inner && y == 6 && Math.floorMod(arc, 14) == 3) s = CRYING;
                    rise.add(new Op(new BlockPos(cx + dx, f + y, cz + dz), s, false));
                }
                if (Math.floorMod(arc, 2) == 0) {
                    rise.add(new Op(new BlockPos(cx + dx, f + WALL_HEIGHT + 1, cz + dz), PBB, false));   // merlon
                    if (inner && Math.floorMod(arc, 10) == 0) rise.add(new Op(new BlockPos(cx + dx, f + WALL_HEIGHT + 2, cz + dz), CAMPFIRE, false));
                }
            }
        }

        // --- eight colossal pillars between the spokes
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(22.5 + 45 * k);
            int px = cx + (int) Math.round(Math.cos(a) * PILLAR_RING), pz = cz + (int) Math.round(Math.sin(a) * PILLAR_RING);
            boolean broken = k == 1 || k == 6;
            pillar(rise, px, f, pz, broken ? 9 : 17, broken);
        }

        // --- four bone arches vaulting over the arena, meeting in a crown
        for (int k = 0; k < 4; k++) {
            double a = Math.toRadians(45 * k);
            double ux = Math.cos(a), uz = Math.sin(a);
            int steps = (RADIUS + 2) * 8;
            for (int i = -steps; i <= steps; i++) {
                double t = i / (double) steps;
                double h = WALL_HEIGHT + (APEX - WALL_HEIGHT) * Math.sqrt(Math.max(0, 1 - t * t));
                double along = t * (RADIUS + 1.5);
                int bx = cx + (int) Math.round(ux * along), bz = cz + (int) Math.round(uz * along);
                int by = f + (int) Math.round(h);
                // a 2-thick rib; the steep ends stand upright, the vault lies along the arch
                Direction.Axis axis = Math.abs(t) > 0.75 ? Direction.Axis.Y : (Math.abs(ux) > Math.abs(uz) ? Direction.Axis.X : Direction.Axis.Z);
                BlockState bone = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
                rise.add(new Op(new BlockPos(bx, by, bz), bone, false));
                rise.add(new Op(new BlockPos(bx, by + 1, bz), bone, false));
                // vertebra spurs every few blocks along the vault
                if (Math.abs(t) < 0.8 && i % 12 == 0) {
                    rise.add(new Op(new BlockPos(bx, by + 2, bz), bone.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y), false));
                }
            }
        }
        int top = f + APEX + 2;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                rise.add(new Op(new BlockPos(cx + dx, top, cz + dz), (Math.abs(dx) + Math.abs(dz)) % 2 == 0 ? CRYING : OBSIDIAN, false));
            }
        }
        rise.add(new Op(new BlockPos(cx, top + 1, cz), CAMPFIRE, false));
        // the chandelier: a long chain from the crown, lanterns hanging around its end
        for (int y = top - 1; y >= f + APEX - 6; y--) rise.add(new Op(new BlockPos(cx, y, cz), CHAIN, false));
        rise.add(new Op(new BlockPos(cx, f + APEX - 7, cz), LANTERN, false));
        for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (int y = top - 1; y >= f + APEX - 3; y--) rise.add(new Op(new BlockPos(cx + o[0] * 2, y, cz + o[1] * 2), CHAIN, false));
            rise.add(new Op(new BlockPos(cx + o[0] * 2, f + APEX - 4, cz + o[1] * 2), LANTERN, false));
        }

        // everything above the floor rises together, course by course
        rise.sort(Comparator.comparingInt(op -> op.pos().getY()));
        List<Op> all = new ArrayList<>(clear.size() + floor.size() + rise.size());
        all.addAll(clear);
        all.addAll(floor);
        all.addAll(rise);
        return all;
    }

    private static BlockState floorBlock(int x, int z, int dx, int dz, double d) {
        if (d <= 6.5) return d > 5.5 ? CHISELED : PB;                       // a rim around the platform
        if (d > RADIUS - 1.5) return d > RADIUS + 0.5 ? BLACKSTONE : PB;     // the border, and under the wall
        double ang = Math.atan2(dz, dx);
        double sector = ang - Math.round(ang / (Math.PI / 4)) * (Math.PI / 4);
        if (Math.abs(Math.sin(sector)) * d < 0.6 && d < RADIUS - 2.5) return CRYING;   // eight glowing spokes
        if (d > 12.5 && d <= 13.5) return Math.floorMod((int) Math.round(ang * 13), 3) == 0 ? CRYING : CHISELED;
        if (d > 18.5 && d <= 19.5) return CHISELED;
        int h = hash(x, 0, z) % 100;
        double edge = (d - 7) / (RADIUS - 7);
        if (h < 6 + edge * 14) return SOUL_SOIL;
        if (h < 30) return CRACKED;
        if (h < 38) return BLACKSTONE;
        return PBB;
    }

    private static void pillar(List<Op> ops, int px, int f, int pz, int height, boolean broken) {
        for (int y = 1; y <= height; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean corner = dx != 0 && dz != 0;
                    BlockState s;
                    if (y <= 2) s = y == 2 && corner ? CHISELED : PBB;
                    else s = y % 5 == 0 ? CRYING : OBSIDIAN;
                    if (broken && y >= height - 2 && hash(px + dx, y, pz + dz) % 3 == 0) continue;   // jagged break
                    if (broken && y == height) s = CRACKED;
                    ops.add(new Op(new BlockPos(px + dx, f + y, pz + dz), s, false));
                }
            }
            if (y == 1) {   // a wider plinth
                for (int[] o : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) ops.add(new Op(new BlockPos(px + o[0], f + 1, pz + o[1]), PB, false));
            }
        }
        if (broken) {
            // rubble scattered at its foot
            for (int i = 0; i < 6; i++) {
                int h = hash(px, i, pz);
                int rx = px + (h % 7) - 3, rz = pz + ((h / 7) % 7) - 3;
                if (Math.abs(rx - px) <= 1 && Math.abs(rz - pz) <= 1) continue;
                ops.add(new Op(new BlockPos(rx, f + 1, rz), i % 2 == 0 ? CRACKED : OBSIDIAN, false));
            }
            return;
        }
        // capital, crowned with soul fire, lanterns hanging from chains below its rim
        int cap = f + height + 1;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                ops.add(new Op(new BlockPos(px + dx, cap, pz + dz), Math.abs(dx) == 2 || Math.abs(dz) == 2 ? PB : PBB, false));
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) ops.add(new Op(new BlockPos(px + dx, cap + 1, pz + dz), dx == 0 && dz == 0 ? CRYING : CHISELED, false));
        }
        ops.add(new Op(new BlockPos(px, cap + 2, pz), CAMPFIRE, false));
        for (int[] o : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
            for (int y = cap - 1; y >= cap - 3; y--) ops.add(new Op(new BlockPos(px + o[0], y, pz + o[1]), CHAIN, false));
            ops.add(new Op(new BlockPos(px + o[0], cap - 4, pz + o[1]), LANTERN, false));
        }
    }

    private static int hash(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h & 0x7fffffff;
    }
}
