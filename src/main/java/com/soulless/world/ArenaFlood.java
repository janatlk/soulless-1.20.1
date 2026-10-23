package com.soulless.world;

import com.soulless.Soulless;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Cleans the Drowned Souls arena. Worldgen that runs after the arena is built (features from the chunks round it)
 * can leave pockets of air in it, which water placed by worldgen never flows into, and loose stuff - gravel, sand,
 * dirt, clay - drops into it down the shaft or is laid on its floor. When a player first comes into an arena (each
 * session), every air block and every block a shovel digs that is joined to its water, inside its walls, becomes
 * water - a flood fill from the middle, a few thousand blocks a tick. The first time ever a player comes into an
 * arena, its Leviathan is put in it (once per arena per world - Spawned).
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class ArenaFlood {
    private static final ResourceKey<Structure> KEY = ResourceKey.create(Registries.STRUCTURE, Soulless.id("drowned_souls"));
    private static final int BUDGET = 6000;
    /** Arenas flooded (or flooding) this session, by centre. */
    private static final Set<Long> DONE = new HashSet<>();
    private static Job job;

    private record Job(ServerLevel level, BlockPos centre, ArrayDeque<Long> open, Set<Long> seen) {}

    @SubscribeEvent
    public static void onTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        if (job != null && job.level == level) {
            step(job);
            if (job.open.isEmpty()) job = null;
            return;
        }
        if (job != null || level.getGameTime() % 40 != 0) return;
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(KEY);
        if (structure == null) return;
        for (var player : level.players()) {
            var start = level.structureManager().getStructureWithPieceAt(player.blockPosition(), structure);
            if (!start.isValid()) continue;
            for (var piece : start.getPieces()) {
                if (!(piece instanceof DrownedSoulsStructure.Piece p) || !DONE.add(p.centre().asLong())) continue;
                BlockPos mid = p.centre().offset(0, (DrownedSoulsStructure.ARENA_TOP_Y + DrownedSoulsStructure.ARENA_BOTTOM_Y) / 2, 0);
                Spawned spawned = level.getDataStorage().computeIfAbsent(Spawned::load, Spawned::new, "soulless_leviathans");
                if (spawned.arenas.add(p.centre().asLong())) {
                    spawned.setDirty();
                    var lev = com.soulless.registry.ModEntities.LEVIATHAN.get().create(level);
                    if (lev != null) {
                        lev.moveTo(mid.getX() + 0.5, mid.getY(), mid.getZ() + 0.5, level.random.nextFloat() * 360, 0);
                        lev.setHome(mid);
                        lev.setPersistenceRequired();
                        level.addFreshEntity(lev);
                    }
                }
                job = new Job(level, p.centre(), new ArrayDeque<>(), new HashSet<>());
                job.open.add(mid.asLong());
                job.seen.add(mid.asLong());
                return;
            }
        }
    }

    private static void step(Job j) {
        BlockState water = Blocks.WATER.defaultBlockState();
        int r = DrownedSoulsStructure.ARENA_R + 1;
        int top = j.centre.getY() + DrownedSoulsStructure.ARENA_TOP_Y + 1, bottom = j.centre.getY() + DrownedSoulsStructure.ARENA_BOTTOM_Y;
        for (int n = 0; n < BUDGET && !j.open.isEmpty(); n++) {
            BlockPos p = BlockPos.of(j.open.poll());
            if (!j.level.isLoaded(p)) continue;
            BlockState s = j.level.getBlockState(p);
            if (s.isAir() || s.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL)) j.level.setBlock(p, water, 2);
            else if (!s.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) continue;
            for (var d : net.minecraft.core.Direction.values()) {
                BlockPos q = p.relative(d);
                int dx = q.getX() - j.centre.getX(), dz = q.getZ() - j.centre.getZ();
                if (q.getY() < bottom || q.getY() > top || dx * dx + dz * dz > r * r) continue;
                if (j.seen.add(q.asLong())) j.open.add(q.asLong());
            }
        }
    }

    /** The arenas whose Leviathan has been put in them already (by centre), kept with the world. */
    public static final class Spawned extends net.minecraft.world.level.saveddata.SavedData {
        final Set<Long> arenas = new HashSet<>();

        static Spawned load(net.minecraft.nbt.CompoundTag tag) {
            Spawned s = new Spawned();
            for (long l : tag.getLongArray("Arenas")) s.arenas.add(l);
            return s;
        }

        @Override
        public net.minecraft.nbt.CompoundTag save(net.minecraft.nbt.CompoundTag tag) {
            tag.putLongArray("Arenas", arenas.stream().mapToLong(Long::longValue).toArray());
            return tag;
        }
    }

    private ArenaFlood() {}
}
