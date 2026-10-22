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
    /** SOULLESS_FOX=1: tame a white fox, check following, teleporting, sitting, the bed, healing, saving. */
    public static final boolean FOX = ENABLED && System.getenv("SOULLESS_FOX") != null;
    /** SOULLESS_RENEGADE=1: the fight with the Renegade - every attack, a parry, the loss, the win, the harakiri lesson. */
    public static final boolean RENEGADE = ENABLED && System.getenv("SOULLESS_RENEGADE") != null;
    /** SOULLESS_FORGE=1: Wraith Iron in the ground, the Soul Anvil (screen and forging), the katana's stages and what they unlock. */
    public static final boolean FORGE = ENABLED && System.getenv("SOULLESS_FORGE") != null;
    /** SOULLESS_STRUCT=1: the Drowned Souls arena and the altar shrine built in front of the camera; a seeking eye's flight and fall. */
    public static final boolean STRUCT = ENABLED && System.getenv("SOULLESS_STRUCT") != null;
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
        if (!PREVIEW && !BOSS && !KATANA && !FOX && !RENEGADE && !FORGE && !STRUCT) spawnZombies(p);
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
                    Soulless.LOGGER.info("[autotest] t={} boss state={} frozen={} hp={} pos={} playerHp={}", tick, boss.state(), boss.frozen(), boss.getHealth(),
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
        if (FOX) {
            foxTick(p);
            return;
        }
        if (STRUCT) {
            structTick(p);
            return;
        }
        if (FORGE) {
            forgeTick(p);
            return;
        }
        if (RENEGADE) {
            renegadeTick(p);
            return;
        }
        if (KATANA) {
            // the ritual every cycle: no cooldown, and the health it costs comes back between cycles
            p.getCooldowns().removeCooldown(ModItems.SOUL_KATANA.get());
            if (tick % 40 == 0 && !com.soulless.item.ResonanceRitual.isActive(p)) p.setHealth(p.getMaxHealth());
            if (tick % 20 == 0) Soulless.LOGGER.info("[autotest] katana hp={} blessed={}", p.getHealth(),
                    p.hasEffect(com.soulless.registry.ModEffects.SOUL_RESONANCE.get()));
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


    private static String inv(ServerPlayer p) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (!s.isEmpty()) out.add(s.getCount() + "x" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getPath());
        }
        return out.toString();
    }





    private static boolean pick(ServerPlayer p, String key) {
        boolean ok = com.soulless.npc.dialogue.DialogueManager.chooseText(p, key);
        if (!ok) Soulless.LOGGER.info("[autotest] quest t={} FAIL option not shown: {}", tick, key);
        return ok;
    }

    private static void holdItem(ServerPlayer p, net.minecraft.world.item.Item item) {
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getItem(i).is(item)) {
                p.getInventory().selected = i;
                return;
            }
        }
    }


    private static net.minecraft.core.BlockPos anvilAt;

    private static void forgeLog(String what, Object... args) {
        Object[] all = new Object[args.length + 1];
        all[0] = tick;
        System.arraycopy(args, 0, all, 1, args.length);
        Soulless.LOGGER.info("[autotest] forge t={} " + what, all);
    }

    private static void forgeTick(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        var inv = p.getInventory();
        var dmgAttr = net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE;
        com.soulless.forge.SoulAnvilBlockEntity be = anvilAt == null ? null
                : level.getBlockEntity(anvilAt) instanceof com.soulless.forge.SoulAnvilBlockEntity b ? b : null;
        if (tick == 20) {
            p.setGameMode(GameType.SURVIVAL);
            inv.clearContent();
            anvilAt = p.blockPosition().relative(p.getDirection(), 2);
            level.setBlock(anvilAt, com.soulless.registry.ModBlocks.SOUL_ANVIL.get().defaultBlockState(), 3);
            inv.add(new ItemStack(ModItems.WRAITH_IRON_INGOT.get(), 9));
            inv.add(new ItemStack(net.minecraft.world.item.Items.STICK, 2));
            inv.add(new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE, 1));
        }
        if (tick == 25) {
            // the vessel: chosen on the screen, filled by hand
            com.soulless.network.AnvilActionPacket.run(p, be, com.soulless.network.AnvilActionPacket.CHOOSE, "soul_vessel", 0);
            forgeLog("vessel chosen: recipe={}", be.recipe() == null ? null : be.recipe().id());
            feed(p, be, ModItems.WRAITH_IRON_INGOT.get());
            feed(p, be, net.minecraft.world.item.Items.GLASS_BOTTLE);
        }
        if (tick == 30) {
            var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(anvilAt).inflate(2));
            forgeLog("vessel forged: idle={} dropped={}", be.idle(), drops.stream().map(e -> e.getItem().getItem().toString()).toList());
            drops.forEach(e -> {
                inv.add(e.getItem().copy());
                e.discard();
            });
            // kills: a zombie gives a soul to the vessel, a cow none
            for (int k = 0; k < 25; k++) {
                Zombie z = EntityType.ZOMBIE.create(level);
                z.moveTo(p.getX() + 2, p.getY(), p.getZ(), 0, 0);
                level.addFreshEntity(z);
                z.hurt(p.damageSources().playerAttack(p), 1000);
            }
            var cow = EntityType.COW.create(level);
            cow.moveTo(p.getX() - 2, p.getY(), p.getZ(), 0, 0);
            level.addFreshEntity(cow);
            cow.hurt(p.damageSources().playerAttack(p), 1000);
        }
        if (tick == 35) {
            forgeLog("after 25 zombies and a cow: souls={}", com.soulless.upgrade.SoulVessels.total(p));
            com.soulless.network.AnvilActionPacket.run(p, be, com.soulless.network.AnvilActionPacket.CHOOSE, "soul_katana", 0);
            feed(p, be, ModItems.WRAITH_IRON_INGOT.get());
            forgeLog("katana half laid: ingots {}/6 sticks {}/2 souls {}/20", be.filled(0), be.filled(1), be.soulsPaid());
        }
        if (tick == 40) {
            feed(p, be, net.minecraft.world.item.Items.STICK);
            feed(p, be, ModItems.SOUL_VESSEL.get());
            var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(anvilAt).inflate(2));
            forgeLog("katana forged: idle={} dropped={} souls left={}", be.idle(), drops.stream().map(e -> e.getItem().getItem().toString()).toList(),
                    com.soulless.upgrade.SoulVessels.total(p));
            drops.forEach(e -> {
                if (e.getItem().getItem() instanceof com.soulless.item.SoulKatanaItem) inv.setItem(0, e.getItem().copy());
                e.discard();
            });
            inv.selected = 0;
        }
        if (tick == 45) {
            ItemStack k = p.getMainHandItem();
            com.soulless.item.KatanaParry.tryStart(p);
            forgeLog("fresh katana: invested={} tier={} dmg {} parry raised={}", com.soulless.upgrade.Upgrades.invested(k),
                    com.soulless.upgrade.Upgrades.tier(k), p.getAttributeValue(dmgAttr), com.soulless.item.KatanaParry.started(p) >= 0);
            // laid on the anvil and upgraded, through the screen actions
            be.place(k.copy());
            inv.setItem(0, ItemStack.EMPTY);
            com.soulless.upgrade.SoulVessels.give(p, 1000);
        }
        if (tick == 50) {
            int a = com.soulless.upgrade.SoulVessels.total(p);
            com.soulless.network.AnvilActionPacket.run(p, be, com.soulless.network.AnvilActionPacket.BUY, "", 0);
            com.soulless.network.AnvilActionPacket.run(p, be, com.soulless.network.AnvilActionPacket.BUY, "", 1);
            com.soulless.network.AnvilActionPacket.run(p, be, com.soulless.network.AnvilActionPacket.BUY, "", 0);
            forgeLog("bought 3 tiers: picked={} souls {} -> {} (one vessel holds {})", com.soulless.upgrade.Upgrades.picked(be.held()), a,
                    com.soulless.upgrade.SoulVessels.total(p), com.soulless.item.SoulVesselItem.MAX);
        }
        if (tick == 53) {
            com.soulless.network.AnvilActionPacket.run(p, be, com.soulless.network.AnvilActionPacket.TAKE, "", 0);
            for (int i = 0; i < inv.items.size(); i++) {
                if (inv.items.get(i).getItem() instanceof com.soulless.item.SoulKatanaItem) {
                    ItemStack k = inv.items.get(i);
                    inv.items.set(i, inv.items.get(0));
                    inv.items.set(0, k);
                }
            }
            inv.selected = 0;
        }
        if (tick == 56) {
            ItemStack k = p.getMainHandItem();
            com.soulless.item.KatanaParry.resetCooldown(p);
            com.soulless.item.KatanaParry.tryStart(p);
            forgeLog("upgraded katana: tier={} dmg {} stun ticks {} parry raised={} invested={}", com.soulless.upgrade.Upgrades.tier(k),
                    p.getAttributeValue(dmgAttr), com.soulless.item.KatanaParry.stunTicks(k), com.soulless.item.KatanaParry.started(p) >= 0,
                    com.soulless.upgrade.Upgrades.invested(k));
            // the ward: 10 souls for Resistance IV
        }
        if (tick == 60) {
            int before = com.soulless.upgrade.SoulVessels.total(p);
            for (int i = 0; i < inv.items.size(); i++) {
                if (inv.items.get(i).getItem() instanceof com.soulless.item.SoulVesselItem) {
                    inv.selected = i < 9 ? i : inv.selected;
                    break;
                }
            }
            ItemStack v = p.getMainHandItem();
            if (v.getItem() instanceof com.soulless.item.SoulVesselItem) v.use(level, p, net.minecraft.world.InteractionHand.MAIN_HAND);
            var res = p.getEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE);
            forgeLog("ward: souls {} -> {} resistance amp={} cooldown={}", before, com.soulless.upgrade.SoulVessels.total(p), res == null ? -1 : res.getAmplifier(),
                    p.getCooldowns().isOnCooldown(v.getItem()));
            inv.selected = 0;
        }
        if (tick == 65) {
            // melting the katana down: half of all it cost
            int before = com.soulless.upgrade.SoulVessels.total(p);
            int value = com.soulless.forge.SoulAnvilRecipes.salvageValue(inv.items.get(0));
            com.soulless.network.AnvilActionPacket.run(p, be, com.soulless.network.AnvilActionPacket.SALVAGE_SLOT, "", 0);
            forgeLog("melted: value {} souls {} -> {} slot0={}", value, before, com.soulless.upgrade.SoulVessels.total(p), inv.items.get(0));
        }
        if (tick == 70) {
            com.soulless.network.AnvilActionPacket.run(p, be, com.soulless.network.AnvilActionPacket.CHOOSE, "soul_forged", 0);
            feed(p, be, ModItems.WRAITH_IRON_INGOT.get());
        }
        if (tick == 85) {
            var src = p.createCommandSourceStack().withSuppressedOutput().withPermission(4);
            var cmds = level.getServer().getCommands();
            int bx = p.getBlockX(), bz = p.getBlockZ();
            cmds.performPrefixedCommand(src, "fill " + (bx - 6) + " 150 " + (bz - 6) + " " + (bx + 6) + " 162 " + (bz + 6) + " stone");
            for (int i = 0; i < 6; i++) cmds.performPrefixedCommand(src, "place feature soulless:wraith_iron_ore " + bx + " 156 " + bz);
        }
        if (tick == 90) {
            int stone = 0;
            var pos = new net.minecraft.core.BlockPos.MutableBlockPos();
            for (int x = -8; x < 8; x++) for (int z = -8; z < 8; z++) for (int y = 148; y < 165; y++) {
                pos.set(p.getBlockX() + x, y, p.getBlockZ() + z);
                if (level.getBlockState(pos).is(com.soulless.registry.ModBlocks.WRAITH_IRON_ORE.get())) stone++;
            }
            forgeLog("ore placed by the feature: {}", stone);
        }
        if (tick == 160) Soulless.LOGGER.info("[autotest] forge done");
    }

    private static com.soulless.boss.SeekingEyeEntity eye;

    private static void structTick(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        var all = new net.minecraft.world.level.levelgen.structure.BoundingBox(-30000000, -64, -30000000, 30000000, 320, 30000000);
        if (tick == 20) {
            p.setGameMode(GameType.SPECTATOR);
            var base = p.blockPosition().offset(0, 0, 30);
            com.soulless.world.DrownedSoulsStructure.build(level, base, all);
            com.soulless.boss.SoulAltarFeature.build(level, p.blockPosition().offset(40, 1, 30));
            p.teleportTo(level, base.getX() + 0.5, base.getY() + 12, base.getZ() - 22.5, 0, 25);
            Soulless.LOGGER.info("[autotest] struct built at {}", base);
        }
        if (tick == 80) {
            p.teleportTo(level, p.getX() + 40, p.getY() - 6, p.getZ() + 14, 0, 20);
        }
        if (tick == 120) {
            eye = new com.soulless.boss.SeekingEyeEntity(level, p.getX(), p.getY(), p.getZ() + 3);
            eye.setItem(new ItemStack(ModItems.EYE_OF_LAMENT.get()));
            eye.signalTo(p.blockPosition().offset(200, 0, 200));
            level.addFreshEntity(eye);
            Soulless.LOGGER.info("[autotest] struct eye thrown at {}", eye.position());
        }
        if (tick == 215) {
            var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(40));
            Soulless.LOGGER.info("[autotest] struct eye alive={} drops={}", eye.isAlive(),
                    drops.stream().map(e -> e.getItem().getItem() + " glowing=" + e.isCurrentlyGlowing() + " fireproof=" + e.fireImmune()).toList());
        }
        if (tick == 240) Soulless.LOGGER.info("[autotest] struct done");
    }

    /** A right click on the anvil with this item in hand. */
    private static void feed(ServerPlayer p, com.soulless.forge.SoulAnvilBlockEntity be, net.minecraft.world.item.Item item) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            if (inv.items.get(i).is(item)) {
                be.feed(p, inv.items.get(i));
                return;
            }
        }
    }

    private static int rgPhase, rgPhaseAt, rgLastAttack = -2;

    private static void rgLog(ServerPlayer p, String what) {
        Soulless.LOGGER.info("[autotest] renegade t={} {} | dim={} hp={} flags={}", tick, what, p.level().dimension().location(),
                p.getHealth(), com.soulless.npc.dialogue.DialogueContext.playerFlags(p));
    }

    private static int rgTalkAt = -1;

    private static void renegadeTick(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        var flags = com.soulless.npc.dialogue.DialogueContext.playerFlags(p);
        if (tick == 20) {
            for (String f : new java.util.ArrayList<>(flags.getAllKeys())) flags.remove(f);
            p.setGameMode(GameType.SURVIVAL);
            p.getAbilities().invulnerable = false;
            p.onUpdateAbilities();
            p.getInventory().clearContent();
            p.getInventory().setItem(0, new ItemStack(ModItems.SOUL_KATANA.get()));
            p.getInventory().selected = 0;
            p.setHealth(20);
            com.soulless.item.ResonanceRitual.tryStart(p);
            rgLog(p, "R before the lesson: ritual active=" + com.soulless.item.ResonanceRitual.isActive(p));
            com.soulless.boss.RenegadeEncounter.begin(p);
        }
        var him = com.soulless.boss.RenegadeEncounter.fighting(p);
        // he speaks in the dialogue window: answer a second after it opens
        if (com.soulless.npc.dialogue.DialogueManager.talking(p) && com.soulless.boss.RenegadeEncounter.in(p)) {
            if (rgTalkAt < 0) rgTalkAt = tick;
            if (tick - rgTalkAt >= 20) {
                rgLog(p, "ANSWERED his words in the dialogue window");
                com.soulless.npc.dialogue.DialogueManager.chooseText(p, "");
                rgTalkAt = -1;
            }
        }
        if (tick % 100 == 0) rgLog(p, "state phase=" + rgPhase + " me=" + p.blockPosition().toShortString() + " him=" + (him == null ? "none" : him.blockPosition().toShortString()
                + " hp=" + him.getHealth() + " alive=" + him.isAlive() + " attack=" + him.attackId()));
        if (rgPhase == 0 && him != null && com.soulless.boss.RenegadeEncounter.in(p)) {
            rgPhase = 1;
            rgPhaseAt = tick;
            him.script(0, 1, 2, 3, 4, 5, 6, 0);   // every attack once, then the iai again (parried)
            rgLog(p, "PULLED into the dark at " + p.blockPosition().toShortString() + " below=" + p.level().getBlockState(p.blockPosition().below())
                    + " centre=" + p.level().getBlockState(new net.minecraft.core.BlockPos(0, 99, 0)) + " onGround=" + p.onGround());
        }
        if (rgPhase == 1 && him != null) {
            p.setHealth(20);   // keep the test going through every attack
            p.getFoodData().setFoodLevel(20);
            int a = him.attackId();
            if (a != rgLastAttack) {
                Soulless.LOGGER.info("[autotest] renegade t={} attack {} -> {}", tick, rgLastAttack, a);
                rgLastAttack = a;
            }
            // keep the player facing him at a few blocks for the camera
            if (tick % 5 == 0 && a != 6) {
                net.minecraft.world.phys.Vec3 to = him.position().subtract(p.position());
                if (to.horizontalDistance() > 7 && p.getY() > 90) p.teleportTo(him.getX() - to.normalize().x * 5, him.getY(), him.getZ() - to.normalize().z * 5);
            }
            // the last attack (the iai again): parried right before the draw cut lands
            if (a == 0 && him.attackTick() == 33 && him.hasEffect(com.soulless.registry.ModEffects.STUN.get()) == false && rgParryTest) {
                com.soulless.item.KatanaParry.tryStart(p);
            }
            if (a == 0 && him.attackTick() == 30 && rgIaiCount++ == 1) rgParryTest = true;
            if (rgParryTest && him.hasEffect(com.soulless.registry.ModEffects.STUN.get()) && !rgParried) {
                rgParried = true;
                rgLog(p, "PARRIED the iai: he is stunned");
            }
            if (rgParried && tick - rgPhaseAt > 0 && !rgLowHp) {
                // now lose: a blow that would kill leaves half a heart
                rgLowHp = true;
                rgPhase = 2;
                rgPhaseAt = tick;
            }
            if (tick - rgPhaseAt > 3000) {
                rgLog(p, "FAIL the script did not finish");
                rgPhase = 9;
            }
        }
        if (rgPhase == 2) {
            int at = rgPhaseAt + 40;
            boolean katanaInInv = p.getInventory().contains(new ItemStack(ModItems.SOUL_KATANA.get()));
            if (tick == at && him != null) {
                p.setHealth(2);
                p.hurt(level.damageSources().mobAttack(him), 6);
                rgLog(p, "LOSS blow taken");
            }
            if (tick == at + 10) rgLog(p, "LOSS chained: riding " + (p.getVehicle() == null ? "nothing" : p.getVehicle().getType().toShortString())
                    + " pose=" + p.getPose() + " stunned=" + com.soulless.effect.Stun.isStunned(p));
            if (tick == at + 12 && p.getVehicle() != null) {
                p.stopRiding();
                rgLog(p, "LOSS tried to get up: still riding=" + (p.getVehicle() != null));
            }
            if (tick == at + 36) rgLog(p, "LOSS taken: he holds it=" + (him != null && him.holding()) + " katana in inventory=" + katanaInInv);
            if (tick == at + 60) rgLog(p, "LOSS dropped: he holds it=" + (him != null && him.holding()) + " katana back in inventory=" + katanaInInv);
            if (tick > at + 60 && !com.soulless.boss.RenegadeEncounter.in(p) && com.soulless.boss.RenegadeEncounter.fighting(p) == null) {
                rgLog(p, "LOSS back home: riding=" + (p.getVehicle() != null) + " katana=" + katanaInInv + " dream flag="
                        + flags.getBoolean(com.soulless.boss.RenegadeEncounter.DREAM) + " due=" + flags.getLong(com.soulless.boss.RenegadeEncounter.DUE));
                p.setHealth(20);
                rgPhase = 6;
                rgPhaseAt = tick;
            }
            if (tick > at + 600) {
                rgLog(p, "FAIL the loss scene did not end");
                rgPhase = 9;
            }
        }
        if (rgPhase == 6) {
            // a night later: into bed - he should come into the dream
            if (tick == rgPhaseAt + 20) {
                level.setDayTime(18000);   // night: in the day the game wakes sleepers at once
                net.minecraft.core.BlockPos bed = p.blockPosition();
                level.setBlock(bed, net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), 3);
                p.startSleeping(bed);
                rgLog(p, "SLEEP in bed at " + bed.toShortString() + " sleeping=" + p.isSleeping());
            }
            if (com.soulless.boss.RenegadeEncounter.in(p)) {
                rgLog(p, "DREAM he came into the sleep: in the dark astral");
                rgPhase = 3;
                rgPhaseAt = tick;
            }
            if (tick > rgPhaseAt + 400) {
                rgLog(p, "FAIL he did not come into the dream");
                rgPhase = 9;
            }
        }
        if (rgPhase == 3) p.setHealth(20);   // the rematch is for the win: no losing it by accident
        if (rgPhase == 3 && him != null && com.soulless.boss.RenegadeEncounter.in(p) && tick > rgPhaseAt + 240) {
            him.setHealth(45);
            him.hurt(p.damageSources().playerAttack(p), 20);   // he may slip aside (10%): again next tick
            if (him.defeated()) {
                rgLog(p, "WIN he kneels: defeated=" + him.defeated());
                rgPhase = 4;
                rgPhaseAt = tick;
            }
        }
        if (rgPhase == 4 && tick == rgPhaseAt + 200) {
            rgLog(p, "WIN back home");
            Soulless.LOGGER.info("[autotest] renegade done");
            rgPhase = 5;
        }
    }

    private static boolean rgParryTest, rgParried, rgLowHp;
    private static int rgIaiCount;

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
