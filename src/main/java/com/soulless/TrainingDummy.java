package com.soulless;

import com.soulless.registry.ModEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /soulless dummy}: an immortal, motionless iron golem for testing. Every time it takes damage it
 * reports in chat to players nearby: the hit, the running total (reset after 10 s without damage), the Soul
 * Curse damage per second and time left, and the stun time left.
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class TrainingDummy {
    public static final String TAG = "soulless_dummy";
    private static final int RESET_TICKS = 200;

    private static final class Stats {
        float total;
        float thisTick;
        long lastHit;
    }

    private static final Map<UUID, Stats> STATS = new HashMap<>();

    public static boolean isDummy(Entity e) {
        return e.getPersistentData().getBoolean(TAG);
    }

    public static void spawn(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        HitResult hit = player.pick(16, 1, false);
        Vec3 at = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : player.position().add(player.getLookAngle().scale(4));
        IronGolem golem = EntityType.IRON_GOLEM.create(level);
        if (golem == null) return;
        golem.moveTo(at.x, Math.floor(at.y + 0.01), at.z, player.getYRot() + 180, 0);
        golem.setNoAi(true);
        golem.setPersistenceRequired();
        golem.setSilent(true);
        golem.setCustomName(Component.translatable("entity.soulless.dummy").withStyle(ChatFormatting.LIGHT_PURPLE));
        golem.setCustomNameVisible(true);
        golem.getPersistentData().putBoolean(TAG, true);
        level.addFreshEntity(golem);
    }

    public static int clear(ServerLevel level) {
        int n = 0;
        for (Entity e : level.getAllEntities()) {
            if (isDummy(e)) {
                e.discard();
                n++;
            }
        }
        return n;
    }

    /** Count the damage, then cancel the health loss: the dummy never dies. */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || !isDummy(e)) return;
        Stats s = STATS.computeIfAbsent(e.getUUID(), k -> new Stats());
        long now = e.level().getGameTime();
        if (now - s.lastHit > RESET_TICKS) s.total = 0;
        s.lastHit = now;
        s.total += event.getAmount();
        s.thisTick += event.getAmount();
        event.setAmount(0);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (isDummy(event.getEntity())) {
            event.setCanceled(true);
            event.getEntity().setHealth(event.getEntity().getMaxHealth());
        }
    }

    /** One chat line per dummy per tick it was damaged. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || STATS.isEmpty()) return;
        Iterator<Map.Entry<UUID, Stats>> it = STATS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Stats> en = it.next();
            Stats s = en.getValue();
            if (s.thisTick <= 0) continue;
            LivingEntity dummy = find(event.getServer(), en.getKey());
            if (dummy == null) {
                it.remove();
                continue;
            }
            MutableComponent msg = Component.translatable("message.soulless.dummy.hit", fmt(s.thisTick), fmt(s.total))
                    .withStyle(ChatFormatting.LIGHT_PURPLE);
            MobEffectInstance curse = dummy.getEffect(ModEffects.SOUL_CURSE.get());
            if (curse != null) {
                float dps = com.soulless.effect.SoulCurse.DAMAGE_PER_SECOND + curse.getAmplifier();
                msg.append(Component.translatable("message.soulless.dummy.curse", fmt(dps), fmt(curse.getDuration() / 20F))
                        .withStyle(ChatFormatting.DARK_PURPLE));
            }
            MobEffectInstance stun = dummy.getEffect(ModEffects.STUN.get());
            if (stun != null) {
                msg.append(Component.translatable("message.soulless.dummy.stun", fmt(stun.getDuration() / 20F))
                        .withStyle(ChatFormatting.YELLOW));
            }
            for (ServerPlayer p : ((ServerLevel) dummy.level()).players()) {
                if (p.distanceToSqr(dummy) < 48 * 48) p.sendSystemMessage(msg);
            }
            s.thisTick = 0;
        }
    }

    private static LivingEntity find(net.minecraft.server.MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof LivingEntity l) return l;
        }
        return null;
    }

    private static String fmt(float v) {
        return String.valueOf(Math.round(v * 10) / 10.0);
    }

    private TrainingDummy() {}
}
