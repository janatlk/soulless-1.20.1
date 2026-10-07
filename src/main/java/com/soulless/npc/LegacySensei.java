package com.soulless.npc;

import com.soulless.Soulless;
import com.soulless.registry.ModEntities;
import com.soulless.registry.ModVillagers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * The sensei used to be a villager with the soulless:sensei profession. Worlds made back then still have those:
 * each one met is swapped (on the next tick - not while its chunk is still loading) for a {@link SenseiEntity} in
 * the same place (the old trades are gone: the sensei only talks now). Zombie villagers, which pick a random profession
 * from all registered ones, lose this one at once (blank profession textures cover the moment before).
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class LegacySensei {
    private static final List<Villager> PENDING = new ArrayList<>();

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        // zombie villagers pick a random profession from all registered ones - never this one (it has no textures)
        if (event.getEntity() instanceof net.minecraft.world.entity.monster.ZombieVillager zv
                && zv.getVillagerData().getProfession() == ModVillagers.SENSEI.get()) {
            zv.setVillagerData(zv.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.NONE));
            return;
        }
        if (!event.getLevel().isClientSide && event.getEntity() instanceof Villager v
                && v.getVillagerData().getProfession() == ModVillagers.SENSEI.get()) {
            PENDING.add(v);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        List<Villager> todo = new ArrayList<>(PENDING);
        PENDING.clear();
        for (Villager v : todo) {
            if (v.isRemoved() || !(v.level() instanceof ServerLevel level)) continue;
            SenseiEntity sensei = ModEntities.SENSEI.get().create(level);
            if (sensei == null) continue;
            sensei.moveTo(v.getX(), v.getY(), v.getZ(), v.getYRot(), v.getXRot());
            sensei.setHome(v.blockPosition());
            if (v.hasCustomName()) sensei.setCustomName(v.getCustomName());
            v.discard();
            level.addFreshEntity(sensei);
            Soulless.LOGGER.info("Converted the old sensei villager at {} into the sensei character", v.blockPosition());
        }
    }

    private LegacySensei() {}
}
