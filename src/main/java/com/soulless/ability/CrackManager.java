package com.soulless.ability;

import com.soulless.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Visual-only ground cracks. Uses the vanilla block-breaking overlay ({@code destroyBlockProgress})
 * with fake negative breaker ids, so no block is ever harmed. Cracks spread outward as a ring,
 * hold, then heal stage by stage.
 */
public final class CrackManager {
    private static final int HOLD_TICKS = 70;
    private static final int HEAL_TICKS_PER_STAGE = 4;
    private static final List<Crack> CRACKS = new ArrayList<>();
    private static int nextId = -0x50_0000;

    private static final class Crack {
        final ServerLevel level;
        final BlockPos pos;
        final int breakerId;
        final long start;
        final int peak;
        final int hold;
        /** Opened by a wielder wearing the Soulsmith's Ring: the cracks glow gold. */
        final boolean gold;
        int shown = -1;

        Crack(ServerLevel level, BlockPos pos, int breakerId, long start, int peak, int hold, boolean gold) {
            this.gold = gold;
            this.level = level;
            this.pos = pos;
            this.breakerId = breakerId;
            this.start = start;
            this.peak = peak;
            this.hold = hold;
        }
    }

    public static void start(ServerLevel level, BlockPos center, float radius, boolean gold) {
        RandomSource rnd = level.getRandom();
        long now = level.getGameTime();
        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > radius) continue;
                // ragged edge: outer blocks crack less often
                if (d > radius * 0.6 && rnd.nextFloat() < (d / radius) * 0.55) continue;
                BlockPos surface = findSurface(level, center.offset(dx, 0, dz));
                if (surface == null) continue;
                int peak = Math.max(2, Math.min(9, 9 - (int) (d * 1.15) + rnd.nextInt(3) - 1));
                long begin = now + (long) (d * 1.6);
                CRACKS.add(new Crack(level, surface, nextId--, begin, peak, HOLD_TICKS + rnd.nextInt(30), gold));
                if (nextId < -0x7F_0000) nextId = -0x50_0000;
            }
        }
    }

    /** Topmost solid block within a few blocks above/below {@code around}, or null. */
    @Nullable
    public static BlockPos findSurface(ServerLevel level, BlockPos around) {
        for (int dy = 2; dy >= -4; dy--) {
            BlockPos p = around.offset(0, dy, 0);
            BlockState state = level.getBlockState(p);
            if (!state.isAir() && state.isSolidRender(level, p) && level.getBlockState(p.above()).canBeReplaced()) {
                return p;
            }
        }
        return null;
    }

    static void tick(MinecraftServer server) {
        Iterator<Crack> it = CRACKS.iterator();
        while (it.hasNext()) {
            Crack c = it.next();
            long age = c.level.getGameTime() - c.start;
            if (age < 0) continue;
            int stage;
            if (age < 4) {
                stage = (int) (c.peak * (age + 1) / 4);
            } else if (age < 4 + c.hold) {
                stage = c.peak;
            } else {
                stage = c.peak - (int) ((age - 4 - c.hold) / HEAL_TICKS_PER_STAGE);
            }
            if (stage != c.shown) {
                if (c.shown < 0 && stage >= 0) {
                    burst(c);
                }
                c.level.destroyBlockProgress(c.breakerId, c.pos, Math.max(stage, -1));
                c.shown = stage;
            }
            if (stage < 0) {
                c.level.destroyBlockProgress(c.breakerId, c.pos, -1);
                it.remove();
            } else if (age > 4 && age < 4 + c.hold && c.level.getRandom().nextInt(40) == 0) {
                // the fissure still smoulders with soul fire
                c.level.sendParticles(com.soulless.item.SoulRing.ember(c.gold), c.pos.getX() + 0.5, c.pos.getY() + 1.02, c.pos.getZ() + 0.5,
                        1, 0.3, 0, 0.3, 0.01);
            }
        }
    }

    private static void burst(Crack c) {
        BlockState state = c.level.getBlockState(c.pos);
        c.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                c.pos.getX() + 0.5, c.pos.getY() + 1.05, c.pos.getZ() + 0.5, 4, 0.3, 0.05, 0.3, 0.1);
    }

    private CrackManager() {}
}
