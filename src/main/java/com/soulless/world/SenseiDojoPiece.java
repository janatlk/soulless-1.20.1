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
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * The sensei's dojo, built block by block. Local coordinates: x 0..23 east, z 0..14 south, y 0 = the house floor
 * (one step above the yard). On a levelled stone platform (filled down to the slope, the hill cut away above):
 * <ul>
 *   <li>the house (x 1..11, z 2..11): a raised engawa veranda, a dark timber frame with paper-white walls and shoji
 *       windows, a tatami floor, a hipped roof of dark tiles with an overhang and hanging lanterns; inside a bed,
 *       bookshelves with candles, a potted cherry sapling - and the sensei;</li>
 *   <li>the path and a vermilion torii (south), cherry trees beside the house;</li>
 *   <li>the training yard (x 14..23): raked gravel inside a bamboo fence, straw training dummies, stone lanterns
 *       and a rack of practice weapons.</li>
 * </ul>
 */
public class SenseiDojoPiece extends StructurePiece {
    public static final int WIDTH = 24, DEPTH = 15;
    private static final int UP = 13, DOWN = 26;
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

    /** Build the whole dojo at once with its north-west corner (floor level) at {@code origin} ({@code /soulless dojo}). */
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
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) set(x, y, z, s);
    }

    private void fill(int x0, int y0, int z0, int x1, int y1, int z1, Block b) {
        fill(x0, y0, z0, x1, y1, z1, b.defaultBlockState());
    }

    private static BlockState stair(Block b, Direction facing) {
        return b.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM);
    }

    private static BlockState connected(Block fence, boolean n, boolean e, boolean s, boolean w) {
        BlockState st = fence.defaultBlockState();
        if (st.hasProperty(FenceBlock.NORTH)) {
            st = st.setValue(FenceBlock.NORTH, n).setValue(FenceBlock.EAST, e).setValue(FenceBlock.SOUTH, s).setValue(FenceBlock.WEST, w);
        } else if (st.hasProperty(IronBarsBlock.NORTH)) {
            st = st.setValue(IronBarsBlock.NORTH, n).setValue(IronBarsBlock.EAST, e).setValue(IronBarsBlock.SOUTH, s).setValue(IronBarsBlock.WEST, w);
        }
        return st;
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator gen, RandomSource random,
                            BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        this.level = level;
        this.box = chunkBox;
        platform(random);
        house();
        roof();
        interior();
        grounds();
        yard();
        spawn(level, chunkBox);
        this.level = null;
        this.box = null;
    }

    /** Level the site: a stone platform filled down to the slope, everything above cleared. */
    private void platform(RandomSource random) {
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < DEPTH; z++) {
                fill(x, 0, z, x, UP, z, Blocks.AIR);
                boolean yard = x >= 14 && x <= 23 && z >= 1 && z <= 13;
                set(x, -1, z, yard ? Blocks.GRAVEL : Blocks.GRASS_BLOCK);
                set(x, -2, z, Blocks.DIRT);
                for (int y = -3; y >= -DOWN; y--) {
                    BlockPos p = at(x, y, z);
                    if (!box.isInside(p)) break;
                    BlockState here = level.getBlockState(p);
                    if (!here.isAir() && here.getFluidState().isEmpty() && here.isSolid()) break;
                    set(x, y, z, random.nextInt(5) == 0 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS);
                }
            }
        }
    }

    /** The house body: veranda floor, timber frame, white walls, shoji windows, the door. */
    private void house() {
        // the raised floor (engawa all round, tatami inside) on a stone footing
        fill(1, -1, 2, 11, -1, 11, Blocks.STONE_BRICKS);
        fill(1, 0, 2, 11, 0, 11, Blocks.SPRUCE_PLANKS);
        fill(3, 0, 4, 9, 0, 9, Blocks.BAMBOO_MOSAIC);
        // walls x 2..10, z 3..10: dark oak sill, paper-white walls above
        for (int x = 2; x <= 10; x++) {
            for (int z = 3; z <= 10; z++) {
                boolean edge = x == 2 || x == 10 || z == 3 || z == 10;
                if (!edge) continue;
                set(x, 1, z, Blocks.DARK_OAK_PLANKS);
                set(x, 2, z, Blocks.MUSHROOM_STEM);
                set(x, 3, z, Blocks.MUSHROOM_STEM);
                set(x, 4, z, Blocks.DARK_OAK_PLANKS);
            }
        }
        // timber posts at the corners and between
        int[] px = {2, 6, 10}, pz = {3, 7, 10};
        for (int x : px) for (int z : new int[]{3, 10}) fill(x, 1, z, x, 4, z, Blocks.STRIPPED_DARK_OAK_LOG);
        for (int z : pz) for (int x : new int[]{2, 10}) fill(x, 1, z, x, 4, z, Blocks.STRIPPED_DARK_OAK_LOG);
        // shoji windows: paper panes between the posts
        BlockState paperEW = connected(Blocks.WHITE_STAINED_GLASS_PANE, false, true, false, true);
        BlockState paperNS = connected(Blocks.WHITE_STAINED_GLASS_PANE, true, false, true, false);
        for (int x : new int[]{3, 4, 8, 9}) {
            set(x, 2, 3, paperEW);
            set(x, 2, 10, paperEW);
        }
        set(5, 2, 3, paperEW);
        set(7, 2, 3, paperEW);
        for (int z : new int[]{4, 5, 8, 9}) {
            set(2, 2, z, paperNS);
            set(10, 2, z, paperNS);
        }
        // the door, south, in the middle
        BlockState door = Blocks.CHERRY_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        set(6, 1, 10, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(6, 2, 10, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        set(6, 3, 10, Blocks.DARK_OAK_PLANKS);
        // ceiling
        fill(3, 4, 4, 9, 4, 9, Blocks.SPRUCE_PLANKS);
        // veranda posts carrying the eaves
        for (int[] c : new int[][]{{1, 2}, {11, 2}, {1, 11}, {11, 11}, {6, 11}, {1, 7}, {11, 7}}) {
            fill(c[0], 1, c[1], c[0], 4, c[1], Blocks.DARK_OAK_FENCE);
        }
        // steps down to the path
        for (int x = 5; x <= 7; x++) set(x, 0, 12, stair(Blocks.SPRUCE_STAIRS, Direction.NORTH));
    }

    /** A hipped roof of dark tiles with a wide overhang, a ridge, and lanterns under the eaves. */
    private void roof() {
        int x0 = 0, x1 = 12, z0 = 1, z1 = 12;
        for (int k = 0; x0 + k <= x1 - k && z0 + k <= z1 - k; k++) {
            int ax = x0 + k, bx = x1 - k, az = z0 + k, bz = z1 - k, y = 5 + k;
            for (int x = ax; x <= bx; x++) {
                for (int z = az; z <= bz; z++) {
                    boolean north = z == az, south = z == bz, west = x == ax, east = x == bx;
                    if (!(north || south || west || east)) continue;
                    BlockState s;
                    if ((north || south) && (west || east)) s = Blocks.DEEPSLATE_TILES.defaultBlockState();   // corners
                    else if (north) s = stair(Blocks.DEEPSLATE_TILE_STAIRS, Direction.SOUTH);
                    else if (south) s = stair(Blocks.DEEPSLATE_TILE_STAIRS, Direction.NORTH);
                    else if (west) s = stair(Blocks.DEEPSLATE_TILE_STAIRS, Direction.EAST);
                    else s = stair(Blocks.DEEPSLATE_TILE_STAIRS, Direction.WEST);
                    if (ax == bx || az == bz) s = Blocks.DEEPSLATE_TILES.defaultBlockState();   // the ridge
                    set(x, y, z, s);
                }
            }
        }
        // the ridge cap and its upturned ends
        set(6, 11, 6, Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        set(6, 11, 7, Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        // the eave corners turned up
        for (int[] c : new int[][]{{0, 1}, {12, 1}, {0, 12}, {12, 12}}) {
            set(c[0], 6, c[1], Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        }
        // lanterns hanging under the front and back eaves
        BlockState hanging = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        for (int x : new int[]{3, 9}) {
            set(x, 4, 12, hanging);
            set(x, 4, 1, hanging);
        }
    }

    private void interior() {
        // the sensei's futon (a red bed), head to the north wall
        BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        set(3, 1, 4, bed.setValue(BedBlock.PART, BedPart.HEAD));
        set(3, 1, 5, bed.setValue(BedBlock.PART, BedPart.FOOT));
        // scrolls on the shelves, a candle on top
        set(9, 1, 4, Blocks.BOOKSHELF);
        set(9, 1, 5, Blocks.BOOKSHELF);
        set(9, 2, 4, Blocks.WHITE_CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 2).setValue(CandleBlock.LIT, true));
        set(9, 2, 5, Blocks.POTTED_CHERRY_SAPLING);
        // a low table: a slab with a lantern on it
        set(7, 1, 7, Blocks.SPRUCE_SLAB);
        set(3, 1, 9, Blocks.LANTERN);
        set(9, 1, 9, Blocks.DECORATED_POT);
        set(3, 1, 7, Blocks.POTTED_BAMBOO);
    }

    /** The path, the torii, the cherry trees. */
    private void grounds() {
        for (int z = 12; z < DEPTH; z++) for (int x = 5; x <= 7; x++) set(x, -1, z, Blocks.DIRT_PATH);
        // the torii: vermilion posts, a red tie beam, a black top beam overhanging both sides
        for (int x : new int[]{4, 8}) fill(x, 0, 14, x, 3, 14, Blocks.RED_CONCRETE);
        fill(5, 3, 14, 7, 3, 14, Blocks.RED_CONCRETE);
        fill(3, 4, 14, 9, 4, 14, Blocks.POLISHED_BLACKSTONE);
        set(2, 4, 14, Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        set(10, 4, 14, Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        // cherry trees beside the house
        tree(13, 1);
        tree(13, 13);
        tree(0, 13);
    }

    private void tree(int x, int z) {
        fill(x, 0, z, x, 3, z, Blocks.CHERRY_LOG);
        BlockState leaves = Blocks.CHERRY_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 3; dy <= 5; dy++) {
                    int r = Math.abs(dx) + Math.abs(dz) + (dy == 5 ? 1 : 0);
                    if (r > 3 || (dx == 0 && dz == 0 && dy < 4)) continue;
                    int lx = x + dx, lz = z + dz;
                    if (lx < 0 || lx >= WIDTH || lz < 0 || lz >= DEPTH) continue;
                    if (lx <= 12 && lz >= 1 && lz <= 12) continue;   // keep off the roof
                    set(lx, dy, lz, leaves);
                }
            }
        }
    }

    /** The training yard: a bamboo fence round raked gravel, dummies, stone lanterns, a weapon rack. */
    private void yard() {
        int x0 = 14, x1 = 23, z0 = 1, z1 = 13;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                if (!edge) continue;
                if (x == x0 && z >= 6 && z <= 8) continue;   // the way in, facing the house
                boolean ns = x == x0 || x == x1;
                set(x, 0, z, connected(Blocks.BAMBOO_FENCE, ns, !ns, ns, !ns));
            }
        }
        // straw dummies: a post, a bale for the body, a target for the head
        for (int[] d : new int[][]{{17, 4}, {20, 7}, {17, 10}}) {
            set(d[0], 0, d[1], Blocks.SPRUCE_FENCE);
            set(d[0], 1, d[1], Blocks.HAY_BLOCK);
            set(d[0], 2, d[1], Blocks.TARGET);
        }
        // stone lanterns (toro) in the corners
        for (int[] c : new int[][]{{15, 2}, {22, 2}, {15, 12}, {22, 12}}) {
            set(c[0], 0, c[1], Blocks.STONE_BRICK_WALL);
            set(c[0], 1, c[1], Blocks.LANTERN);
            set(c[0], 2, c[1], Blocks.STONE_BRICK_SLAB);
        }
    }

    /** The sensei, at home; practice weapons on stands in the yard. */
    private void spawn(WorldGenLevel level, BoundingBox chunkBox) {
        BlockPos home = at(6, 1, 7);
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
        for (int z : new int[]{6, 8}) {
            BlockPos p = at(22, 0, z);
            if (!chunkBox.isInside(p)) continue;
            ArmorStand stand = EntityType.ARMOR_STAND.create(level.getLevel());
            if (stand == null) continue;
            stand.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 90, 0);
            stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(z == 6 ? Items.WOODEN_SWORD : Items.STONE_SWORD));
            level.addFreshEntity(stand);
        }
    }
}
