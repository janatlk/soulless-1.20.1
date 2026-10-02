package com.soulless.dev;

import com.soulless.Soulless;
import com.soulless.ability.SlamManager;
import com.soulless.effect.SoulCurse;
import com.soulless.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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
import java.util.Optional;

/**
 * Dev-only scripted scenario (env SOULLESS_AUTOTEST=1): gives the sword, lines up tough zombies, hits one,
 * then slams three times. With SOULLESS_TUNE=1 it instead loops a hit + slam every {@link #TUNE_CYCLE} ticks
 * forever, for live first-person tuning. Paired with {@code DevAutoTestClient}. Does nothing in normal play.
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class DevAutoTest {
    public static final boolean ENABLED = System.getenv("SOULLESS_AUTOTEST") != null;
    public static final boolean TUNE = ENABLED && System.getenv("SOULLESS_TUNE") != null;
    public static final int TUNE_CYCLE = 120;
    /** Open ground away from anything built near spawn. */
    private static final double ARENA_X = 300.5, ARENA_Z = 0.5;

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
        p.teleportTo(level, ARENA_X, p.getY(), ARENA_Z, 0, 10);
        spawnZombies(p);
        Soulless.LOGGER.info("[autotest] scenario ready (tune={})", TUNE);
    }

    private static void spawnZombies(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        level.getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(40)).forEach(Zombie::discard);
        // tuning: a single target behind the player, so nothing blocks any camera
        int count = TUNE ? 1 : 6;
        for (int i = 0; i < count; i++) {
            double a = TUNE ? -Math.PI / 2 : Math.PI * (0.15 + 0.7 * i / 5.0);
            Zombie z = EntityType.ZOMBIE.create(level);
            if (z == null) continue;
            double r = TUNE ? 3.0 : i % 2 == 0 ? 3.2 : 4.6;
            z.moveTo(ARENA_X + Math.cos(a) * r, p.getY(), ARENA_Z + Math.sin(a) * r, 180, 0);
            z.setNoAi(true);
            z.setPersistenceRequired();
            z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
            z.setHealth(1000);
            level.addFreshEntity(z);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END || player == null || tick < 0) return;
        tick++;
        ServerPlayer p = player;
        if (TUNE) {
            int c = tick % TUNE_CYCLE;
            if (c == 0) {
                // zombies from earlier runs load asynchronously after login; keep only the tuning target
                p.serverLevel().getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(40), z -> z.getZ() > ARENA_Z - 2)
                        .forEach(Zombie::discard);
                p.teleportTo(p.serverLevel(), ARENA_X, p.getY(), ARENA_Z, 0, 10);
                slam(p);
            }
            if (c == 70) hit(p);
            if (tick % (TUNE_CYCLE * 6) == 0) spawnZombies(p);
            return;
        }
        if (tick == 60) hit(p);
        if (tick == 100 || tick == 260 || tick == 420) {
            p.teleportTo(p.serverLevel(), ARENA_X, p.getY(), ARENA_Z, 0, tick == 260 ? 5 : 20);
            slam(p);
        }
        if (tick == 140) {
            p.serverLevel().getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(10)).forEach(z ->
                    Soulless.LOGGER.info("[autotest] zombie {} hp={} cursed={} speed={}", z.getId(), z.getHealth(),
                            SoulCurse.isCursed(z), z.getAttributeValue(Attributes.MOVEMENT_SPEED)));
        }
    }

    private static void slam(ServerPlayer p) {
        p.getCooldowns().removeCooldown(ModItems.SOUL_FORGED.get());
        SlamManager.tryStart(p);
        Soulless.LOGGER.info("[autotest] slam started");
    }

    private static void hit(ServerPlayer p) {
        nearest(p).ifPresent(z -> {
            z.setSecondsOnFire(8);
            p.swing(InteractionHand.MAIN_HAND, true);
            p.attack(z);
            Soulless.LOGGER.info("[autotest] hit {} fireTicks={} cursed={}", z.getId(), z.getRemainingFireTicks(), SoulCurse.isCursed(z));
        });
    }

    private static Optional<LivingEntity> nearest(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(8)).stream()
                .map(z -> (LivingEntity) z)
                .min(Comparator.comparingDouble(z -> z.distanceToSqr(p)));
    }

    private DevAutoTest() {}
}
