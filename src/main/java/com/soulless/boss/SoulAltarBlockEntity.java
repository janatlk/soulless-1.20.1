package com.soulless.boss;

import com.soulless.registry.ModBlocks;
import com.soulless.registry.ModEntities;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives the summoning ritual: (1) the arena is raised block by block around the altar, (2) a boss bar
 * fills up while souls gather, (3) the Wailing Reaper rises from the altar.
 */
public class SoulAltarBlockEntity extends BlockEntity {
    public static final int ARENA_RADIUS = 15;
    public static final int ARENA_HEIGHT = 12;
    private static final int WALL_HEIGHT = 5;
    /** The altar platform itself (left untouched by the arena). */
    public static final int PLATFORM_RADIUS = 4;
    private static final int OPS_PER_TICK = 90;
    private static final int FILL_TICKS = 100;

    private enum Phase { IDLE, BUILD, FILL, DONE }

    private Phase phase = Phase.IDLE;
    private int step;
    private int fillTicks;
    private List<long[]> plan;
    private ServerBossEvent bar;

    public SoulAltarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SOUL_ALTAR_BE.get(), pos, state);
    }

    public void startRitual() {
        phase = Phase.BUILD;
        step = 0;
        plan = null;
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SoulAltarBlockEntity be) {
        if (!(level instanceof ServerLevel server) || be.phase == Phase.IDLE || be.phase == Phase.DONE) return;
        if (be.phase == Phase.BUILD) {
            be.build(server);
        } else if (be.phase == Phase.FILL) {
            be.fill(server);
        }
    }

    // ------------------------------------------------------------------ arena

    /**
     * Each op is {x, y, z, kind}: 0 = clear to air, 1 = floor, 2 = wall, 3 = wall accent, 4 = soul fire.
     * Ordered so the arena visibly grows: clearing first, then the floor ring by ring outward, then the wall
     * course by course upward.
     */
    private List<long[]> makePlan() {
        List<long[]> ops = new ArrayList<>();
        BlockPos c = worldPosition;
        // the altar sits on a one-block dais; the arena floor is level with the platform around it
        int floorY = c.getY() - 2;
        for (int r = 0; r <= ARENA_RADIUS + 1; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > ARENA_RADIUS + 0.5 || d <= PLATFORM_RADIUS + 0.5) continue;
                    for (int y = 0; y < ARENA_HEIGHT; y++) ops.add(new long[]{c.getX() + dx, floorY + 1 + y, c.getZ() + dz, 0});
                }
            }
        }
        for (int r = PLATFORM_RADIUS + 1; r <= ARENA_RADIUS; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > r + 0.5 || d <= r - 0.5) continue;
                    ops.add(new long[]{c.getX() + dx, floorY, c.getZ() + dz, 1});
                }
            }
        }
        for (int y = 1; y <= WALL_HEIGHT; y++) {
            for (int dx = -ARENA_RADIUS - 1; dx <= ARENA_RADIUS + 1; dx++) {
                for (int dz = -ARENA_RADIUS - 1; dz <= ARENA_RADIUS + 1; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d <= ARENA_RADIUS + 0.5 || d > ARENA_RADIUS + 1.5) continue;
                    boolean pillar = (Math.abs(dx) <= 1 || Math.abs(dz) <= 1 || Math.abs(Math.abs(dx) - Math.abs(dz)) <= 1);
                    long kind = y == WALL_HEIGHT && pillar ? 4 : pillar ? 3 : 2;
                    if (y == WALL_HEIGHT && !pillar) continue;
                    ops.add(new long[]{c.getX() + dx, floorY + y, c.getZ() + dz, kind});
                }
            }
        }
        return ops;
    }

    private void build(ServerLevel level) {
        if (plan == null) plan = makePlan();
        int done = 0;
        while (step < plan.size() && done < OPS_PER_TICK) {
            long[] op = plan.get(step++);
            BlockPos p = new BlockPos((int) op[0], (int) op[1], (int) op[2]);
            BlockState cur = level.getBlockState(p);
            if (cur.is(Blocks.BEDROCK) || cur.getBlock() == ModBlocks.SOUL_ALTAR.get()) continue;
            BlockState next = switch ((int) op[3]) {
                case 1 -> ((p.getX() + p.getZ()) & 1) == 0 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                        : (Math.floorMod(p.getX() * 7 + p.getZ() * 13, 9) == 0 ? Blocks.SOUL_SOIL.defaultBlockState()
                        : Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                case 2 -> Blocks.OBSIDIAN.defaultBlockState();
                case 3 -> Blocks.CRYING_OBSIDIAN.defaultBlockState();
                case 4 -> Blocks.SOUL_FIRE.defaultBlockState();
                default -> Blocks.AIR.defaultBlockState();
            };
            if (op[3] == 4) {
                level.setBlock(p.below(), Blocks.SOUL_SOIL.defaultBlockState(), 3);
            }
            if (cur.equals(next)) continue;
            level.setBlock(p, next, 3);
            done++;
            if (op[3] != 0 && level.random.nextInt(6) == 0) {
                level.sendParticles(ParticleTypes.SOUL, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0.02);
            }
        }
        if (step % 400 < OPS_PER_TICK) {
            level.playSound(null, worldPosition, SoundEvents.DEEPSLATE_PLACE, SoundSource.BLOCKS, 1.0F, 0.5F);
        }
        if (step >= plan.size()) {
            plan = null;
            phase = Phase.FILL;
            fillTicks = 0;
            bar = new ServerBossEvent(Component.translatable("entity.soulless.wailing_reaper"), BossEvent.BossBarColor.PURPLE,
                    BossEvent.BossBarOverlay.NOTCHED_10);
            bar.setProgress(0);
            bar.setDarkenScreen(true);
            level.playSound(null, worldPosition, SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 1.5F, 0.5F);
        }
        setChanged();
    }

    // ------------------------------------------------------------------ boss bar + spawn

    private void fill(ServerLevel level) {
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
                boss.moveTo(worldPosition.getX() + 0.5, worldPosition.getY() - 1, worldPosition.getZ() + 3.5, 180, 0);
                boss.setHome(worldPosition);
                level.addFreshEntity(boss);
                boss.playEmerge();
            }
            level.sendParticles(ParticleTypes.SOUL, worldPosition.getX() + 0.5, worldPosition.getY() + 2, worldPosition.getZ() + 3.5, 120, 1, 3, 1, 0.1);
            level.playSound(null, worldPosition, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0F, 0.6F);
            phase = Phase.DONE;
        }
        setChanged();
    }

    @Override
    public void setRemoved() {
        if (bar != null) bar.removeAllPlayers();
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("phase", phase.name());
        tag.putInt("step", step);
        tag.putInt("fill", fillTicks);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        try {
            phase = Phase.valueOf(tag.getString("phase"));
        } catch (IllegalArgumentException e) {
            phase = Phase.IDLE;
        }
        step = tag.getInt("step");
        fillTicks = tag.getInt("fill");
        if (phase == Phase.FILL) {
            // the bar is not saved; recreate it on the next tick by restarting the fill
            phase = Phase.FILL;
            bar = new ServerBossEvent(Component.translatable("entity.soulless.wailing_reaper"), BossEvent.BossBarColor.PURPLE,
                    BossEvent.BossBarOverlay.NOTCHED_10);
        }
    }
}
