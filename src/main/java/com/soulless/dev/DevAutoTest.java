package com.soulless.dev;

import com.soulless.Soulless;
import com.soulless.ability.RendManager;
import com.soulless.ability.RendTimeline;
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
    /** SOULLESS_TUNE_ABILITY=rend loops Soul Rend (with fling/stun/heal logging) instead of Soul Crush. */
    public static final boolean TUNE_REND = TUNE && "rend".equals(System.getenv("SOULLESS_TUNE_ABILITY"));
    public static final int TUNE_CYCLE = TUNE_REND ? 300 : 120;
    /** SOULLESS_CHECK=1: tooltip screenshots + a training dummy hit by a swing, Soul Crush and Soul Rend. */
    public static final boolean CHECK = ENABLED && System.getenv("SOULLESS_CHECK") != null;
    /** SOULLESS_BOSS=1: build the altar, run the ritual, fight the Reaper (player protected), force the ults. */
    public static final boolean BOSS = ENABLED && System.getenv("SOULLESS_BOSS") != null;
    /** SOULLESS_PREVIEW=1: a preview statue of the Reaper photographed from four sides. */
    public static final boolean PREVIEW = ENABLED && System.getenv("SOULLESS_PREVIEW") != null;
    private static net.minecraft.core.BlockPos altarPos;
    /** Open ground away from anything built near spawn. */
    private static final double ARENA_X = 300.5, ARENA_Z = 0.5;

    private static ServerPlayer player;
    private static int bossSeen = -1;
    private static boolean chestLogged;

    @SubscribeEvent
    public static void onPlayerHurt(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (BOSS && event.getEntity() instanceof ServerPlayer) {
            Soulless.LOGGER.info("[autotest] player hit by {} via {} for {}", event.getSource().getMsgId(),
                    event.getSource().getDirectEntity() == null ? "-" : event.getSource().getDirectEntity().getType().getDescriptionId(),
                    event.getAmount());
        }
    }
    private static int tick = -1;

    /** Safety: the scripted scenarios only ever run in the dedicated autotest world, in the overworld. */
    private static boolean inAutotestWorld(ServerPlayer p) {
        return p.server.getWorldData().getLevelName().equals(DevAutoTestClient.WORLD)
                && p.level().dimension() == net.minecraft.world.level.Level.OVERWORLD;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!ENABLED || !(event.getEntity() instanceof ServerPlayer p)) return;
        if (!inAutotestWorld(p)) {
            Soulless.LOGGER.warn("[autotest] refusing to run outside the '{}' world", DevAutoTestClient.WORLD);
            return;
        }
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
        if (!PREVIEW && !BOSS) spawnZombies(p);
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
        if (PREVIEW) {
            ServerLevel level = p.serverLevel();
            double cx = ARENA_X, cz = ARENA_Z + 9;
            if (tick == 20) {
                var r = com.soulless.registry.ModEntities.WAILING_REAPER.get().create(level);
                r.moveTo(cx, p.getY(), cz, 180, 0);
                r.setYBodyRot(180);
                r.setYHeadRot(180);
                r.makePreview(System.getenv().getOrDefault("SOULLESS_PREVIEW_ANIM", "idle"));
                level.addFreshEntity(r);
            }
            // walk around it: front, front-left, side, back
            double[][] views = {{0, -9}, {-7, -7}, {-10, 0}, {0, 10}};
            int v = (tick - 30) / 40;
            if (tick >= 30 && (tick - 30) % 40 == 0 && v < views.length) {
                double px = cx + views[v][0], pz = cz + views[v][1];
                float yaw = (float) (Math.toDegrees(Math.atan2(cz - pz, cx - px)) - 90);
                p.teleportTo(level, px, p.getY(), pz, yaw, -12);
            }
            return;
        }
        if (BOSS) {
            ServerLevel level = p.serverLevel();
            if (tick == 20) {
                level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, p.getBoundingBox().inflate(60), e -> !(e instanceof ServerPlayer))
                        .forEach(net.minecraft.world.entity.Entity::discard);
                p.setGameMode(GameType.SURVIVAL);
                p.teleportTo(level, ARENA_X, p.getY(), ARENA_Z - 2, 0, 5);
                net.minecraft.core.BlockPos base = net.minecraft.core.BlockPos.containing(ARENA_X, p.getY(), ARENA_Z + 9);
                com.soulless.boss.SoulAltarFeature.build(level, base);
                altarPos = base.above();
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 100000, 3, false, false));
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 100000, 3, false, false));
                Soulless.LOGGER.info("[autotest] altar built at {}", altarPos);
            }
            if (tick == 60 && level.getBlockEntity(altarPos) instanceof com.soulless.boss.SoulAltarBlockEntity altar) {
                level.setBlock(altarPos, level.getBlockState(altarPos).setValue(com.soulless.boss.SoulAltarBlock.FILLED, true), 3);
                altar.startRitual();
                Soulless.LOGGER.info("[autotest] ritual started");
            }
            var bosses = level.getEntitiesOfClass(com.soulless.boss.WailingReaperEntity.class, p.getBoundingBox().inflate(60));
            if (!bosses.isEmpty()) {
                var boss = bosses.get(0);
                if (tick % 20 == 0) {
                    Soulless.LOGGER.info("[autotest] t={} boss state={} hp={} pos={} playerHp={}", tick, boss.state(), boss.getHealth(),
                            boss.position(), p.getHealth());
                }
                if (bossSeen < 0) bossSeen = tick;
                int bt = tick - bossSeen;
                if (bt == 700) boss.setHealth(boss.getMaxHealth() * 0.49F);   // force the first ult
                if (bt == 1100) boss.setHealth(boss.getMaxHealth() * 0.14F);  // and the second
                if (bt == 1400) boss.hurt(level.damageSources().playerAttack(p), 99999);
            }
            if (tick % 20 == 0) {
                long heads = level.getEntitiesOfClass(com.soulless.boss.AbyssHeadEntity.class, p.getBoundingBox().inflate(60)).size();
                long bolts = level.getEntitiesOfClass(com.soulless.boss.SoulBoltEntity.class, p.getBoundingBox().inflate(60)).size();
                if (heads + bolts > 0) Soulless.LOGGER.info("[autotest] t={} heads={} bolts={}", tick, heads, bolts);
            }
            if (altarPos != null && tick > 300 && level.getBlockState(altarPos).is(net.minecraft.world.level.block.Blocks.CHEST)
                    && !chestLogged) {
                chestLogged = true;
                Soulless.LOGGER.info("[autotest] reward chest placed, slot13={}",
                        ((net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(altarPos)).getItem(13));
            }
            return;
        }
        if (CHECK) {
            if (tick == 20) {
                p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(60), e -> !(e instanceof ServerPlayer))
                        .forEach(LivingEntity::discard);
                p.teleportTo(p.serverLevel(), ARENA_X, p.getY(), ARENA_Z, 0, 20);
                p.setGameMode(GameType.SURVIVAL);
                // the client may have restored another hotbar slot: put the sword in whichever is selected
                p.getInventory().setItem(p.getInventory().selected, new ItemStack(ModItems.SOUL_FORGED.get()));
                com.soulless.TrainingDummy.spawn(p);
            }
            if (tick == 60) {
                p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(8), com.soulless.TrainingDummy::isDummy)
                        .forEach(d -> {
                            Soulless.LOGGER.info("[autotest] dummy at {} (player {})", d.position(), p.position());
                            p.attack(d);
                        });
            }
            if (tick == 140) slam(p);
            if (tick == 260) {
                RendManager.resetCooldown(p);
                RendManager.tryStart(p);
            }
            if (tick == 520) Soulless.LOGGER.info("[autotest] check done");
            return;
        }
        if (TUNE_REND) {
            int c = tick % TUNE_CYCLE;
            if (c == 0) {
                p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(60), e -> !(e instanceof ServerPlayer))
                        .forEach(LivingEntity::discard);
                p.teleportTo(p.serverLevel(), ARENA_X, p.getY(), ARENA_Z, 0, 10);
                p.getPersistentData().remove("soulless_rend_ready_at");
                p.setGameMode(GameType.SURVIVAL);
                p.getFoodData().setFoodLevel(20);
                p.setHealth(10);
                rendTargets.clear();
                rendTargets.add(place(p, EntityType.ZOMBIE, 0, 3.0));
                rendTargets.add(place(p, EntityType.CHICKEN, -1.8, 2.4));
                rendTargets.add(place(p, EntityType.IRON_GOLEM, 2.2, 3.2));
                rendTargets.add(place(p, EntityType.PIG, -0.8, 4.4));
                RendManager.tryStart(p);
                Soulless.LOGGER.info("[autotest] rend started food={} hp={}", p.getFoodData().getFoodLevel(), p.getHealth());
            }
            if (c == RendTimeline.WINDUP + RendTimeline.HIT_AT + 80) {
                Soulless.LOGGER.info("[autotest] rend result: player hp={} food={}", p.getHealth(), p.getFoodData().getFoodLevel());
                for (LivingEntity t : rendTargets) {
                    double d = Math.sqrt(Math.pow(t.getX() - ARENA_X, 2) + Math.pow(t.getZ() - ARENA_Z, 2));
                    Soulless.LOGGER.info("[autotest]   {} maxHp={} hp={} dist={} stunned={}", t.getType().getDescriptionId(),
                            t.getMaxHealth(), t.getHealth(), String.format("%.1f", d), com.soulless.effect.Stun.isStunned(t));
                }
            }
            return;
        }
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

    private static final java.util.List<LivingEntity> rendTargets = new java.util.ArrayList<>();

    /**
     * A test target at (dx, dz) blocks from the arena centre (+z is in front of the player). It keeps its AI
     * (NoAI mobs ignore velocity, so they could not be flung) but is pinned by heavy slowness and fireproof.
     */
    private static LivingEntity place(ServerPlayer p, EntityType<? extends LivingEntity> type, double dx, double dz) {
        LivingEntity e = type.create(p.serverLevel());
        e.moveTo(ARENA_X + dx, p.getY(), ARENA_Z + dz, 180, 0);
        e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 400, 6, false, false));
        e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 400, 0, false, false));
        if (e instanceof net.minecraft.world.entity.Mob mob) {
            mob.setPersistenceRequired();
        }
        p.serverLevel().addFreshEntity(e);
        return e;
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
