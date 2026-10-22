package com.soulless.world;

import com.mojang.serialization.Codec;
import com.soulless.Soulless;
import com.soulless.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Drowned Souls: a sunken dungeon on the floor of a deep ocean, very rare and flooded through. A gate opens on a grid
 * of halls joined by corridors - a maze with a few loops, every hall of some kind (pillared hall, crypt, shrine,
 * library, prison, a caved-in one) - and at the far end a great round arena where a boss will wait. Its layout comes
 * from the structure's position, so every chunk builds its share of the same plan.
 */
public class DrownedSoulsStructure extends Structure {
    public static final Codec<DrownedSoulsStructure> CODEC = simpleCodec(DrownedSoulsStructure::new);
    /** The grid: N x N cells of CELL blocks; a hall fills the middle of each (inner half-size HALL, inner height ROOM_H). */
    private static final int N = 5, CELL = 18, HALL = 6, ROOM_H = 6, CORR = 1, CORR_H = 4;
    /** The arena: across the far row's three middle cells. */
    public static final int ARENA_R = 15, ARENA_H = 14;
    private static final int SPAN = N * CELL;
    public static final String LOOT = "chests/drowned_souls";

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
        // deep enough that even the arena's dome stays under water
        if (surface - floor < ARENA_H + 6 || surface < gen.getSeaLevel() - 1) return Optional.empty();
        BlockPos base = new BlockPos(x - SPAN / 2, floor, z - SPAN / 2);
        return Optional.of(new GenerationStub(base, b -> b.addPiece(new Piece(base))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.DROWNED_SOULS.get();
    }

    public static class Piece extends StructurePiece {
        private final BlockPos base;

        public Piece(BlockPos base) {
            super(ModStructures.DROWNED_SOULS_PIECE.get(), 0, new BoundingBox(base.getX() - 12, base.getY() - 12, base.getZ() - 12,
                    base.getX() + SPAN + 12, base.getY() + ARENA_H + 4, base.getZ() + SPAN + 12));
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
            new Builder(level, base, box).build();
        }
    }

    // ------------------------------------------------------------------ the plan

    private enum Kind { HALL, CRYPT, SHRINE, LIBRARY, PRISON, COLLAPSED }

    /** Cells taken by the arena (the far row's middle three) and the gate's cell. */
    private static boolean arenaCell(int cx, int cz) {
        return cz == N - 1 && cx >= 1 && cx <= 3;
    }

    private static final int GATE_X = N / 2, GATE_Z = 0;

    /** Builds the dungeon whose grid's corner is {@code base}, writing only inside {@code clip}. */
    public static void build(WorldGenLevel level, BlockPos base, BoundingBox clip) {
        new Builder(level, base, clip).build();
    }

    private static final class Builder {
        final WorldGenLevel level;
        final BlockPos base;
        final BoundingBox clip;
        final long seed;
        /** Doors: links between cells (right: x+1, down: z+1). */
        final boolean[][] right = new boolean[N][N], down = new boolean[N][N];

        final BlockState water = Blocks.WATER.defaultBlockState();
        final BlockState wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState(), wallCracked = Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
        final BlockState tiles = Blocks.DEEPSLATE_TILES.defaultBlockState(), tilesCracked = Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
        final BlockState floor = Blocks.DARK_PRISMARINE.defaultBlockState(), trim = Blocks.PRISMARINE_BRICKS.defaultBlockState();
        final BlockState polished = Blocks.POLISHED_DEEPSLATE.defaultBlockState();

        Builder(WorldGenLevel level, BlockPos base, BoundingBox clip) {
            this.level = level;
            this.base = base;
            this.clip = clip;
            this.seed = base.asLong() * 0x5DEECE66DL;
            plan();
        }

        int h(int a, int b) {
            long x = seed + a * 73856093L ^ b * 19349663L;
            x ^= x >>> 31;
            x *= 0x9E3779B97F4A7C15L;
            x ^= x >>> 29;
            return (int) ((x >>> 33) & 0x7FFFFFFF);
        }

        /** A maze over the cells (depth first from the gate), a few extra doors for loops, the arena joined to the row before it. */
        void plan() {
            boolean[][] seen = new boolean[N][N];
            List<int[]> stack = new ArrayList<>();
            stack.add(new int[]{GATE_X, GATE_Z});
            seen[GATE_X][GATE_Z] = true;
            int step = 0;
            while (!stack.isEmpty()) {
                int[] c = stack.get(stack.size() - 1);
                List<int[]> next = new ArrayList<>();
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = c[0] + d[0], nz = c[1] + d[1];
                    if (nx < 0 || nz < 0 || nx >= N || nz >= N || seen[nx][nz] || arenaCell(nx, nz)) continue;
                    next.add(new int[]{nx, nz});
                }
                if (next.isEmpty()) {
                    stack.remove(stack.size() - 1);
                    continue;
                }
                int[] n = next.get(h(step++, 7) % next.size());
                link(c[0], c[1], n[0], n[1]);
                seen[n[0]][n[1]] = true;
                stack.add(n);
            }
            for (int i = 0; i < 4; i++) {   // loops
                int cx = h(i, 11) % (N - 1), cz = h(i, 13) % (N - 1);
                if (h(i, 17) % 2 == 0) {
                    if (!arenaCell(cx + 1, cz) && !arenaCell(cx, cz)) right[cx][cz] = true;
                } else if (!arenaCell(cx, cz + 1) && !arenaCell(cx, cz)) {
                    down[cx][cz] = true;
                }
            }
        }

        void link(int ax, int az, int bx, int bz) {
            if (ax == bx) down[ax][Math.min(az, bz)] = true;
            else right[Math.min(ax, bx)][az] = true;
        }

        int cx(int cell) {
            return cell * CELL + CELL / 2;
        }

        Kind kind(int cx, int cz) {
            if (cx == GATE_X && cz == GATE_Z) return Kind.HALL;
            Kind[] all = Kind.values();
            return all[h(cx * 31 + cz, 23) % all.length];
        }

        // ---------------------------------------------------------- building

        void build() {
            // shells first, then hollows (so corridors cut doors through the walls), then what stands inside
            for (int x = 0; x < N; x++) {
                for (int z = 0; z < N; z++) {
                    if (arenaCell(x, z)) continue;
                    shell(cx(x) - HALL - 1, 0, cx(z) - HALL - 1, cx(x) + HALL + 1, ROOM_H + 1, cz(z) + HALL + 1);
                    if (x < N - 1 && right[x][z]) shell(cx(x) + HALL, 0, cz(z) - CORR - 1, cx(x + 1) - HALL, CORR_H + 1, cz(z) + CORR + 1);
                    if (z < N - 1 && down[x][z] && !arenaCell(x, z + 1)) shell(cx(x) - CORR - 1, 0, cz(z) + HALL, cx(x) + CORR + 1, CORR_H + 1, cz(z + 1) - HALL);
                }
            }
            // the gate: a corridor out of the first hall to the open sea, framed by a tall arch
            int gx = cx(GATE_X);
            shell(gx - CORR - 1, 0, -6, gx + CORR + 1, CORR_H + 1, cz(GATE_Z) - HALL);
            // the passage from the last row into the arena
            int ax = cx(2), az = cz(N - 1);
            shell(ax - CORR - 1, 0, cz(N - 2) + HALL, ax + CORR + 1, CORR_H + 1, az - ARENA_R + 1);
            arenaShell(ax, az);

            for (int x = 0; x < N; x++) {
                for (int z = 0; z < N; z++) {
                    if (arenaCell(x, z)) continue;
                    hollow(cx(x) - HALL, 1, cz(z) - HALL, cx(x) + HALL, ROOM_H, cz(z) + HALL);
                    if (x < N - 1 && right[x][z]) hollow(cx(x) + HALL, 1, cz(z) - CORR, cx(x + 1) - HALL, CORR_H, cz(z) + CORR);
                    if (z < N - 1 && down[x][z] && !arenaCell(x, z + 1)) hollow(cx(x) - CORR, 1, cz(z) + HALL, cx(x) + CORR, CORR_H, cz(z + 1) - HALL);
                }
            }
            hollow(gx - CORR, 1, -7, gx + CORR, CORR_H, cz(GATE_Z) - HALL);
            hollow(ax - CORR, 1, cz(N - 2) + HALL, ax + CORR, CORR_H, az - ARENA_R + 2);
            arenaHollow(ax, az);
            gate(gx);

            for (int x = 0; x < N; x++) {
                for (int z = 0; z < N; z++) {
                    if (!arenaCell(x, z)) furnish(x, z);
                }
            }
            arenaFurnish(ax, az);
        }

        int cz(int cell) {
            return cx(cell);
        }

        BlockPos at(int x, int y, int z) {
            return base.offset(x, y, z);
        }

        void put(int x, int y, int z, BlockState s) {
            BlockPos p = at(x, y, z);
            if (clip.isInside(p)) level.setBlock(p, s, 2);
        }

        BlockState wallAt(int x, int y, int z) {
            int k = h(x * 7 + y, z * 13 + y);
            return k % 9 == 0 ? wallCracked : k % 23 == 0 ? trim : wall;
        }

        /** A box of walls with a patterned floor, a tiled ceiling, and footings down to the sea floor. */
        void shell(int x0, int y0, int z0, int x1, int y1, int z1) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    for (int y = y0; y <= y1; y++) {
                        BlockState s = y == y0 ? (((x + z) & 3) == 0 ? trim : floor) : y == y1 ? (h(x, z) % 7 == 0 ? tilesCracked : tiles) : wallAt(x, y, z);
                        put(x, y, z, s);
                    }
                    footing(x, y0 - 1, z);
                }
            }
        }

        void footing(int x, int y, int z) {
            for (int d = 0; d < 12; d++) {
                BlockPos p = at(x, y - d, z);
                if (!clip.isInside(p)) return;
                BlockState here = level.getBlockState(p);
                if (here.isSolid() && !here.is(Blocks.WATER)) return;
                level.setBlock(p, h(x, z + d) % 3 == 0 ? tilesCracked : tiles, 2);
            }
        }

        void hollow(int x0, int y0, int z0, int x1, int y1, int z1) {
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = y0; y <= y1; y++) put(x, y, z, water);
        }

        void arenaShell(int ox, int oz) {
            for (int dx = -ARENA_R - 1; dx <= ARENA_R + 1; dx++) {
                for (int dz = -ARENA_R - 1; dz <= ARENA_R + 1; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > ARENA_R + 1.3) continue;
                    // a dome: the ceiling rises toward the middle
                    int top = ARENA_H - (int) Math.round(Math.max(0, d - 6) * 0.35);
                    for (int y = 0; y <= top; y++) {
                        BlockState s = y == 0 ? floor : y == top ? (h(dx, dz) % 6 == 0 ? tilesCracked : tiles) : wallAt(ox + dx, y, oz + dz);
                        put(ox + dx, y, oz + dz, s);
                    }
                    footing(ox + dx, -1, oz + dz);
                }
            }
        }

        void arenaHollow(int ox, int oz) {
            for (int dx = -ARENA_R; dx <= ARENA_R; dx++) {
                for (int dz = -ARENA_R; dz <= ARENA_R; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > ARENA_R + 0.3) continue;
                    int top = ARENA_H - (int) Math.round(Math.max(0, d - 6) * 0.35) - 1;
                    for (int y = 1; y <= top; y++) put(ox + dx, y, oz + dz, water);
                    // the floor: rings of prismarine, a seal in the middle
                    BlockState f;
                    if (d < 2.6) f = d < 1.2 ? Blocks.CHISELED_DEEPSLATE.defaultBlockState() : polished;
                    else if (Math.abs(d - 7) < 0.6 || Math.abs(d - 12) < 0.6) f = trim;
                    else f = h(dx, dz) % 11 == 0 ? Blocks.PRISMARINE.defaultBlockState() : h(dx, dz) % 7 == 0 ? tilesCracked : floor;
                    put(ox + dx, 0, oz + dz, f);
                }
            }
        }

        void arenaFurnish(int ox, int oz) {
            // eight pillars round the middle, lanterns on the standing ones
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2 * i / 8 + 0.2;
                int px = ox + Mth.floor(Math.cos(a) * 10), pz = oz + Mth.floor(Math.sin(a) * 10);
                boolean standing = h(i, 99) % 4 != 0;
                int top = standing ? ARENA_H - 3 : 2 + h(i, 98) % 3;
                for (int y = 1; y <= top; y++) {
                    for (int[] o : new int[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}}) {
                        put(px + o[0], y, pz + o[1], y == 1 || y == top ? polished : wallAt(px + o[0], y, pz + o[1]));
                    }
                }
                if (standing) put(px, top + 1, pz, Blocks.SEA_LANTERN.defaultBlockState());
                else for (int k = 1; k <= 3; k++) put(px + k + 1, 1, pz, wallCracked);
            }
            // soul lanterns hung round the walls
            for (int i = 0; i < 16; i++) {
                double a = Math.PI * 2 * i / 16;
                int lx = ox + Mth.floor(Math.cos(a) * (ARENA_R - 1)), lz = oz + Mth.floor(Math.sin(a) * (ARENA_R - 1));
                put(lx, 5, lz, lantern());
            }
            put(ox, 1, oz, Blocks.SEA_PICKLE.defaultBlockState().setValue(BlockStateProperties.PICKLES, 4));
        }

        BlockState lantern() {
            return Blocks.SOUL_LANTERN.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
        }

        void gate(int gx) {
            // a tall arch around the opening, standing on the sea floor
            for (int y = 0; y <= CORR_H + 5; y++) {
                for (int dx = -CORR - 3; dx <= CORR + 3; dx++) {
                    boolean opening = Math.abs(dx) <= CORR && y >= 1 && y <= CORR_H;
                    boolean frame = Math.abs(dx) >= CORR + 2 || y >= CORR_H + 2;
                    if (opening) put(gx + dx, y, -7, water);
                    else if (frame) put(gx + dx, y, -7, y == CORR_H + 5 || Math.abs(dx) == CORR + 3 ? polished : wallAt(gx + dx, y, -7));
                }
            }
            put(gx - CORR - 2, CORR_H + 1, -8, lantern());
            put(gx + CORR + 2, CORR_H + 1, -8, lantern());
            put(gx, CORR_H + 4, -8, Blocks.CHISELED_DEEPSLATE.defaultBlockState());
        }

        // ---------------------------------------------------------- what stands in the halls

        void furnish(int cellX, int cellZ) {
            int ox = cx(cellX), oz = cz(cellZ);
            Kind k = kind(cellX, cellZ);
            int r = h(cellX, cellZ * 3 + 1);
            // light: a sea lantern set in the ceiling
            put(ox, ROOM_H + 1, oz, Blocks.SEA_LANTERN.defaultBlockState());
            switch (k) {
                case HALL -> {
                    for (int[] c : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
                        for (int y = 1; y <= ROOM_H; y++) put(ox + c[0], y, oz + c[1], y == 1 || y == ROOM_H ? polished : wallAt(ox + c[0], y, oz + c[1]));
                    }
                    put(ox, 1, oz, Blocks.SEA_PICKLE.defaultBlockState().setValue(BlockStateProperties.PICKLES, 1 + r % 4));
                }
                case CRYPT -> {
                    // stone coffins along both sides, one of them broken open
                    for (int i = -4; i <= 4; i += 4) {
                        for (int side : new int[]{-4, 4}) {
                            for (int l = -1; l <= 1; l++) put(ox + side, 1, oz + i + l, polished);
                            put(ox + side, 2, oz + i, (r + i) % 3 == 0 ? Blocks.SOUL_SAND.defaultBlockState() : Blocks.POLISHED_DEEPSLATE_SLAB.defaultBlockState()
                                    .setValue(BlockStateProperties.WATERLOGGED, true));
                        }
                    }
                    chest(ox, 1, oz + HALL - 1, Direction.NORTH);
                    put(ox - 1, 1, oz - HALL + 1, lantern());
                }
                case SHRINE -> {
                    // an altar at the back with lanterns, kneeling stones before it
                    for (int dx = -2; dx <= 2; dx++) put(ox + dx, 1, oz + HALL - 1, polished);
                    put(ox, 2, oz + HALL - 1, Blocks.CHISELED_DEEPSLATE.defaultBlockState());
                    put(ox - 2, 2, oz + HALL - 1, lantern());
                    put(ox + 2, 2, oz + HALL - 1, lantern());
                    for (int dx = -2; dx <= 2; dx += 2) put(ox + dx, 1, oz, Blocks.POLISHED_DEEPSLATE_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
                    if (r % 2 == 0) chest(ox + 1, 2, oz + HALL - 2, Direction.NORTH);
                }
                case LIBRARY -> {
                    // shelves along the walls, some fallen
                    for (int i = -HALL + 1; i <= HALL - 1; i++) {
                        if (Math.abs(i) <= CORR + 1) continue;   // keep the doorways clear
                        for (int y = 1; y <= 3; y++) {
                            if (h(i, y + r) % 5 == 0) continue;
                            put(ox - HALL, y, oz + i, Blocks.BOOKSHELF.defaultBlockState());
                            put(ox + HALL, y, oz + i, Blocks.BOOKSHELF.defaultBlockState());
                        }
                    }
                    put(ox, 1, oz, Blocks.LECTERN.defaultBlockState());
                    if (r % 3 == 0) chest(ox - 2, 1, oz + 2, Direction.EAST);
                }
                case PRISON -> {
                    // barred cells down one side, a chain hanging in each
                    for (int c = -1; c <= 1; c++) {
                        int cz0 = oz + c * 4;
                        for (int y = 1; y <= ROOM_H - 1; y++) {
                            for (int l = -1; l <= 1; l++) put(ox + 2, y, cz0 + l, Blocks.IRON_BARS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
                            put(ox + 2, y, cz0 + 2, wall);
                        }
                        put(ox + 4, ROOM_H, cz0, Blocks.CHAIN.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
                        put(ox + 4, ROOM_H - 1, cz0, Blocks.CHAIN.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
                    }
                    put(ox + 4, 1, oz + 4, Blocks.SKELETON_SKULL.defaultBlockState());
                    if (r % 2 == 1) chest(ox + 4, 1, oz - 4, Direction.WEST);
                }
                case COLLAPSED -> {
                    // the ceiling has come down in the middle: rubble on the floor, a hole up to the sea
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            if (dx * dx + dz * dz > 5) continue;
                            put(ox + dx, ROOM_H + 1, oz + dz, water);
                            int pile = 1 + (h(dx, dz + r) % 3);
                            for (int y = 1; y <= pile; y++) put(ox + dx, y, oz + dz, h(dx + y, dz) % 2 == 0 ? wallCracked : tilesCracked);
                        }
                    }
                    put(ox + 3, 1, oz - 3, Blocks.SEA_PICKLE.defaultBlockState().setValue(BlockStateProperties.PICKLES, 3));
                }
            }
        }

        void chest(int x, int y, int z, Direction facing) {
            BlockPos p = at(x, y, z);
            if (!clip.isInside(p)) return;
            level.setBlock(p, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing).setValue(ChestBlock.WATERLOGGED, true), 2);
            RandomizableContainerBlockEntity.setLootTable(level, RandomSource.create(h(x, z)), p, Soulless.id(LOOT));
        }
    }
}
