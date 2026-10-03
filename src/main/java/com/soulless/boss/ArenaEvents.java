package com.soulless.boss;

import com.soulless.Soulless;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * A challenger who dies in the Reaper's arena drops everything on the altar instead of where they fell (the
 * arena is about to crumble), and when nobody is left standing the encounter is lost.
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class ArenaEvents {
    /** Players who just died in an arena -> that arena's altar (their drops come right after the death event). */
    private static final Map<UUID, SoulAltarBlockEntity> FALLEN = new WeakHashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        for (SoulAltarBlockEntity altar : SoulAltarBlockEntity.active()) {
            if (altar.isRemoved() || !altar.contains(player)) continue;
            FALLEN.put(player.getUUID(), altar);
            altar.onPlayerDied(player);
            return;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        SoulAltarBlockEntity altar = FALLEN.remove(player.getUUID());
        if (altar == null || altar.isRemoved()) return;
        Vec3 at = altar.dropPoint();
        for (ItemEntity item : event.getDrops()) {
            item.setPos(at.x + (player.getRandom().nextDouble() - 0.5) * 0.6, at.y + 0.2, at.z + (player.getRandom().nextDouble() - 0.5) * 0.6);
            item.setDeltaMovement(0, 0.1, 0);
            item.setExtendedLifetime();   // they wait for the owner to come back
        }
    }

    private ArenaEvents() {}
}
