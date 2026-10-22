package com.soulless;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * {@code /soulless arena [size]}: a balance check in real combat. A ring of mixed mobs is spawned around the player
 * (zombies, skeletons, spiders, a vindicator, a blaze; more with a bigger size), all aware of them. When the wave
 * is cleared, the player dies, or {@code /soulless arena stop} - a report: time, damage dealt and taken, kills,
 * parries, rituals, the weapon used.
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class CombatTrial {
    private static final int TIME_LIMIT = 3 * 60 * 20;

    private static final class Trial {
        final Set<UUID> mobs = new HashSet<>();
        final long start;
        /** Damage dealt per weapon (what was in the main hand when each hit landed). */
        final Map<String, Float> byWeapon = new HashMap<>();
        float dealt, taken, maxHit;
        int kills, parries, rituals, hits;

        Trial(long start) {
            this.start = start;
        }

        /** The weapon that dealt the most damage in the trial. */
        String weapon() {
            return byWeapon.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("-");
        }
    }

    private static final Map<UUID, Trial> TRIALS = new HashMap<>();

    public static int start(ServerPlayer player, int size) {
        stop(player, false);
        ServerLevel level = player.serverLevel();
        List<EntityType<? extends Mob>> wave = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            wave.add(EntityType.ZOMBIE);
            wave.add(EntityType.ZOMBIE);
            wave.add(EntityType.SKELETON);
            wave.add(EntityType.SPIDER);
            if (i % 2 == 0) wave.add(EntityType.VINDICATOR);
            if (i % 2 == 1) wave.add(EntityType.BLAZE);
            if (i >= 1) wave.add(EntityType.HUSK);
        }
        Trial trial = new Trial(level.getGameTime());
        for (int i = 0; i < wave.size(); i++) {
            double a = Math.PI * 2 * i / wave.size();
            double r = 9 + level.random.nextDouble() * 3;
            double x = player.getX() + Math.cos(a) * r, z = player.getZ() + Math.sin(a) * r;
            Mob mob = wave.get(i).create(level);
            if (mob == null) continue;
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
            mob.moveTo(x, y, z, (float) Math.toDegrees(Math.atan2(player.getZ() - z, player.getX() - x)) - 90, 0);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.COMMAND, null, null);
            // no burning in daylight, no picking up loot: a fair, repeatable fight
            if (mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            mob.setDropChance(EquipmentSlot.HEAD, 0);
            mob.setCanPickUpLoot(false);
            mob.setPersistenceRequired();
            mob.setTarget(player);
            level.addFreshEntity(mob);
            trial.mobs.add(mob.getUUID());
        }
        TRIALS.put(player.getUUID(), trial);
        player.sendSystemMessage(Component.translatable("trial.soulless.start", trial.mobs.size()).withStyle(ChatFormatting.GOLD));
        return trial.mobs.size();
    }

    public static void stop(ServerPlayer player, boolean report) {
        Trial t = TRIALS.remove(player.getUUID());
        if (t == null) return;
        ServerLevel level = player.serverLevel();
        int left = 0;
        for (UUID id : t.mobs) {
            Entity e = level.getEntity(id);
            if (e != null && e.isAlive()) {
                left++;
                e.discard();
            }
        }
        if (report) report(player, t, left);
    }

    private static void report(ServerPlayer player, Trial t, int left) {
        float seconds = (player.level().getGameTime() - t.start) / 20F;
        String verdict = !player.isAlive() ? "died" : left > 0 ? "stopped" : "cleared";
        player.sendSystemMessage(Component.translatable("trial.soulless." + verdict, t.weapon()).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable("trial.soulless.report1", String.format("%.1f", seconds), t.kills, t.kills + left)
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.translatable("trial.soulless.report2", String.format("%.0f", t.dealt), String.format("%.1f", t.maxHit),
                String.format("%.1f", t.dealt / Math.max(1, seconds))).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.translatable("trial.soulless.report3", String.format("%.1f", t.taken / 2), t.hits, t.parries, t.rituals)
                .withStyle(ChatFormatting.GRAY));
        Soulless.LOGGER.info("[trial] {} {} {}s kills={}/{} dealt={} maxHit={} taken={} hitsTaken={} parries={} rituals={}", player.getGameProfile().getName(),
                verdict, seconds, t.kills, t.kills + left, t.dealt, t.maxHit, t.taken, t.hits, t.parries, t.rituals);
        Soulless.LOGGER.info("[trial] by weapon {}", t.byWeapon);
    }

    /** A katana parry landed (item.KatanaParry). */
    public static void onParry(Player player) {
        Trial t = TRIALS.get(player.getUUID());
        if (t != null) t.parries++;
    }

    /** The Kitsune ritual's blade went in (item.ResonanceRitual). */
    public static void onRitual(Player player) {
        Trial t = TRIALS.get(player.getUUID());
        if (t != null) t.rituals++;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        if (TRIALS.isEmpty()) return;
        if (event.getEntity() instanceof ServerPlayer p && TRIALS.containsKey(p.getUUID())) {
            Trial t = TRIALS.get(p.getUUID());
            t.taken += event.getAmount();
            t.hits++;
        }
        for (Map.Entry<UUID, Trial> e : TRIALS.entrySet()) {
            Trial t = e.getValue();
            if (t.mobs.contains(event.getEntity().getUUID()) && event.getSource().getEntity() instanceof Player p && p.getUUID().equals(e.getKey())) {
                float dealt = Math.min(event.getAmount(), event.getEntity().getHealth());
                t.dealt += dealt;
                t.byWeapon.merge(p.getMainHandItem().getHoverName().getString(), dealt, Float::sum);
                t.maxHit = Math.max(t.maxHit, event.getAmount());
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (TRIALS.isEmpty()) return;
        if (event.getEntity() instanceof ServerPlayer p && TRIALS.containsKey(p.getUUID())) {
            stop(p, true);
            return;
        }
        for (Trial t : TRIALS.values()) {
            if (t.mobs.contains(event.getEntity().getUUID())) t.kills++;
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TRIALS.isEmpty() || event.getServer().getTickCount() % 10 != 0) return;
        for (UUID id : new ArrayList<>(TRIALS.keySet())) {
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(id);
            Trial t = TRIALS.get(id);
            if (p == null) {
                TRIALS.remove(id);
                continue;
            }
            boolean anyAlive = false;
            for (UUID m : t.mobs) {
                Entity e = p.serverLevel().getEntity(m);
                if (e != null && e.isAlive()) {
                    anyAlive = true;
                    if (e instanceof Mob mob && mob.getTarget() == null) mob.setTarget(p);
                }
            }
            if (!anyAlive || p.level().getGameTime() - t.start > TIME_LIMIT) stop(p, true);
        }
    }

    private CombatTrial() {}
}
