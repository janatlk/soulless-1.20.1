package com.soulless.boss;

import com.soulless.Soulless;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Fire the Reaper sets that puts itself out after a while (fire on the arena's soul soil would otherwise burn
 * forever).
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class TempFire {
    private record Flame(ServerLevel level, BlockPos pos, long until) {}

    private static final List<Flame> FLAMES = new ArrayList<>();

    /** Lights fire on {@code pos} (fire, or soul fire on soul blocks) if there is room, for {@code ticks}. */
    public static void light(ServerLevel level, BlockPos pos, int ticks) {
        if (!level.getBlockState(pos).isAir()) return;
        BlockState fire = BaseFireBlock.getState(level, pos);
        if (!fire.canSurvive(level, pos)) return;
        level.setBlock(pos, fire, 11);
        FLAMES.add(new Flame(level, pos, level.getGameTime() + ticks));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || FLAMES.isEmpty()) return;
        Iterator<Flame> it = FLAMES.iterator();
        while (it.hasNext()) {
            Flame f = it.next();
            if (f.level.getGameTime() < f.until) continue;
            BlockState s = f.level.getBlockState(f.pos);
            if (s.is(Blocks.FIRE) || s.is(Blocks.SOUL_FIRE)) f.level.setBlock(f.pos, Blocks.AIR.defaultBlockState(), 11);
            it.remove();
        }
    }

    private TempFire() {}
}
