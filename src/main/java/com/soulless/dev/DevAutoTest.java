package com.soulless.dev;

import com.soulless.Soulless;
import com.soulless.ability.SlamManager;
import com.soulless.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;

/**
 * Dev-only scripted scenario (env SOULLESS_AUTOTEST=1): gives the sword, lines up tough zombies,
 * hits one, then slams twice. Paired with {@code DevAutoTestClient}, which frames the camera and
 * takes screenshots. Does nothing in normal play.
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class DevAutoTest {
    public static final boolean ENABLED = System.getenv("SOULLESS_AUTOTEST") != null;

    private static ServerPlayer player;
    private static int tick = -1;

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!ENABLED || !(event.getEntity() instanceof ServerPlayer p)) return;
        player = p;
        tick = 0;
        ServerLevel level = p.serverLevel();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        level.setDayTime(6000);
        p.setGameMode(GameType.CREATIVE);
        p.getInventory().clearContent();
        p.getInventory().setItem(0, new ItemStack(ModItems.SOUL_FORGED.get()));
        p.getInventory().selected = 0;
        p.teleportTo(level, 0.5, p.getY(), 0.5, 0, 10);
        level.getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(40)).forEach(z -> z.discard());
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * (0.15 + 0.7 * i / 5.0);
            Zombie z = EntityType.ZOMBIE.create(level);
            if (z == null) continue;
            double r = i % 2 == 0 ? 3.2 : 4.6;
            z.moveTo(0.5 + Math.cos(a) * r, p.getY(), 0.5 + Math.sin(a) * r, 180, 0);
            z.setNoAi(true);
            z.setPersistenceRequired();
            z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(300);
            z.setHealth(300);
            level.addFreshEntity(z);
        }
        Soulless.LOGGER.info("[autotest] scenario ready");
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END || player == null || tick < 0) return;
        tick++;
        ServerPlayer p = player;
        if (tick == 60) {
            // a normal hit -> Soul Curse on the nearest zombie (it was burning in daylight)
            nearest(p).ifPresent(z -> {
                z.setSecondsOnFire(8);
                p.attack(z);
                Soulless.LOGGER.info("[autotest] hit {} fireTicks={} cursed={}", z.getId(), z.getRemainingFireTicks(),
                        com.soulless.effect.SoulCurse.isCursed(z));
            });
        }
        if (tick == 100 || tick == 290) {
            p.getCooldowns().removeCooldown(ModItems.SOUL_FORGED.get());
            SlamManager.tryStart(p);
            Soulless.LOGGER.info("[autotest] slam started");
        }
        if (tick == 140) {
            p.serverLevel().getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(10)).forEach(z ->
                    Soulless.LOGGER.info("[autotest] zombie {} hp={} cursed={} speed={}", z.getId(), z.getHealth(),
                            com.soulless.effect.SoulCurse.isCursed(z), z.getAttributeValue(Attributes.MOVEMENT_SPEED)));
        }
    }

    private static java.util.Optional<LivingEntity> nearest(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(8)).stream()
                .map(z -> (LivingEntity) z)
                .min(Comparator.comparingDouble(z -> z.distanceToSqr(p)));
    }

    private DevAutoTest() {}
}
