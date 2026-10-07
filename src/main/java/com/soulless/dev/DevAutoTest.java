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
    /** SOULLESS_KATANA=1: the katana in hand, two cuts every cycle, filmed from four views (live pose tuning). */
    public static final boolean KATANA = ENABLED && System.getenv("SOULLESS_KATANA") != null;
    /** SOULLESS_DOJO=1: build the sensei's dojo, photograph it from outside and in, trade with the sensei. */
    public static final boolean DOJO = ENABLED && System.getenv("SOULLESS_DOJO") != null;
    /** SOULLESS_FOX=1: tame a white fox, check following, teleporting, sitting, the bed, healing, saving. */
    public static final boolean FOX = ENABLED && System.getenv("SOULLESS_FOX") != null;
    /** SOULLESS_ASTRAL=1: the dream - sleeping with the fox, Kitsune, the oath and the reforging, the refusal. */
    public static final boolean ASTRAL = ENABLED && System.getenv("SOULLESS_ASTRAL") != null;
    private static net.minecraft.core.BlockPos dojo;
    private static net.minecraft.core.BlockPos altarPos;
    /** Open ground away from anything built near spawn. */
    private static final double ARENA_X = 300.5, ARENA_Z = 0.5;

    private static ServerPlayer player;
    private static int bossSeen = -1;
    private static boolean chestLogged;

    @SubscribeEvent
    public static void onDummyHurt(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (KATANA && event.getEntity() instanceof Zombie z) {
            Soulless.LOGGER.info("[autotest] katana t={} dummy at ({}, {}) took {}", tick, String.format("%.1f", z.getX() - ARENA_X),
                    String.format("%.1f", z.getZ() - ARENA_Z), event.getAmount());
        }
    }

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
        p.getInventory().setItem(0, new ItemStack(KATANA ? ModItems.SOUL_KATANA.get() : ModItems.SOUL_FORGED.get()));
        p.getInventory().selected = 0;
        p.teleportTo(level, ARENA_X, p.getY(), ARENA_Z, 0, 10);
        if (!PREVIEW && !BOSS && !KATANA && !DOJO && !FOX && !ASTRAL) spawnZombies(p);
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
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);   // no sword particles in the shots
            }
            // walk around it: front, front-left, side, back
            double[][] views = {{0, -9}, {-7, -7}, {-10, 0}, {0, 10}};
            int v = (tick - 30) / 40;
            if (DevAutoTestClient.PREVIEW_STRIP) {   // filming one attack: stay at the front-left three-quarter view
                if (tick == 30) {
                    double px = cx - 4, pz = cz - 12;
                    float yaw = (float) (Math.toDegrees(Math.atan2(cz - pz, cx - px)) - 90);
                    p.teleportTo(level, px, p.getY(), pz, yaw, -10);
                }
                return;
            }
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
        if (DOJO) {
            dojoTick(p);
            return;
        }
        if (FOX) {
            foxTick(p);
            return;
        }
        if (ASTRAL) {
            astralTick(p);
            return;
        }
        if (KATANA) {
            // the ritual every cycle: no cooldown, and the health it costs comes back between cycles
            p.getCooldowns().removeCooldown(ModItems.SOUL_KATANA.get());
            if (tick % 40 == 0 && !com.soulless.item.KitsuneRitual.isActive(p)) p.setHealth(p.getMaxHealth());
            if (tick % 20 == 0) Soulless.LOGGER.info("[autotest] katana hp={} blessed={}", p.getHealth(),
                    p.hasEffect(com.soulless.registry.ModEffects.KITSUNE_BLESSING.get()));
            if (tick == 20) {
                p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(60), e -> !(e instanceof ServerPlayer))
                        .forEach(LivingEntity::discard);
                p.setGameMode(GameType.SURVIVAL);
                p.teleportTo(p.serverLevel(), ARENA_X, p.getY(), ARENA_Z, 0, 0);
                p.getInventory().setItem(p.getInventory().selected, new ItemStack(ModItems.SOUL_KATANA.get()));
                // dummies in front, beside it, and behind the player: cuts hit the first two, the thrust only the
                // one ahead, nothing reaches the one behind
                for (double[] at : new double[][]{{0, 2.6}, {1.6, 2.0}, {0, -2.2}}) {
                    Zombie z = EntityType.ZOMBIE.create(p.serverLevel());
                    z.moveTo(ARENA_X + at[0], p.getY(), ARENA_Z + at[1], 180, 0);
                    z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET));
                    z.setNoAi(true);
                    z.setSilent(true);
                    z.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 1_000_000, 0, false, false));
                    z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
                    z.setHealth(1000);
                    p.serverLevel().addFreshEntity(z);
                }
            }
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

    private static void tp(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        p.getAbilities().flying = true;   // hold the camera where it is put
        p.onUpdateAbilities();
        p.teleportTo(p.serverLevel(), dojo.getX() + x, dojo.getY() + y, dojo.getZ() + z, yaw, pitch);
    }

    private static void dojoTick(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        if (tick == 20) {
            p.getInventory().clearContent();
            p.setGameMode(GameType.CREATIVE);
            dojo = net.minecraft.core.BlockPos.containing(ARENA_X - 18, p.getY(), ARENA_Z + 6);
            com.soulless.world.SenseiDojoPiece.buildAt(level, dojo);
            level.setDayTime(5000);
            Soulless.LOGGER.info("[autotest] dojo built at {}", dojo);
        }
        if (tick == 30) tp(p, 13.5, 7, 37, 180, 8);      // the front
        if (tick == 80) tp(p, 16, 34, 48, 180, 42);      // from above, the whole layout
        if (tick == 130) tp(p, -7, 8, 35, 220, 10);      // three-quarter from the south-west
        if (tick == 180) tp(p, 18.5, 2, 15.5, 135, 8);   // inside, toward the tokonoma
        if (tick == 230) {
            var sensei = sensei(p);
            if (sensei != null) {
                net.minecraft.world.phys.Vec3 s = sensei.position();
                p.teleportTo(level, s.x, s.y, s.z + 1.8, 180, 10);
                sensei.setYRot(0);
                sensei.setYHeadRot(0);
                sensei.setNoAi(true);
            }
        }
        if (tick == 345) {
            var sensei = sensei(p);
            if (sensei != null) {
                Soulless.LOGGER.info("[autotest] sensei flags: {}, player flags: {}", sensei.flags(),
                        com.soulless.npc.dialogue.DialogueContext.playerFlags(p));
            }
        }
    }

    @org.jetbrains.annotations.Nullable
    private static com.soulless.npc.SenseiEntity sensei(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(com.soulless.npc.SenseiEntity.class, p.getBoundingBox().inflate(40))
                .stream().findFirst().orElse(null);
    }

    private static net.minecraft.world.entity.animal.Fox wildFox, redFox;
    private static net.minecraft.core.BlockPos foxBed;
    private static net.minecraft.world.phys.Vec3 sitAt;
    private static net.minecraft.world.entity.monster.Zombie foxGuardTest;

    private static void foxLog(String what, Object... args) {
        Soulless.LOGGER.info("[autotest] fox t=" + tick + " " + what, args);
    }

    @org.jetbrains.annotations.Nullable
    private static com.soulless.pet.TamedFox pet(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(com.soulless.pet.TamedFox.class, p.getBoundingBox().inflate(80))
                .stream().findFirst().orElse(null);
    }

    private static void hold(ServerPlayer p, ItemStack stack) {
        p.getInventory().setItem(0, stack);
        p.getInventory().selected = 0;
    }

    private static void foxTick(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        double x = ARENA_X, z = ARENA_Z, y = p.getY();
        var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
        var berries = net.minecraft.world.item.Items.SWEET_BERRIES;
        if (tick == 20) {
            level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, p.getBoundingBox().inflate(80),
                    e -> !(e instanceof ServerPlayer)).forEach(net.minecraft.world.entity.Entity::discard);
            p.setGameMode(GameType.SURVIVAL);
            p.getAbilities().invulnerable = true;
            wildFox = net.minecraft.world.entity.EntityType.FOX.create(level);
            wildFox.moveTo(x, y, z + 3, 180, 0);
            wildFox.setVariant(net.minecraft.world.entity.animal.Fox.Type.SNOW);
            level.addFreshEntity(wildFox);
            redFox = net.minecraft.world.entity.EntityType.FOX.create(level);
            redFox.moveTo(x + 3, y, z + 3, 180, 0);
            redFox.setVariant(net.minecraft.world.entity.animal.Fox.Type.RED);
            level.addFreshEntity(redFox);
        }
        if (tick >= 30 && tick < 70 && tick % 2 == 0 && pet(p) == null && wildFox.isAlive()) {
            hold(p, new ItemStack(berries, 64));
            p.interactOn(wildFox, hand);
            foxLog("berry -> tamed={} berries left={}", pet(p) != null, p.getMainHandItem().getCount());
        }
        if (tick == 72) {
            hold(p, new ItemStack(berries, 64));
            for (int i = 0; i < 10; i++) p.interactOn(redFox, hand);
            foxLog("RED fox after 10 berries: class={} alive={} berries left={}", redFox.getClass().getSimpleName(), redFox.isAlive(),
                    p.getMainHandItem().getCount());
            redFox.discard();
            var f = pet(p);
            foxLog("PET {} variant={} owner={} pickUp={} hp={}/{} targets={}", f != null, f == null ? null : f.getVariant(),
                    f != null && f.isOwner(p), f != null && f.canPickUpLoot(), f == null ? 0 : f.getHealth(), f == null ? 0 : f.getMaxHealth(),
                    f == null ? -1 : f.targetSelector.getAvailableGoals().size());
            if (f != null) {
                level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, f.getX(), f.getY(), f.getZ(),
                        new ItemStack(net.minecraft.world.item.Items.CHICKEN)));
            }
        }
        var f = pet(p);
        if (f == null) {
            if (tick == 100) foxLog("FAIL no pet");
            return;
        }
        if (tick == 110) {
            int items = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, f.getBoundingBox().inflate(4)).size();
            foxLog("PICKUP chicken still on the ground={} mouth={}", items, f.getMainHandItem());
            level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(40))
                    .forEach(net.minecraft.world.entity.Entity::discard);
            hold(p, ItemStack.EMPTY);
            p.teleportTo(level, x, y, z + 12, 0, 20);
        }
        if (tick == 170) foxLog("FOLLOW dist after walk-off={}", String.format("%.1f", f.distanceTo(p)));
        if (tick == 175) p.teleportTo(level, x + 40, y, z + 12, 0, 20);
        if (tick == 225) foxLog("TELEPORT dist={}", String.format("%.1f", f.distanceTo(p)));
        if (tick == 230) {
            p.interactOn(f, hand);
            sitAt = f.position();
        }
        if (tick == 236) p.teleportTo(level, p.getX() + 9, y, p.getZ(), 0, 20);
        if (tick == 300) {
            foxLog("SIT sitting={} moved={}", f.isSitting(), String.format("%.2f", f.position().distanceTo(sitAt)));
            p.teleportTo(level, f.getX() + 1.5, y, f.getZ(), 90, 20);
        }
        if (tick == 305) {
            p.interactOn(f, hand);
            foxLog("STAND sitting after toggle={}", f.isSitting());
            // a bed 4 blocks from the owner
            foxBed = net.minecraft.core.BlockPos.containing(p.getX() - 4, y, p.getZ() + 2);
            var dir = net.minecraft.core.Direction.SOUTH;
            var bed = net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.BedBlock.FACING, dir);
            level.setBlock(foxBed, bed.setValue(net.minecraft.world.level.block.BedBlock.PART,
                    net.minecraft.world.level.block.state.properties.BedPart.FOOT), 3);
            level.setBlock(foxBed.relative(dir), bed.setValue(net.minecraft.world.level.block.BedBlock.PART,
                    net.minecraft.world.level.block.state.properties.BedPart.HEAD), 3);
        }
        if (tick > 305 && tick < 460 && tick % 20 == 0) {
            foxLog("BED lying={} at={} y={} bed={}", f.isSleeping(), f.blockPosition(), String.format("%.3f", f.getY()), foxBed);
        }
        if (tick == 470) p.teleportTo(level, p.getX() + 25, y, p.getZ(), 0, 20);
        if (tick == 540) foxLog("WAKE lying={} dist={}", f.isSleeping(), String.format("%.1f", f.distanceTo(p)));
        if (tick == 545) {
            boolean hurt = f.hurt(p.damageSources().playerAttack(p), 5);
            f.setHealth(10);
            hold(p, new ItemStack(berries, 5));
            p.interactOn(f, hand);
            foxLog("OWNER HIT hurt={} | HEAL hp={} berries={} sitting={}", hurt, f.getHealth(), p.getMainHandItem().getCount(), f.isSitting());
            var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
            boolean hurtByOther = f.hurt(level.damageSources().mobAttack(zombie), 2);
            foxLog("OTHER HIT hurt={} hp={}", hurtByOther, f.getHealth());
        }
        if (tick == 550) {
            var tag = new net.minecraft.nbt.CompoundTag();
            f.saveWithoutId(tag);
            var copy = com.soulless.registry.ModEntities.TAMED_FOX.get().create(level);
            copy.load(tag);
            foxLog("SAVE owner={} variant={} targets={} pickUp={} sitTag={}", copy.isOwner(p), copy.getVariant(),
                    copy.targetSelector.getAvailableGoals().size(), copy.canPickUpLoot(), tag.getBoolean("OrderedToSit"));
            copy.discard();
        }
        if (tick == 556) {
            // guarding and fox sense: a zombie hurts the owner and hunts them
            var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
            zombie.moveTo(p.getX() + 6, p.getY(), p.getZ(), 0, 0);
            level.addFreshEntity(zombie);
            zombie.setTarget(p);
            p.setLastHurtByMob(zombie);
            foxGuardTest = zombie;
        }
        if (tick == 600 && foxGuardTest != null) {
            foxLog("GUARD fox target={} zombie glowing={} zombie hp={}", f.getTarget() == foxGuardTest, foxGuardTest.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING),
                    foxGuardTest.getHealth());
            foxGuardTest.discard();
        }
        if (tick == 610) {
            boolean ownerHurts = f.hurt(p.damageSources().playerAttack(p), 3);
            foxLog("KILLABLE owner hit hurts={} hp={}", ownerHurts, f.getHealth());
            foxLog("done");
        }
    }

    private static net.minecraft.core.BlockPos astralBed;
    private static int astralIn = -1, oathAt = -1;

    private static String inv(ServerPlayer p) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (!s.isEmpty()) out.add(s.getCount() + "x" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getPath());
        }
        return out.toString();
    }

    private static void astralLog(ServerPlayer p, String what) {
        Soulless.LOGGER.info("[autotest] astral t={} {} | dim={} pos={} inv={} flags={} time={}", tick, what, p.level().dimension().location(),
                p.blockPosition().toShortString(), inv(p), com.soulless.npc.dialogue.DialogueContext.playerFlags(p),
                p.server.overworld().getDayTime() % 24000);
    }

    private static void astralTick(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        double x = ARENA_X, z = ARENA_Z, y = p.getY();
        var flags = com.soulless.npc.dialogue.DialogueContext.playerFlags(p);
        if (tick == 20) {
            level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, p.getBoundingBox().inflate(80),
                    e -> !(e instanceof ServerPlayer)).forEach(net.minecraft.world.entity.Entity::discard);
            flags.remove(com.soulless.item.KatanaOath.SWORN);
            flags.remove(com.soulless.item.KatanaOath.REFUSED);
            flags.remove(com.soulless.item.KatanaOath.FREED);
            p.setGameMode(GameType.SURVIVAL);
            p.getAbilities().invulnerable = true;
            p.onUpdateAbilities();
            level.setDayTime(14000);
            p.getInventory().clearContent();
            p.getInventory().setItem(0, new ItemStack(net.minecraft.world.item.Items.BREAD, 5));
            p.getInventory().setItem(5, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 3));
            // a bed under a stone roof, the player beside it
            astralBed = net.minecraft.core.BlockPos.containing(x + 3, y, z);
            var dir = net.minecraft.core.Direction.SOUTH;
            var bed = net.minecraft.world.level.block.Blocks.WHITE_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, dir);
            level.setBlock(astralBed, bed.setValue(net.minecraft.world.level.block.BedBlock.PART,
                    net.minecraft.world.level.block.state.properties.BedPart.FOOT), 3);
            level.setBlock(astralBed.relative(dir), bed.setValue(net.minecraft.world.level.block.BedBlock.PART,
                    net.minecraft.world.level.block.state.properties.BedPart.HEAD), 3);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 3; dz++) {
                level.setBlock(astralBed.offset(dx, 3, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
            }
            p.teleportTo(level, x + 1, y, z + 1, 90, 10);
            var fox = com.soulless.registry.ModEntities.TAMED_FOX.get().create(level);
            fox.moveTo(x + 1, y, z - 1, 0, 0);
            fox.tame(p);
            level.addFreshEntity(fox);
        }
        if (tick == 120) {
            var f = level.getEntitiesOfClass(com.soulless.pet.TamedFox.class, p.getBoundingBox().inflate(10)).stream().findFirst().orElse(null);
            astralLog(p, "fox lying=" + (f != null && f.isSleeping()) + " fox at " + (f == null ? null : f.blockPosition().toShortString()));
            var r = p.startSleepInBed(astralBed.relative(net.minecraft.core.Direction.SOUTH));
            astralLog(p, "sleep #1 (no blade) -> " + r.left().map(Object::toString).orElse("ok"));
        }
        if (tick == 200) {
            astralLog(p, "after sleep #1 sleeping=" + p.isSleeping() + " timer=" + p.getSleepTimer());
            if (p.isSleeping()) p.stopSleepInBed(true, true);
            level.setDayTime(14000);
            p.getInventory().setItem(3, new ItemStack(com.soulless.registry.ModItems.BROKEN_KATANA.get()));
        }
        if (tick == 210) {
            var r = p.startSleepInBed(astralBed.relative(net.minecraft.core.Direction.SOUTH));
            astralLog(p, "sleep #2 (with blade) -> " + r.left().map(Object::toString).orElse("ok"));
        }
        if (tick == 300) astralLog(p, "DREAM");
        if (com.soulless.world.Astral.in(p) && astralIn < 0) astralIn = tick;
        if (astralIn >= 0 && tick == astralIn + 5 && com.soulless.world.Astral.in(p) && oathAt < 0) {
            p.teleportTo(p.serverLevel(), 0.5, 100, 4.5, 180, 14);   // Kitsune in full view
        }
        if (astralIn >= 0 && tick == astralIn + 70 && com.soulless.world.Astral.in(p) && oathAt < 0) p.teleportTo(0.5, 100, -0.2);
        if (oathAt < 0 && tick > 300 && !com.soulless.world.Astral.in(p) && com.soulless.item.KatanaOath.sworn(p)) oathAt = tick + 20;
        if (oathAt < 0 && tick == 1000) astralLog(p, "FAIL the oath was never given");
        if (tick == oathAt) {
            astralLog(p, "WOKE");
            // the oath: an innocent cow takes nothing, the blow turns back, the blade loses 50
            var cow = net.minecraft.world.entity.EntityType.COW.create(level);
            cow.moveTo(p.getX() + 2, p.getY(), p.getZ(), 0, 0);
            level.addFreshEntity(cow);
            p.getAbilities().invulnerable = false;
            p.onUpdateAbilities();
            p.setHealth(20);
            ItemStack katana = p.getInventory().getItem(3);
            p.getInventory().selected = 3;
            int before = katana.getDamageValue();
            boolean side = com.soulless.item.KatanaOath.stops(p, cow, false, 8);
            float hpSide = p.getHealth();
            boolean main = com.soulless.item.KatanaOath.stops(p, cow, true, 8);
            Soulless.LOGGER.info("[autotest] astral OATH held={} side stopped={} hp after side={} | main stopped={} hp after main={} cow hp={} wear {} -> {}",
                    p.getMainHandItem().getItem(), side, hpSide, main, p.getHealth(), cow.getHealth(), before, p.getMainHandItem().getDamageValue());
            var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
            Soulless.LOGGER.info("[autotest] astral OATH zombie stopped={}", com.soulless.item.KatanaOath.stops(p, zombie, true, 8));
            flags.putBoolean(com.soulless.item.KatanaOath.FREED, true);
            p.setHealth(20);
            boolean freedStop = com.soulless.item.KatanaOath.stops(p, cow, true, 8);
            Soulless.LOGGER.info("[autotest] astral OATH freed: stopped={} hp={} wear={}", freedStop, p.getHealth(), p.getMainHandItem().getDamageValue());
            // worn to nothing: it breaks in two instead of vanishing
            p.getMainHandItem().setDamageValue(p.getMainHandItem().getMaxDamage() - 3);
            com.soulless.item.KatanaOath.wear(p, 5);
            Soulless.LOGGER.info("[autotest] astral WEAR to nothing -> {}", p.getMainHandItem().getItem());
            p.getAbilities().invulnerable = true;
            p.onUpdateAbilities();
            cow.discard();
        }
        if (oathAt > 0 && tick == oathAt + 20) {
            // the refusal, driven from the server
            flags.remove(com.soulless.item.KatanaOath.SWORN);
            flags.remove(com.soulless.item.KatanaOath.FREED);
            com.soulless.world.Astral.enter(p, null);
        }
        if (oathAt > 0 && tick == oathAt + 40) {
            var k = p.serverLevel().getEntitiesOfClass(com.soulless.npc.KitsuneEntity.class, p.getBoundingBox().inflate(40)).stream().findFirst().orElse(null);
            if (k == null) {
                astralLog(p, "FAIL no kitsune");
                return;
            }
            p.teleportTo(0.5, 100, -0.2);
            com.soulless.npc.dialogue.DialogueManager.open(p, k);
            int[] path = {0, 0, 1, 0, 1, 0};   // blade, mend, "to be stronger", listen, refuse, refuse for good
            for (int c : path) com.soulless.npc.dialogue.DialogueManager.choose(p, c);
            astralLog(p, "REFUSED (still dreaming, before 'wake')");
            com.soulless.npc.dialogue.DialogueManager.choose(p, 0);   // wake
            astralLog(p, "REFUSED woke");
        }
        if (oathAt > 0 && tick == oathAt + 60) Soulless.LOGGER.info("[autotest] astral done");
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
