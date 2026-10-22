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
 * The City of Drowned Souls, under the floor of a deep ocean and flooded through - built to be swum, not walked:
 * <ul>
 *   <li>a ring of ruins on the sea floor round the mouth of a vast shaft that goes straight down into the rock;</li>
 *   <li>at its foot the catacombs - a 5 x 5 maze of halls (pillared, crypt, shrine, library, prison, caved-in) round
 *       a central hub, with loot;</li>
 *   <li>under the hub the shaft goes on, a well through the floor, into the arena: a huge drowned cylinder with a
 *       domed roof and a bowl for a floor - room above, below and all round for the Leviathan of the Abyss.</li>
 * </ul>
 * Every chunk builds its share of the same plan (it comes from the structure's position).
 */
public class DrownedSoulsStructure extends Structure {
    public static final Codec<DrownedSoulsStructure> CODEC = simpleCodec(DrownedSoulsStructure::new);
    /** The shaft: inner radius, how far it drops from the sea floor to the catacombs' ceiling. */
    private static final int SHAFT_R = 9, SHAFT_DEPTH = 24;
    /** The catacombs: N x N cells of CELL blocks, halls of inner half-size HALL and inner height ROOM_H. */
    private static final int N = 5, CELL = 18, HALL = 6, ROOM_H = 6, CORR = 1, CORR_H = 4;
    /** The arena: radius and height (its top just under the catacombs' floor). */
    public static final int ARENA_R = 44, ARENA_H = 54;
    private static final int SPAN = N * CELL, HALF = SPAN / 2;
    /** Depth below the sea floor of the catacombs' floor and of the arena's lowest point. */
    private static final int CATA = SHAFT_DEPTH + ROOM_H + 2, BOTTOM = CATA + 3 + ARENA_H;
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
        // a deep ocean over it, and rock enough under it for the whole city above the bedrock
        if (surface - floor < 12 || surface < gen.getSeaLevel() - 1) return Optional.empty();
        if (floor - BOTTOM < ctx.heightAccessor().getMinBuildHeight() + 6) return Optional.empty();
        BlockPos centre = new BlockPos(x, floor, z);
        return Optional.of(new GenerationStub(centre, b -> b.addPiece(new Piece(centre))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.DROWNED_SOULS.get();
    }

    public static class Piece extends StructurePiece {
        private final BlockPos centre;

        public Piece(BlockPos centre) {
            super(ModStructures.DROWNED_SOULS_PIECE.get(), 0, new BoundingBox(centre.getX() - ARENA_R - 3, centre.getY() - BOTTOM - 4,
                    centre.getZ() - ARENA_R - 3, centre.getX() + ARENA_R + 3, centre.getY() + 12, centre.getZ() + ARENA_R + 3));
            this.centre = centre;
        }

        public Piece(StructurePieceSerializationContext ctx, CompoundTag tag) {
            super(ModStructures.DROWNED_SOULS_PIECE.get(), tag);
            this.centre = NbtUtils.readBlockPos(tag.getCompound("Centre"));
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
            tag.put("Centre", NbtUtils.writeBlockPos(centre));
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator gen, RandomSource random, BoundingBox box,
                                ChunkPos chunk, BlockPos pivot) {
            new Builder(level, centre, box).build();
        }
    }

    /** Builds the city round {@code centre} (the shaft's mouth on the sea floor), writing only inside {@code clip}. */
    public static void build(WorldGenLevel level, BlockPos centre, BoundingBox clip) {
        new Builder(level, centre, clip).build();
    }

    private enum Kind { HALL, CRYPT, SHRINE, LIBRARY, PRISON, COLLAPSED }

    private static final int HUB = N / 2;

    private static final class Builder {
        final WorldGenLevel level;
        final BlockPos c;
        final BoundingBox clip;
        final long seed;
        final boolean[][] right = new boolean[N][N], down = new boolean[N][N];
        /** Heights relative to the sea floor at the centre. */
        final int cataY = -CATA, arenaTop = -CATA - 3, arenaY0 = -BOTTOM;

        final BlockState water = Blocks.WATER.defaultBlockState();
        final BlockState wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState(), wallCracked = Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
        final BlockState tiles = Blocks.DEEPSLATE_TILES.defaultBlockState(), tilesCracked = Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
        final BlockState floor = Blocks.DARK_PRISMARINE.defaultBlockState(), trim = Blocks.PRISMARINE_BRICKS.defaultBlockState();
        final BlockState polished = Blocks.POLISHED_DEEPSLATE.defaultBlockState();

        Builder(WorldGenLevel level, BlockPos centre, BoundingBox clip) {
            this.level = level;
            this.c = centre;
            this.clip = clip;
            this.seed = centre.asLong() * 0x5DEECE66DL;
            plan();
        }

        int h(int a, int b) {
            long x = seed + a * 73856093L ^ b * 19349663L;
            x ^= x >>> 31;
            x *= 0x9E3779B97F4A7C15L;
            x ^= x >>> 29;
            return (int) ((x >>> 33) & 0x7FFFFFFF);
        }

        /** A maze over the cells, depth first from the hub, with a few extra doors for loops. */
        void plan() {
            boolean[][] seen = new boolean[N][N];
            List<int[]> stack = new ArrayList<>();
            stack.add(new int[]{HUB, HUB});
            seen[HUB][HUB] = true;
            int step = 0;
            while (!stack.isEmpty()) {
                int[] p = stack.get(stack.size() - 1);
                List<int[]> next = new ArrayList<>();
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = p[0] + d[0], nz = p[1] + d[1];
                    if (nx < 0 || nz < 0 || nx >= N || nz >= N || seen[nx][nz]) continue;
                    next.add(new int[]{nx, nz});
                }
                if (next.isEmpty()) {
                    stack.remove(stack.size() - 1);
                    continue;
                }
                int[] n = next.get(h(step++, 7) % next.size());
                if (p[0] == n[0]) down[p[0]][Math.min(p[1], n[1])] = true;
                else right[Math.min(p[0], n[0])][p[1]] = true;
                seen[n[0]][n[1]] = true;
                stack.add(n);
            }
            for (int i = 0; i < 5; i++) {
                int cx = h(i, 11) % (N - 1), cz = h(i, 13) % (N - 1);
                if (h(i, 17) % 2 == 0) right[cx][cz] = true;
                else down[cx][cz] = true;
            }
        }

        /** A cell's centre, relative to the shaft (x or z alike). */
        int cell(int i) {
            return i * CELL + CELL / 2 - HALF;
        }

        Kind kind(int cx, int cz) {
            Kind[] all = Kind.values();
            return all[h(cx * 31 + cz, 23) % all.length];
        }

        // ---------------------------------------------------------- primitives (x, y, z relative to the centre)

        void put(int x, int y, int z, BlockState s) {
            BlockPos p = c.offset(x, y, z);
            if (clip.isInside(p)) level.setBlock(p, s, 2);
        }

        BlockState wallAt(int x, int y, int z) {
            int k = h(x * 7 + y, z * 13 + y);
            return k % 9 == 0 ? wallCracked : k % 23 == 0 ? trim : wall;
        }

        void box(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = y0; y <= y1; y++) put(x, y, z, s);
        }

        /** Walls all round with a patterned floor and a tiled ceiling. */
        void shell(int x0, int y0, int z0, int x1, int y1, int z1) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    for (int y = y0; y <= y1; y++) {
                        put(x, y, z, y == y0 ? (((x + z) & 3) == 0 ? trim : floor) : y == y1 ? (h(x, z) % 7 == 0 ? tilesCracked : tiles) : wallAt(x, y, z));
                    }
                }
            }
        }

        BlockState lantern() {
            return Blocks.SOUL_LANTERN.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
        }

        BlockState chain() {
            return Blocks.CHAIN.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
        }

        // ---------------------------------------------------------- the city

        void build() {
            arena();
            catacombs();
            shaft();
            ruins();
        }

        /** A drowned cylinder: a dome for a roof, a bowl for a floor, ribs up the walls, chains hanging, lights high and low. */
        void arena() {
            int mid = (arenaTop + arenaY0) / 2;
            for (int dx = -ARENA_R - 2; dx <= ARENA_R + 2; dx++) {
                for (int dz = -ARENA_R - 2; dz <= ARENA_R + 2; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > ARENA_R + 2.3) continue;
                    double f = Math.min(1, d / ARENA_R);
                    // the roof dips toward the walls, the floor rises toward them: a squashed sphere
                    int top = arenaTop - (int) Math.round(f * f * 10);
                    int bot = arenaY0 + (int) Math.round(f * f * 12);
                    boolean wallCol = d > ARENA_R - 0.5;
                    for (int y = bot - 2; y <= top + 2; y++) {
                        boolean inside = !wallCol && y > bot && y < top;
                        if (inside) {
                            put(dx, y, dz, water);
                        } else if (y >= bot - 2 && y <= top + 2) {
                            boolean rib = wallCol && (Math.round(Math.atan2(dz, dx) * 18 / Math.PI) % 3 == 0);
                            put(dx, y, dz, y <= bot ? (h(dx, dz) % 9 == 0 ? trim : floor) : y >= top ? (h(dx, dz) % 6 == 0 ? tilesCracked : tiles)
                                    : rib ? polished : wallAt(dx, y, dz));
                        }
                    }
                }
            }
            // the seal in the bottom of the bowl
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (dx * dx + dz * dz > 10) continue;
                    put(dx, arenaY0, dz, dx * dx + dz * dz <= 2 ? Blocks.CHISELED_DEEPSLATE.defaultBlockState() : polished);
                }
            }
            // broken columns standing up from the bowl, chains down from the dome, lanterns on the walls
            for (int i = 0; i < 10; i++) {
                double a = Math.PI * 2 * i / 10 + 0.3;
                int r = 22 + h(i, 5) % 10;
                int px = Mth.floor(Math.cos(a) * r), pz = Mth.floor(Math.sin(a) * r);
                double f = Math.min(1, r / (double) ARENA_R);
                int bot = arenaY0 + (int) Math.round(f * f * 12);
                int height = 8 + h(i, 6) % 18;
                for (int y = bot + 1; y <= bot + height; y++) {
                    for (int[] o : new int[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}}) put(px + o[0], y, pz + o[1], y == bot + height ? polished : wallAt(px + o[0], y, pz + o[1]));
                }
                if (h(i, 7) % 2 == 0) put(px, bot + height + 1, pz, Blocks.SEA_LANTERN.defaultBlockState());
            }
            for (int i = 0; i < 14; i++) {
                double a = Math.PI * 2 * i / 14;
                int r = 8 + h(i, 8) % 26;
                int px = Mth.floor(Math.cos(a) * r), pz = Mth.floor(Math.sin(a) * r);
                double f = Math.min(1, r / (double) ARENA_R);
                int top = arenaTop - (int) Math.round(f * f * 10);
                int len = 6 + h(i, 9) % 16;
                for (int y = top - 1; y >= top - len; y--) put(px, y, pz, chain());
                put(px, top - len - 1, pz, lantern());
            }
            for (int ring = 0; ring < 3; ring++) {
                int y = arenaY0 + 16 + ring * 12;
                for (int i = 0; i < 24; i++) {
                    double a = Math.PI * 2 * (i + ring * 0.5) / 24;
                    put(Mth.floor(Math.cos(a) * (ARENA_R - 1)), y, Mth.floor(Math.sin(a) * (ARENA_R - 1)), ring == 1 ? Blocks.SEA_LANTERN.defaultBlockState() : lantern());
                }
            }
        }

        void catacombs() {
            // shells first, then the water inside (corridors cut doors through the walls), then what stands in the halls
            for (int x = 0; x < N; x++) {
                for (int z = 0; z < N; z++) {
                    shell(cell(x) - HALL - 1, cataY, cell(z) - HALL - 1, cell(x) + HALL + 1, cataY + ROOM_H + 1, cell(z) + HALL + 1);
                    if (x < N - 1 && right[x][z]) shell(cell(x) + HALL, cataY, cell(z) - CORR - 1, cell(x + 1) - HALL, cataY + CORR_H + 1, cell(z) + CORR + 1);
                    if (z < N - 1 && down[x][z]) shell(cell(x) - CORR - 1, cataY, cell(z) + HALL, cell(x) + CORR + 1, cataY + CORR_H + 1, cell(z + 1) - HALL);
                }
            }
            for (int x = 0; x < N; x++) {
                for (int z = 0; z < N; z++) {
                    box(cell(x) - HALL, cataY + 1, cell(z) - HALL, cell(x) + HALL, cataY + ROOM_H, cell(z) + HALL, water);
                    if (x < N - 1 && right[x][z]) box(cell(x) + HALL, cataY + 1, cell(z) - CORR, cell(x + 1) - HALL, cataY + CORR_H, cell(z) + CORR, water);
                    if (z < N - 1 && down[x][z]) box(cell(x) - CORR, cataY + 1, cell(z) + HALL, cell(x) + CORR, cataY + CORR_H, cell(z + 1) - HALL, water);
                }
            }
            for (int x = 0; x < N; x++) {
                for (int z = 0; z < N; z++) {
                    if (x == HUB && z == HUB) continue;
                    furnish(x, z);
                }
            }
        }

        /** The shaft: from the sea floor down through the hub's ceiling, on through its floor (the well) into the arena's dome. */
        void shaft() {
            int wellR = 5;
            for (int dx = -SHAFT_R - 2; dx <= SHAFT_R + 2; dx++) {
                for (int dz = -SHAFT_R - 2; dz <= SHAFT_R + 2; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > SHAFT_R + 2.3) continue;
                    // above the catacombs: the wide shaft, its wall ringed with bands and lanterns
                    for (int y = cataY + ROOM_H + 1; y <= 1; y++) {
                        if (d <= SHAFT_R) put(dx, y, dz, water);
                        else if (Math.abs(dx) <= HALL + 1 && Math.abs(dz) <= HALL + 1 && y <= cataY + ROOM_H + 1) continue;
                        else put(dx, y, dz, (y - cataY) % 8 == 0 ? trim : wallAt(dx, y, dz));
                    }
                    // the well: a narrower hole through the hub's floor and the rock into the dome
                    if (d <= wellR) {
                        for (int y = arenaTop - 1; y <= cataY; y++) put(dx, y, dz, water);
                    } else if (d <= wellR + 1.3) {
                        for (int y = arenaTop; y <= cataY; y++) put(dx, y, dz, polished);
                    }
                }
            }
            for (int i = 0; i < 8; i++) {   // lanterns down the shaft
                double a = Math.PI * 2 * i / 8;
                for (int y = cataY + ROOM_H + 4; y < -2; y += 8) put(Mth.floor(Math.cos(a) * SHAFT_R), y, Mth.floor(Math.sin(a) * SHAFT_R), lantern());
            }
            // the hub's floor is a ring round the well with a chiseled rim
            for (int dx = -HALL; dx <= HALL; dx++) {
                for (int dz = -HALL; dz <= HALL; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > wellR + 0.5 && d < wellR + 1.6) put(dx, cataY, dz, Blocks.CHISELED_DEEPSLATE.defaultBlockState());
                }
            }
        }

        /** The mouth on the sea floor: a broken ring wall, toppled columns, an arch. */
        void ruins() {
            for (int dx = -SHAFT_R - 8; dx <= SHAFT_R + 8; dx++) {
                for (int dz = -SHAFT_R - 8; dz <= SHAFT_R + 8; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > SHAFT_R && d <= SHAFT_R + 2.3) {
                        int top = 1 + h(dx, dz) % 4;
                        if (h(dx, dz + 5) % 5 == 0) top = 0;
                        for (int y = 0; y <= top; y++) put(dx, y, dz, y == top ? polished : wallAt(dx, y, dz));
                    } else if (d > SHAFT_R + 2.3 && d < SHAFT_R + 8 && h(dx, dz) % 13 == 0) {
                        put(dx, 0, dz, h(dx, dz) % 2 == 0 ? wallCracked : tilesCracked);
                    }
                }
            }
            for (int i = 0; i < 6; i++) {
                double a = Math.PI * 2 * i / 6 + 0.4;
                int px = Mth.floor(Math.cos(a) * (SHAFT_R + 5)), pz = Mth.floor(Math.sin(a) * (SHAFT_R + 5));
                int height = h(i, 31) % 3 == 0 ? 2 : 6 + h(i, 32) % 6;
                for (int y = 0; y <= height; y++) {
                    for (int[] o : new int[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}}) put(px + o[0], y, pz + o[1], y == height ? polished : wallAt(px + o[0], y, pz + o[1]));
                }
                if (height > 4) put(px, height + 1, pz, lantern());
            }
        }

        // ---------------------------------------------------------- what stands in the halls

        void furnish(int cellX, int cellZ) {
            int ox = cell(cellX), oz = cell(cellZ), y0 = cataY;
            int r = h(cellX, cellZ * 3 + 1);
            put(ox, y0 + ROOM_H + 1, oz, Blocks.SEA_LANTERN.defaultBlockState());
            switch (kind(cellX, cellZ)) {
                case HALL -> {
                    for (int[] q : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
                        for (int y = 1; y <= ROOM_H; y++) put(ox + q[0], y0 + y, oz + q[1], y == 1 || y == ROOM_H ? polished : wallAt(ox + q[0], y0 + y, oz + q[1]));
                    }
                    put(ox, y0 + 1, oz, Blocks.SEA_PICKLE.defaultBlockState().setValue(BlockStateProperties.PICKLES, 1 + r % 4));
                }
                case CRYPT -> {
                    for (int i = -4; i <= 4; i += 4) {
                        for (int side : new int[]{-4, 4}) {
                            for (int l = -1; l <= 1; l++) put(ox + side, y0 + 1, oz + i + l, polished);
                            put(ox + side, y0 + 2, oz + i, (r + i) % 3 == 0 ? Blocks.SOUL_SAND.defaultBlockState()
                                    : Blocks.POLISHED_DEEPSLATE_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
                        }
                    }
                    chest(ox, y0 + 1, oz + HALL - 1, Direction.NORTH);
                    put(ox - 1, y0 + 1, oz - HALL + 1, lantern());
                }
                case SHRINE -> {
                    for (int dx = -2; dx <= 2; dx++) put(ox + dx, y0 + 1, oz + HALL - 1, polished);
                    put(ox, y0 + 2, oz + HALL - 1, Blocks.CHISELED_DEEPSLATE.defaultBlockState());
                    put(ox - 2, y0 + 2, oz + HALL - 1, lantern());
                    put(ox + 2, y0 + 2, oz + HALL - 1, lantern());
                    for (int dx = -2; dx <= 2; dx += 2) put(ox + dx, y0 + 1, oz, Blocks.POLISHED_DEEPSLATE_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
                    if (r % 2 == 0) chest(ox + 1, y0 + 2, oz + HALL - 2, Direction.NORTH);
                }
                case LIBRARY -> {
                    for (int i = -HALL + 1; i <= HALL - 1; i++) {
                        if (Math.abs(i) <= CORR + 1) continue;   // the doorways stay clear
                        for (int y = 1; y <= 3; y++) {
                            if (h(i, y + r) % 5 == 0) continue;
                            put(ox - HALL, y0 + y, oz + i, Blocks.BOOKSHELF.defaultBlockState());
                            put(ox + HALL, y0 + y, oz + i, Blocks.BOOKSHELF.defaultBlockState());
                        }
                    }
                    put(ox, y0 + 1, oz, Blocks.LECTERN.defaultBlockState());
                    if (r % 3 == 0) chest(ox - 2, y0 + 1, oz + 2, Direction.EAST);
                }
                case PRISON -> {
                    for (int k = -1; k <= 1; k++) {
                        int cz0 = oz + k * 4;
                        for (int y = 1; y <= ROOM_H - 1; y++) {
                            for (int l = -1; l <= 1; l++) put(ox + 2, y0 + y, cz0 + l, Blocks.IRON_BARS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
                            put(ox + 2, y0 + y, cz0 + 2, wall);
                        }
                        put(ox + 4, y0 + ROOM_H, cz0, chain());
                        put(ox + 4, y0 + ROOM_H - 1, cz0, chain());
                    }
                    put(ox + 4, y0 + 1, oz + 4, Blocks.SKELETON_SKULL.defaultBlockState());
                    if (r % 2 == 1) chest(ox + 4, y0 + 1, oz - 4, Direction.WEST);
                }
                case COLLAPSED -> {
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            if (dx * dx + dz * dz > 5) continue;
                            int pile = 1 + (h(dx, dz + r) % 3);
                            for (int y = 1; y <= pile; y++) put(ox + dx, y0 + y, oz + dz, h(dx + y, dz) % 2 == 0 ? wallCracked : tilesCracked);
                        }
                    }
                    put(ox + 3, y0 + 1, oz - 3, Blocks.SEA_PICKLE.defaultBlockState().setValue(BlockStateProperties.PICKLES, 3));
                }
            }
        }

        void chest(int x, int y, int z, Direction facing) {
            BlockPos p = c.offset(x, y, z);
            if (!clip.isInside(p)) return;
            level.setBlock(p, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing).setValue(ChestBlock.WATERLOGGED, true), 2);
            RandomizableContainerBlockEntity.setLootTable(level, RandomSource.create(h(x, z)), p, Soulless.id(LOOT));
        }
    }
}
