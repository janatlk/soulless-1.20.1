package com.soulless.boss;

import com.soulless.registry.ModBlocks;
import com.soulless.registry.ModEntities;
import com.soulless.registry.ModItems;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Drives the whole encounter: (1) the arena ({@link ArenaPlan}) is raised block by block around the altar,
 * remembering every block it replaced; (2) a boss bar fills while souls gather; (3) the Wailing Reaper rises
 * and the fight runs; (4) when it ends the arena crumbles away block by block, the original terrain coming
 * back. Victory leaves a chest with Soul Forged where the altar stood; defeat (the challengers all died)
 * makes the Reaper vanish with no reward and turns the altar to obsidian - the fallen players' belongings
 * are waiting on it.
 */
public class SoulAltarBlockEntity extends BlockEntity {
    public static final int ARENA_RADIUS = ArenaPlan.RADIUS;
    public static final int ARENA_HEIGHT = 12;
    /** The altar platform itself (left untouched by the arena). */
    public static final int PLATFORM_RADIUS = 4;
    private static final int OPS_PER_TICK = 160;
    private static final int SCAN_PER_TICK = 6000;
    private static final int FILL_TICKS = 100;

    private enum Phase { IDLE, BUILD, FILL, FIGHT, DISMANTLE, DONE }

    private Phase phase = Phase.IDLE;
    private boolean victory;
    private int step;
    private int fillTicks;
    private List<ArenaPlan.Op> plan;
    private ServerBossEvent bar;
    /** Every block the arena replaced, in build order: position and the original state id. */
    private final LongArrayList changedPos = new LongArrayList();
    private final IntArrayList changedState = new IntArrayList();
    private final LongOpenHashSet changedSet = new LongOpenHashSet();

    /** Altars with an encounter in progress, for the death / drop events. */
    private static final Set<SoulAltarBlockEntity> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());

    public SoulAltarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SOUL_ALTAR_BE.get(), pos, state);
    }

    public void startRitual() {
        phase = Phase.BUILD;
        step = 0;
        plan = null;
        changedPos.clear();
        changedState.clear();
        changedSet.clear();
        ACTIVE.add(this);
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SoulAltarBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        switch (be.phase) {
            case BUILD -> be.build(server);
            case FILL -> be.fill(server);
            case DISMANTLE -> be.dismantle(server);
            default -> {}
        }
    }

    // ------------------------------------------------------------------ queries for the events

    static List<SoulAltarBlockEntity> active() {
        return List.copyOf(ACTIVE);
    }

    /** Is the encounter on (ritual or fight) and is {@code p} inside this arena? */
    boolean contains(net.minecraft.world.entity.Entity e) {
        if (e.level() != level || (phase != Phase.FILL && phase != Phase.FIGHT && phase != Phase.BUILD)) return false;
        double dx = e.getX() - (worldPosition.getX() + 0.5), dz = e.getZ() - (worldPosition.getZ() + 0.5);
        return dx * dx + dz * dz <= (ARENA_RADIUS + 3) * (ARENA_RADIUS + 3) && Math.abs(e.getY() - worldPosition.getY()) < 32;
    }

    /** Where the fallen players' items are laid: on top of the altar. */
    Vec3 dropPoint() {
        return Vec3.atBottomCenterOf(worldPosition.above());
    }

    // ------------------------------------------------------------------ build

    private void build(ServerLevel level) {
        if (plan == null) plan = ArenaPlan.make(worldPosition);
        int done = 0, scanned = 0;
        while (step < plan.size() && done < OPS_PER_TICK && scanned < SCAN_PER_TICK) {
            ArenaPlan.Op op = plan.get(step++);
            scanned++;
            BlockPos p = op.pos();
            BlockState cur = level.getBlockState(p);
            if (cur.is(Blocks.BEDROCK) || cur.getBlock() == ModBlocks.SOUL_ALTAR.get() || cur.equals(op.state())) continue;
            if (op.soft() && !cur.canBeReplaced() && !cur.isAir()) continue;
            if (changedSet.add(p.asLong())) {
                changedPos.add(p.asLong());
                changedState.add(Block.getId(cur));
            }
            level.setBlock(p, op.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            done++;
            if (!op.state().isAir() && level.random.nextInt(8) == 0) {
                level.sendParticles(ParticleTypes.SOUL, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0.02);
            }
        }
        if (level.getGameTime() % 8 == 0 && done > 0) {
            level.playSound(null, worldPosition, SoundEvents.DEEPSLATE_PLACE, SoundSource.BLOCKS, 1.2F, 0.5F + level.random.nextFloat() * 0.2F);
        }
        if (step >= plan.size()) {
            plan = null;
            phase = Phase.FILL;
            fillTicks = 0;
            bar = newBar();
            level.playSound(null, worldPosition, SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 1.5F, 0.5F);
        }
        setChanged();
    }

    private static ServerBossEvent newBar() {
        ServerBossEvent b = new ServerBossEvent(Component.translatable("entity.soulless.wailing_reaper"), BossEvent.BossBarColor.PURPLE,
                BossEvent.BossBarOverlay.NOTCHED_10);
        b.setProgress(0);
        b.setDarkenScreen(true);
        return b;
    }

    // ------------------------------------------------------------------ boss bar + spawn

    private void fill(ServerLevel level) {
        if (bar == null) bar = newBar();
        AABB area = new AABB(worldPosition).inflate(ARENA_RADIUS + 16);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, area)) bar.addPlayer(p);
        fillTicks++;
        bar.setProgress(Math.min(1, fillTicks / (float) FILL_TICKS));
        // souls spiral down into the altar
        double a = fillTicks * 0.4;
        for (int i = 0; i < 4; i++) {
            double ang = a + i * Math.PI / 2, r = 6 - 5 * fillTicks / (double) FILL_TICKS;
            level.sendParticles(ParticleTypes.SOUL, worldPosition.getX() + 0.5 + Math.cos(ang) * r, worldPosition.getY() + 1 + r * 0.5,
                    worldPosition.getZ() + 0.5 + Math.sin(ang) * r, 1, 0, 0, 0, 0);
        }
        if (fillTicks % 20 == 0) {
            level.playSound(null, worldPosition, SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 1.5F, 0.5F + fillTicks / (float) FILL_TICKS);
        }
        if (fillTicks >= FILL_TICKS) {
            bar.removeAllPlayers();
            bar = null;
            WailingReaperEntity boss = ModEntities.WAILING_REAPER.get().create(level);
            if (boss != null) {
                // just off the platform, clear of the spires
                boss.moveTo(worldPosition.getX() + 0.5, worldPosition.getY() - 1, worldPosition.getZ() + 7.5, 180, 0);
                boss.setHome(worldPosition);
                level.addFreshEntity(boss);
                boss.playEmerge();
            }
            level.sendParticles(ParticleTypes.SOUL, worldPosition.getX() + 0.5, worldPosition.getY() + 2, worldPosition.getZ() + 7.5, 120, 1, 3, 1, 0.1);
            level.playSound(null, worldPosition, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0F, 0.6F);
            phase = Phase.FIGHT;
        }
        setChanged();
    }

    // ------------------------------------------------------------------ the end of the fight

    /** The Reaper died: the arena crumbles, then the reward chest takes the altar's place. */
    public void onBossDefeated() {
        if (phase == Phase.DISMANTLE || phase == Phase.DONE) return;
        victory = true;
        startDismantle();
    }

    /**
     * A challenger died in the arena. When nobody is left standing, the Reaper fades away unbeaten (no reward)
     * and the arena crumbles; the altar will turn to obsidian.
     */
    void onPlayerDied(ServerPlayer dead) {
        if (!(level instanceof ServerLevel level)) return;
        boolean anyoneLeft = level.getEntitiesOfClass(ServerPlayer.class, new AABB(worldPosition).inflate(ARENA_RADIUS + 3, 32, ARENA_RADIUS + 3),
                p -> p != dead && p.isAlive() && !p.isSpectator() && !p.isCreative() && contains(p)).size() > 0;
        if (anyoneLeft) return;
        for (WailingReaperEntity r : level.getEntitiesOfClass(WailingReaperEntity.class, new AABB(worldPosition).inflate(ARENA_RADIUS + 16, 40, ARENA_RADIUS + 16))) {
            if (worldPosition.equals(r.home())) {
                level.sendParticles(ParticleTypes.SOUL, r.getX(), r.getY() + 3, r.getZ(), 160, 1.5, 3, 1.5, 0.15);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, r.getX(), r.getY() + 3, r.getZ(), 60, 1.5, 3, 1.5, 0.05);
                level.playSound(null, r.blockPosition(), com.soulless.registry.ModSounds.REAPER_SCREAM.get(), SoundSource.HOSTILE, 3.0F, 1.3F);
                r.discard();
            }
        }
        if (bar != null) {
            bar.removeAllPlayers();
            bar = null;
        }
        victory = false;
        startDismantle();
    }

    private void startDismantle() {
        phase = Phase.DISMANTLE;
        step = changedPos.size();
        setChanged();
    }

    /** Puts the original terrain back, newest change first, so the arena falls apart from the top down. */
    private void dismantle(ServerLevel level) {
        int done = 0;
        while (step > 0 && done < OPS_PER_TICK) {
            step--;
            BlockPos p = BlockPos.of(changedPos.getLong(step));
            BlockState original = Block.stateById(changedState.getInt(step));
            if (level.getBlockState(p).getBlock() == ModBlocks.SOUL_ALTAR.get()) continue;
            level.setBlock(p, original, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            done++;
            if (level.random.nextInt(10) == 0) {
                level.sendParticles(ParticleTypes.SOUL, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.03);
            }
        }
        if (level.getGameTime() % 8 == 0) {
            level.playSound(null, worldPosition, SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 1.2F, 0.5F + level.random.nextFloat() * 0.2F);
        }
        if (step <= 0) {
            phase = Phase.DONE;
            ACTIVE.remove(this);
            changedPos.clear();
            changedState.clear();
            changedSet.clear();
            setChanged();
            BlockPos at = worldPosition;
            if (victory) {
                level.setBlock(at, Blocks.CHEST.defaultBlockState(), 3);
                if (level.getBlockEntity(at) instanceof ChestBlockEntity chest) {
                    chest.setItem(13, new ItemStack(ModItems.REAPER_SOUL.get()));
                }
                level.playSound(null, at, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1.0F, 0.6F);
                level.playSound(null, at, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
            } else {
                level.setBlock(at, Blocks.OBSIDIAN.defaultBlockState(), 3);
                level.playSound(null, at, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 1.5F, 0.6F);
            }
            level.sendParticles(ParticleTypes.SOUL, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 60, 0.5, 0.8, 0.5, 0.05);
            return;
        }
        setChanged();
    }

    // ------------------------------------------------------------------ lifecycle / save

    @Override
    public void onLoad() {
        super.onLoad();
        if (phase != Phase.IDLE && phase != Phase.DONE) ACTIVE.add(this);
    }

    @Override
    public void setRemoved() {
        if (bar != null) bar.removeAllPlayers();
        ACTIVE.remove(this);
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("phase", phase.name());
        tag.putBoolean("victory", victory);
        tag.putInt("step", step);
        tag.putInt("fill", fillTicks);
        tag.putLongArray("changedPos", changedPos.toLongArray());
        tag.putIntArray("changedState", changedState.toIntArray());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        try {
            phase = Phase.valueOf(tag.getString("phase"));
        } catch (IllegalArgumentException e) {
            phase = Phase.IDLE;
        }
        victory = tag.getBoolean("victory");
        step = tag.getInt("step");
        fillTicks = tag.getInt("fill");
        changedPos.clear();
        changedState.clear();
        changedSet.clear();
        for (long l : tag.getLongArray("changedPos")) {
            changedPos.add(l);
            changedSet.add(l);
        }
        for (int i : tag.getIntArray("changedState")) changedState.add(i);
    }
}
