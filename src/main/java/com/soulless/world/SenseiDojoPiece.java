package com.soulless.world;

import com.soulless.registry.ModStructures;
import com.soulless.registry.ModVillagers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * The sensei's dojo, built block by block. Local coordinates: x 0..35 east, z 0..27 south (the gate), y 0 = ground
 * level of the grounds (the grass top is at y -1). On a levelled platform filled down to the slope, the hill cut
 * away above:
 * <ul>
 *   <li>the main house (x 4..22, z 6..18): on a stone base, a raised engawa veranda all round, a dark timber frame
 *       with pale plaster walls and lattice shoji, an open front; a two-tier irimoya roof of dark tiles - a hipped
 *       lower roof with upturned corners and a gabled upper roof with a ridge - with lanterns under the eaves;
 *       inside: tatami, a tokonoma alcove with a katana on the wall and a bonsai, a futon, cushions round a low
 *       table, paper lanterns, shelves of scrolls - and the sensei;</li>
 *   <li>the front garden: a torii at the gate, a stone path between stone lanterns, a pond with lilies, cherry
 *       trees, bamboo, a raked gravel garden with rocks;</li>
 *   <li>the training yard (x 25..35, z 4..22): gravel inside a bamboo fence, straw dummies, targets, weapon racks.</li>
 * </ul>
 */
public class SenseiDojoPiece extends StructurePiece {
    public static final int WIDTH = 36, DEPTH = 28;
    private static final int UP = 18, DOWN = 26;
    private final BlockPos origin;

    public SenseiDojoPiece(BlockPos origin) {
        super(ModStructures.SENSEI_DOJO_PIECE.get(), 0,
                new BoundingBox(origin.getX(), origin.getY() - DOWN, origin.getZ(), origin.getX() + WIDTH - 1, origin.getY() + UP, origin.getZ() + DEPTH - 1));
        this.origin = origin;
    }

    public SenseiDojoPiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
        super(ModStructures.SENSEI_DOJO_PIECE.get(), tag);
        this.origin = new BlockPos(tag.getInt("OX"), tag.getInt("OY"), tag.getInt("OZ"));
    }

    /** Build the whole dojo at once with its north-west corner (ground level) at {@code origin} ({@code /soulless dojo}). */
    public static void buildAt(net.minecraft.server.level.ServerLevel level, BlockPos origin) {
        SenseiDojoPiece piece = new SenseiDojoPiece(origin);
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.random,
                piece.getBoundingBox(), new ChunkPos(origin), origin);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        tag.putInt("OX", origin.getX());
        tag.putInt("OY", origin.getY());
        tag.putInt("OZ", origin.getZ());
    }

    // ------------------------------------------------------------------ placement helpers (clipped to the chunk)

    private WorldGenLevel level;
    private BoundingBox box;
    private RandomSource random;

    private BlockPos at(int x, int y, int z) {
        return origin.offset(x, y, z);
    }

    private void set(int x, int y, int z, BlockState s) {
        BlockPos p = at(x, y, z);
        if (box.isInside(p)) level.setBlock(p, s, 2);
    }

    private void set(int x, int y, int z, Block b) {
        set(x, y, z, b.defaultBlockState());
    }

    private void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(x, y, z, s);
    }

    private void fill(int x0, int y0, int z0, int x1, int y1, int z1, Block b) {
        fill(x0, y0, z0, x1, y1, z1, b.defaultBlockState());
    }

    private static BlockState stair(Block b, Direction facing, boolean upsideDown) {
        return b.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, upsideDown ? Half.TOP : Half.BOTTOM);
    }

    private static BlockState slab(Block b, boolean top) {
        return b.defaultBlockState().setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
    }

    private static BlockState fence(Block b, boolean n, boolean e, boolean s, boolean w) {
        return b.defaultBlockState().setValue(FenceBlock.NORTH, n).setValue(FenceBlock.EAST, e)
                .setValue(FenceBlock.SOUTH, s).setValue(FenceBlock.WEST, w);
    }

    /** A lattice shoji panel in a wall: a birch trapdoor stood open in the wall's plane. */
    private static BlockState shoji(Direction outward) {
        return Blocks.BIRCH_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, true)
                .setValue(TrapDoorBlock.FACING, outward).setValue(TrapDoorBlock.HALF, Half.BOTTOM);
    }

    private static final BlockState HANGING = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator gen, RandomSource random,
                            BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        this.level = level;
        this.box = chunkBox;
        this.random = RandomSource.create(origin.asLong());   // the same garden in every chunk it spans
        platform();
        house();
        roof();
        interior();
        garden();
        yard();
        spawn(level, chunkBox);
        this.level = null;
        this.box = null;
        this.random = null;
    }

    /** Level the site: grass over earth over a stone platform filled down to the slope; everything above cleared. */
    private void platform() {
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < DEPTH; z++) {
                fill(x, 0, z, x, UP, z, Blocks.AIR);
                set(x, -1, z, Blocks.GRASS_BLOCK);
                set(x, -2, z, Blocks.DIRT);
                set(x, -3, z, Blocks.DIRT);
                for (int y = -4; y >= -DOWN; y--) {
                    BlockPos p = at(x, y, z);
                    if (!box.isInside(p)) break;
                    BlockState here = level.getBlockState(p);
                    if (here.isSolid() && here.getFluidState().isEmpty()) break;
                    set(x, y, z, random.nextInt(5) == 0 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS);
                }
            }
        }
    }

    // ------------------------------------------------------------------ the main house

    private static final int HX0 = 4, HX1 = 22, HZ0 = 6, HZ1 = 18;      // the engawa's outer edge
    private static final int WX0 = 5, WX1 = 21, WZ0 = 7, WZ1 = 17;      // the walls
    private static final int FLOOR = 1, WALL_TOP = 5;

    private void house() {
        // the stone base, one high, and the floor on it (engawa planks, tatami inside)
        fill(HX0, 0, HZ0, HX1, 0, HZ1, Blocks.POLISHED_ANDESITE);
        for (int x = HX0; x <= HX1; x++) {
            set(x, 0, HZ0, Blocks.STONE_BRICKS);
            set(x, 0, HZ1, Blocks.STONE_BRICKS);
        }
        for (int z = HZ0; z <= HZ1; z++) {
            set(HX0, 0, z, Blocks.STONE_BRICKS);
            set(HX1, 0, z, Blocks.STONE_BRICKS);
        }
        fill(HX0, FLOOR, HZ0, HX1, FLOOR, HZ1, Blocks.DARK_OAK_PLANKS);
        fill(WX0 + 1, FLOOR, WZ0 + 1, WX1 - 1, FLOOR, WZ1 - 1, Blocks.BAMBOO_MOSAIC);
        // tatami borders: strips of dark planks every few blocks, like the black edges of the mats
        for (int x = WX0 + 4; x < WX1; x += 4) fill(x, FLOOR, WZ0 + 1, x, FLOOR, WZ1 - 1, Blocks.STRIPPED_BAMBOO_BLOCK);

        // walls: a dark sill, lattice shoji (plaster panels between on the sides), the top beam; posts between bays
        for (int x = WX0; x <= WX1; x++) {
            for (int z = WZ0; z <= WZ1; z++) {
                boolean north = z == WZ0, south = z == WZ1, west = x == WX0, east = x == WX1;
                if (!(north || south || west || east)) continue;
                boolean corner = (north || south) && (west || east);
                boolean post = corner || ((north || south) && (x - WX0) % 4 == 0) || ((west || east) && (z - WZ0) % 5 == 0);
                if (post) {
                    fill(x, FLOOR + 1, z, x, WALL_TOP, z, Blocks.STRIPPED_DARK_OAK_LOG);
                    continue;
                }
                boolean opening = south && x >= 11 && x <= 15;   // the front stands open, the doors slid back
                set(x, WALL_TOP, z, Blocks.DARK_OAK_PLANKS);
                if (opening) {
                    fill(x, FLOOR + 1, z, x, FLOOR + 3, z, Blocks.AIR);
                    continue;
                }
                set(x, FLOOR + 1, z, Blocks.DARK_OAK_PLANKS);
                Direction out = north ? Direction.NORTH : south ? Direction.SOUTH : west ? Direction.WEST : Direction.EAST;
                boolean plaster = (west || east) && ((z - WZ0) / 5) % 2 == 1;
                for (int y = FLOOR + 2; y <= FLOOR + 3; y++) set(x, y, z, plaster ? Blocks.CALCITE.defaultBlockState() : shoji(out));
            }
        }
        // engawa posts carrying the eaves, and a lantern on each corner post
        for (int x = HX0; x <= HX1; x += 6) {
            fill(x, FLOOR + 1, HZ0, x, WALL_TOP, HZ0, Blocks.DARK_OAK_FENCE);
            fill(x, FLOOR + 1, HZ1, x, WALL_TOP, HZ1, Blocks.DARK_OAK_FENCE);
        }
        for (int z = HZ0; z <= HZ1; z += 6) {
            fill(HX0, FLOOR + 1, z, HX0, WALL_TOP, z, Blocks.DARK_OAK_FENCE);
            fill(HX1, FLOOR + 1, z, HX1, WALL_TOP, z, Blocks.DARK_OAK_FENCE);
        }
        // the ceiling: spruce boards crossed by dark beams
        fill(WX0 + 1, WALL_TOP, WZ0 + 1, WX1 - 1, WALL_TOP, WZ1 - 1, Blocks.SPRUCE_PLANKS);
        for (int x = WX0 + 4; x < WX1; x += 4) fill(x, WALL_TOP, WZ0 + 1, x, WALL_TOP, WZ1 - 1, Blocks.STRIPPED_DARK_OAK_LOG);
        // wide stone steps up to the front
        for (int x = 10; x <= 16; x++) {
            set(x, 0, HZ1 + 1, stair(Blocks.STONE_BRICK_STAIRS, Direction.NORTH, false));
            set(x, FLOOR, HZ1, stair(Blocks.DARK_OAK_STAIRS, Direction.NORTH, false));
        }
    }

    /**
     * The irimoya roof: a hipped lower roof stepping in from a wide overhang (its corners turned up), then a gabled
     * upper roof running east-west with plastered gable ends and a ridge cap.
     */
    private void roof() {
        Block tile = Blocks.DEEPSLATE_TILE_STAIRS;
        int x0 = HX0 - 2, x1 = HX1 + 2, z0 = HZ0 - 2, z1 = HZ1 + 2, y0 = WALL_TOP + 1;
        int k = 0;
        // the hipped part: 5 steps in, so the gable above stays low and wide
        for (; k < 5; k++) {
            int ax = x0 + k, bx = x1 - k, az = z0 + k, bz = z1 - k, y = y0 + k;
            for (int x = ax; x <= bx; x++) {
                for (int z = az; z <= bz; z++) {
                    boolean n = z == az, s = z == bz, w = x == ax, e = x == bx;
                    if (!(n || s || w || e)) continue;
                    BlockState st;
                    if ((n || s) && (w || e)) st = Blocks.DEEPSLATE_TILES.defaultBlockState();
                    else if (n) st = stair(tile, Direction.SOUTH, false);
                    else if (s) st = stair(tile, Direction.NORTH, false);
                    else if (w) st = stair(tile, Direction.EAST, false);
                    else st = stair(tile, Direction.WEST, false);
                    set(x, y, z, st);
                }
            }
            // the eave's underside, so the overhang reads as a thick edge
            if (k == 0) {
                for (int x = ax; x <= bx; x++) {
                    set(x, y - 1, az, slab(Blocks.DARK_OAK_SLAB, true));
                    set(x, y - 1, bz, slab(Blocks.DARK_OAK_SLAB, true));
                }
                for (int z = az; z <= bz; z++) {
                    set(ax, y - 1, z, slab(Blocks.DARK_OAK_SLAB, true));
                    set(bx, y - 1, z, slab(Blocks.DARK_OAK_SLAB, true));
                }
            }
        }
        // close the eaves: a dark frieze on the wall line from the top beam up to the roof step above it, so the attic
        // can't be seen from outside under the overhang (the hipped rings step in two blocks outside the walls)
        int frieze = y0 + (WX0 - x0) - 1;
        for (int x = WX0; x <= WX1; x++) {
            fill(x, WALL_TOP + 1, WZ0, x, frieze, WZ0, Blocks.DARK_OAK_PLANKS);
            fill(x, WALL_TOP + 1, WZ1, x, frieze, WZ1, Blocks.DARK_OAK_PLANKS);
        }
        for (int z = WZ0; z <= WZ1; z++) {
            fill(WX0, WALL_TOP + 1, z, WX0, frieze, z, Blocks.DARK_OAK_PLANKS);
            fill(WX1, WALL_TOP + 1, z, WX1, frieze, z, Blocks.DARK_OAK_PLANKS);
        }
        // the closed attic: invisible light so nothing spawns up there
        for (int x = WX0 + 3; x < WX1; x += 5) for (int z = WZ0 + 3; z < WZ1; z += 5) set(x, WALL_TOP + 2, z, Blocks.LIGHT);
        // the corners of the lowest eave turned up
        for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            set(c[0], y0 + 1, c[1], slab(Blocks.DEEPSLATE_TILE_SLAB, false));
        }
        // fill under the hipped steps (hidden) so no gaps show from inside
        int gx0 = x0 + k, gx1 = x1 - k, gz0 = z0 + k, gz1 = z1 - k, gy = y0 + k;
        fill(gx0 + 1, gy - 1, gz0 + 1, gx1 - 1, gy - 1, gz1 - 1, Blocks.SPRUCE_PLANKS);
        // the gabled upper roof: slopes from north and south up to a ridge, gable walls at the ends
        int depth = gz1 - gz0;
        for (int j = 0; j <= depth / 2; j++) {
            int y = gy + j, zn = gz0 + j, zs = gz1 - j;
            for (int x = gx0 - 1; x <= gx1 + 1; x++) {
                if (zn < zs) {
                    set(x, y, zn, stair(tile, Direction.SOUTH, false));
                    set(x, y, zs, stair(tile, Direction.NORTH, false));
                } else {
                    set(x, y, zn, Blocks.DEEPSLATE_TILES);
                }
            }
            // the gable ends: plaster framed in dark wood
            for (int z = zn + 1; z < zs; z++) {
                set(gx0, y, z, j == 0 ? Blocks.DARK_OAK_PLANKS : Blocks.CALCITE);
                set(gx1, y, z, j == 0 ? Blocks.DARK_OAK_PLANKS : Blocks.CALCITE);
            }
        }
        int ridgeZ = (gz0 + gz1) / 2, ridgeY = gy + depth / 2;
        for (int x = gx0 - 1; x <= gx1 + 1; x++) {
            set(x, ridgeY, ridgeZ, Blocks.DEEPSLATE_TILES);
            if (depth % 2 == 1) set(x, ridgeY, ridgeZ + 1, Blocks.DEEPSLATE_TILES);
            set(x, ridgeY + 1, ridgeZ, slab(Blocks.DEEPSLATE_TILE_SLAB, false));
        }
        // the ridge's ends rise (oni-ita)
        set(gx0 - 1, ridgeY + 1, ridgeZ, Blocks.POLISHED_BLACKSTONE_WALL);
        set(gx1 + 1, ridgeY + 1, ridgeZ, Blocks.POLISHED_BLACKSTONE_WALL);
        // lanterns all along the eaves, every three blocks, hung from the roof's underside
        for (int x = HX0; x <= HX1; x += 3) {
            set(x, WALL_TOP, HZ1 + 1, HANGING);
            set(x, WALL_TOP, HZ0 - 1, HANGING);
        }
        for (int z = HZ0; z <= HZ1; z += 3) {
            set(HX0 - 1, WALL_TOP, z, HANGING);
            set(HX1 + 1, WALL_TOP, z, HANGING);
        }
    }

    private void interior() {
        int y = FLOOR + 1;
        // the tokonoma: a raised alcove on the north wall with a bonsai, a katana on the wall above
        fill(12, FLOOR, WZ0 + 1, 14, FLOOR, WZ0 + 1, slab(Blocks.DARK_OAK_SLAB, true));
        set(12, y, WZ0 + 1, slab(Blocks.DARK_OAK_SLAB, false));
        set(13, y, WZ0 + 1, Blocks.POTTED_FLOWERING_AZALEA);
        set(14, y, WZ0 + 1, Blocks.WHITE_CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true));
        // the futon, west room
        BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        set(7, y, WZ0 + 1, bed.setValue(BedBlock.PART, BedPart.HEAD));
        set(7, y, WZ0 + 2, bed.setValue(BedBlock.PART, BedPart.FOOT));
        // shelves of scrolls with candles, east room
        fill(19, y, WZ0 + 1, 20, y + 1, WZ0 + 1, Blocks.BOOKSHELF);
        set(19, y + 2, WZ0 + 1, Blocks.WHITE_CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 2).setValue(CandleBlock.LIT, true));
        set(20, y + 2, WZ0 + 1, Blocks.POTTED_BAMBOO);
        // a low table with cushions round it in the middle
        set(13, y, 12, Blocks.DARK_OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP));
        set(13, y, 13, Blocks.DARK_OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP));
        set(12, y, 12, Blocks.RED_CARPET);
        set(14, y, 13, Blocks.RED_CARPET);
        set(12, y, 13, Blocks.WHITE_CARPET);
        set(14, y, 12, Blocks.WHITE_CARPET);
        // paper lanterns hanging from the ceiling beams, and standing in the corners
        for (int[] c : new int[][]{{9, 10}, {17, 10}, {9, 14}, {17, 14}}) set(c[0], WALL_TOP - 1, c[1], HANGING);
        set(WX0 + 1, y, WZ1 - 1, Blocks.LANTERN);
        set(WX1 - 1, y, WZ1 - 1, Blocks.LANTERN);
        set(WX1 - 1, y, WZ0 + 4, Blocks.DECORATED_POT);
        set(WX0 + 1, y, WZ0 + 5, Blocks.POTTED_CHERRY_SAPLING);
    }

    // ------------------------------------------------------------------ the grounds

    private void garden() {
        // the stone path from the gate to the steps, stepping stones in gravel, lanterns along it
        for (int z = HZ1 + 2; z < DEPTH; z++) {
            for (int x = 11; x <= 15; x++) set(x, -1, z, Blocks.GRAVEL);
            for (int x = 12; x <= 14; x++) set(x, -1, z, (z + x) % 3 == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.STONE_BRICKS);
        }
        for (int z : new int[]{HZ1 + 3, HZ1 + 7}) {
            toro(9, z);
            toro(17, z);
        }
        // the torii at the gate
        int tz = DEPTH - 1;
        fill(10, 0, tz, 10, 4, tz, Blocks.RED_CONCRETE);
        fill(16, 0, tz, 16, 4, tz, Blocks.RED_CONCRETE);
        set(10, 0, tz, Blocks.POLISHED_BLACKSTONE);
        set(16, 0, tz, Blocks.POLISHED_BLACKSTONE);
        fill(9, 4, tz, 17, 4, tz, Blocks.RED_CONCRETE);
        fill(8, 5, tz, 18, 5, tz, Blocks.POLISHED_BLACKSTONE);
        set(7, 5, tz, stair(Blocks.POLISHED_BLACKSTONE_STAIRS, Direction.EAST, true));
        set(19, 5, tz, stair(Blocks.POLISHED_BLACKSTONE_STAIRS, Direction.WEST, true));
        set(13, 3, tz, Blocks.RED_CONCRETE);   // the tablet between the beams
        // the pond, west of the path: water with lilies, a stone rim
        for (int x = 1; x <= 7; x++) {
            for (int z = 20; z <= 25; z++) {
                double dx = (x - 4) / 3.4, dz = (z - 22.5) / 2.9;
                double r = dx * dx + dz * dz;
                if (r <= 1.0) {
                    set(x, -1, z, Blocks.WATER);
                    set(x, -2, z, Blocks.WATER);
                    set(x, -3, z, Blocks.CLAY);
                    if (random.nextInt(6) == 0) set(x, 0, z, Blocks.LILY_PAD);
                } else if (r <= 1.5) {
                    set(x, -1, z, random.nextBoolean() ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE);
                }
            }
        }
        // the raked gravel garden, east of the path, with a few rocks
        for (int x = 19; x <= 24; x++) for (int z = 21; z <= 26; z++) set(x, -1, z, Blocks.GRAVEL);
        set(20, 0, 22, Blocks.MOSSY_COBBLESTONE);
        set(23, 0, 25, Blocks.ANDESITE);
        set(23, 1, 25, slab(Blocks.ANDESITE_SLAB, false));
        set(21, 0, 25, Blocks.MOSS_CARPET);
        // cherry trees and bamboo
        tree(2, 3, 5);
        tree(2, 15, 4);
        tree(8, 23, 4);
        tree(23, 1, 4);
        for (int[] b : new int[][]{{0, 0}, {1, 0}, {0, 1}, {0, 26}, {0, 27}, {1, 27}, {24, 13}, {24, 14}, {3, 12}}) {
            int h = 4 + random.nextInt(4);
            for (int y = 0; y < h; y++) set(b[0], y, b[1], Blocks.BAMBOO);
        }
    }

    /** A stone lantern (toro): a post, a lantern, a cap. */
    private void toro(int x, int z) {
        set(x, 0, z, Blocks.STONE_BRICK_WALL);
        set(x, 1, z, Blocks.LANTERN);
        set(x, 2, z, slab(Blocks.STONE_BRICK_SLAB, false));
    }

    private void tree(int x, int z, int height) {
        fill(x, 0, z, x, height, z, Blocks.CHERRY_LOG);
        BlockState leaves = Blocks.CHERRY_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        int cy = height + 1;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    double d = dx * dx / 9.0 + dz * dz / 9.0 + dy * dy / 4.0;
                    if (d > 1.0 || (d > 0.75 && random.nextInt(3) == 0)) continue;
                    int lx = x + dx, lz = z + dz, ly = cy + dy;
                    if (lx < 0 || lx >= WIDTH || lz < 0 || lz >= DEPTH) continue;
                    if (lx >= HX0 - 2 && lx <= HX1 + 2 && lz >= HZ0 - 2 && lz <= HZ1 + 2 && ly >= WALL_TOP) continue;   // off the roof
                    if (dx == 0 && dz == 0 && dy <= 0) continue;
                    set(lx, ly, lz, leaves);
                }
            }
        }
    }

    /** The training yard: gravel in a bamboo fence, straw dummies, targets, lanterns, weapon racks. */
    private void yard() {
        int x0 = 25, x1 = 35, z0 = 4, z1 = 22;
        fill(x0 + 1, -1, z0 + 1, x1 - 1, -1, z1 - 1, Blocks.GRAVEL);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                if (!edge) continue;
                if (x == x0 && z >= 11 && z <= 13) continue;   // the way in, facing the house
                boolean ns = x == x0 || x == x1;
                boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                set(x, 0, z, corner ? fence(Blocks.BAMBOO_FENCE, z == z1, x == x0, z == z0, x == x1)
                        : fence(Blocks.BAMBOO_FENCE, ns, !ns, ns, !ns));
                if (corner || (ns && z % 4 == 0) || (!ns && x % 4 == 0)) set(x, 1, z, Blocks.BAMBOO_BLOCK);
            }
        }
        // straw dummies: a post, a bale for the body, a target for the head
        for (int[] d : new int[][]{{28, 7}, {32, 7}, {28, 17}, {32, 17}}) {
            set(d[0], 0, d[1], Blocks.SPRUCE_FENCE);
            set(d[0], 1, d[1], Blocks.HAY_BLOCK);
            set(d[0], 2, d[1], Blocks.TARGET);
        }
        // a sparring ring marked out in the middle
        for (int x = 28; x <= 32; x++) for (int z = 10; z <= 14; z++) {
            boolean ring = x == 28 || x == 32 || z == 10 || z == 14;
            set(x, -1, z, ring ? Blocks.SMOOTH_SANDSTONE : Blocks.SAND);
        }
        toro(26, 5);
        toro(34, 5);
        toro(26, 21);
        toro(34, 21);
    }

    /** The sensei, at home; practice weapons on stands in the yard; a katana on the tokonoma wall. */
    private void spawn(WorldGenLevel level, BoundingBox chunkBox) {
        BlockPos home = at(13, FLOOR + 1, 10);
        if (chunkBox.isInside(home)) {
            Villager sensei = EntityType.VILLAGER.create(level.getLevel());
            if (sensei != null) {
                sensei.moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 180, 0);
                sensei.finalizeSpawn(level, level.getCurrentDifficultyAt(home), MobSpawnType.STRUCTURE, null, null);
                sensei.setVillagerData(sensei.getVillagerData().setProfession(ModVillagers.SENSEI.get()).setLevel(1));
                sensei.setVillagerXp(1);   // keeps the profession without a job site
                sensei.setPersistenceRequired();
                level.addFreshEntityWithPassengers(sensei);
            }
        }
        BlockPos frame = at(13, FLOOR + 3, WZ0 + 1);
        if (chunkBox.isInside(frame)) {
            ItemFrame f = new ItemFrame(level.getLevel(), frame, Direction.SOUTH);
            f.setItem(new ItemStack(Items.IRON_SWORD), false);
            level.addFreshEntity(f);
        }
        int[][] racks = {{34, 9, 0}, {34, 11, 1}, {34, 13, 2}, {34, 15, 0}};
        for (int[] r : racks) {
            BlockPos p = at(r[0], 0, r[1]);
            if (!chunkBox.isInside(p)) continue;
            ArmorStand stand = EntityType.ARMOR_STAND.create(level.getLevel());
            if (stand == null) continue;
            stand.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 90, 0);
            stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(r[2] == 0 ? Items.WOODEN_SWORD : r[2] == 1 ? Items.STONE_SWORD : Items.BOW));
            level.addFreshEntity(stand);
        }
    }
}
