package com.soulless.item;

import com.soulless.Soulless;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the katana's attacks. The katana never uses the vanilla crosshair attack: the client announces
 * each cut (KatanaCutPacket) and the server hits whatever the blade passes through, a little generously -
 * <ul>
 *   <li>kesa / gyaku: everything in an arc in front of the player, when the blade sweeps past (a few ticks
 *       after the click, in step with the animation);</li>
 *   <li>tsuki, the combo's third step: the first thing along a narrow line ahead, for more damage, at the lunge.</li>
 * </ul>
 * Nothing behind a wall is hit. Damage follows the vanilla attack: the attack damage attribute plus enchantments,
 * scaled by how charged the attack was.
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class KatanaCombat {
    public static final int KESA = 0, GYAKU = 1, THRUST_STEP = 2;
    /** The thrust deals this much more than a cut. */
    public static final float THRUST_DAMAGE_MULT = 1.6F;
    /** Ticks from a cut's start to the blade sweeping past / the thrust's lunge (client.KatanaPoses timelines). */
    private static final int[] HIT_DELAY = {7, 8, 9};
    /** Under the Kitsune's blessing the katana moves this much faster (animations, hits and attack recharge). */
    public static final float BLESSED_RATE = 1.25F;
    /** The cut's arc: reach from the player's centre and half-angle around the facing. */
    public static final double CUT_REACH = 3.6;
    private static final double CUT_HALF_ANGLE = Math.toRadians(75);
    /** A cut hits its main target (closest to where the player faces) fully, the others in the arc for this share. */
    public static final float CUT_SIDE_DAMAGE = 0.55F;
    /** The thrust's line: length from the chest and how fat it is. */
    public static final double THRUST_REACH = 4.0;
    private static final double THRUST_RADIUS = 0.45;
    /** The katana's sounds are soft: the cut, not the noise, should carry the hit. */
    private static final float HIT_VOLUME = 0.4F;
    /** A new cut is refused while the last one is younger than this (clients can't spam faster than the animation). */
    private static final int MIN_GAP = 10;

    private record Pending(int step, long due, float charge) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();

    /** The client says a cut started (0 kesa, 1 gyaku, 2 tsuki); its hit is resolved when the blade gets there. */
    public static void announce(ServerPlayer player, int step) {
        if (step < KESA || step > THRUST_STEP || !(player.getMainHandItem().getItem() instanceof SoulKatanaItem)) return;
        if (KitsuneRitual.isActive(player)) return;
        long now = player.level().getGameTime();
        // right after a parry the riposte comes at once, faster and at full strength, whatever the weapon's cooldown
        boolean riposte = KatanaParry.riposteReady(player);
        float rate = rate(player) * (riposte && step != THRUST_STEP ? KatanaParry.RIPOSTE_RATE : 1);
        Long last = LAST.get(player.getUUID());
        if (PENDING.containsKey(player.getUUID()) || (!riposte && last != null && now - last < MIN_GAP / rate)) return;
        LAST.put(player.getUUID(), now);
        float charge = riposte ? 1 : player.getAttackStrengthScale(0.5F);
        player.resetAttackStrengthTicker();
        PENDING.put(player.getUUID(), new Pending(step, now + Math.round(HIT_DELAY[step] / rate), charge));
        // everyone else watching plays the cut on this player's model
        com.soulless.network.ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> player),
                new com.soulless.network.KatanaAnimPacket(player.getId(), step, rate));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        for (Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Pending> e = it.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(e.getKey());
            if (player == null || !player.isAlive() || !(player.getMainHandItem().getItem() instanceof SoulKatanaItem)) {
                it.remove();
                continue;
            }
            if (player.level().getGameTime() >= e.getValue().due) {
                it.remove();
                if (e.getValue().step == THRUST_STEP) thrust(player, e.getValue().charge);
                else cut(player, e.getValue().charge);
            }
        }
    }

    /** A cut or thrust is under way (its hit not resolved yet). */
    public static boolean isCutting(net.minecraft.world.entity.player.Player player) {
        return PENDING.containsKey(player.getUUID());
    }

    /** How fast this player's katana moves: faster under the Kitsune's blessing. */
    public static float rate(net.minecraft.world.entity.player.Player player) {
        return player.hasEffect(com.soulless.registry.ModEffects.KITSUNE_BLESSING.get()) ? BLESSED_RATE : 1;
    }

    /** Horizontal unit vector the player faces. */
    public static Vec3 facing(net.minecraft.world.entity.player.Player player) {
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    /** What a cut made now would hit: everything in the arc in front (shared with the zones overlay). */
    public static List<LivingEntity> cutTargets(net.minecraft.world.entity.player.Player player) {
        Vec3 c = player.position();
        Vec3 facing = facing(player);
        AABB box = new AABB(c.x - CUT_REACH, c.y - 0.6, c.z - CUT_REACH, c.x + CUT_REACH, c.y + player.getBbHeight() + 0.8, c.z + CUT_REACH);
        List<LivingEntity> hit = new ArrayList<>();
        for (LivingEntity t : player.level().getEntitiesOfClass(LivingEntity.class, box, t -> canHit(player, t))) {
            // nearest point of the target's box, so big mobs are hit at their edge
            AABB b = t.getBoundingBox();
            double nx = Mth.clamp(c.x, b.minX, b.maxX), nz = Mth.clamp(c.z, b.minZ, b.maxZ);
            Vec3 to = new Vec3(nx - c.x, 0, nz - c.z);
            double d = to.length();
            if (d > CUT_REACH) continue;
            if (d > 0.8 && Math.acos(Mth.clamp(to.normalize().dot(facing), -1, 1)) > CUT_HALF_ANGLE) continue;
            if (!player.hasLineOfSight(t)) continue;
            hit.add(t);
        }
        return hit;
    }

    /** The thrust's line: from the chest along the look (shared with the zones overlay). */
    public static Vec3[] thrustLine(net.minecraft.world.entity.player.Player player) {
        Vec3 from = player.position().add(0, player.getBbHeight() * 0.7, 0);
        return new Vec3[]{from, from.add(player.getLookAngle().scale(THRUST_REACH))};
    }

    /** What the thrust made now would run into: the first target along its line, or null. */
    @org.jetbrains.annotations.Nullable
    public static LivingEntity thrustTarget(net.minecraft.world.entity.player.Player player) {
        Vec3[] line = thrustLine(player);
        Vec3 from = line[0], to = line[1];
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity t : player.level().getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1.0), t -> canHit(player, t))) {
            AABB b = t.getBoundingBox().inflate(THRUST_RADIUS);
            Vec3 at = b.contains(from) ? from : b.clip(from, to).orElse(null);
            if (at == null || !player.hasLineOfSight(t)) continue;
            double d = at.distanceToSqr(from);
            if (d < bestDist) {
                bestDist = d;
                best = t;
            }
        }
        return best;
    }

    /** Everything in the arc in front, at full damage each. */
    private static void cut(ServerPlayer player, float charge) {
        Vec3 c = player.position();
        Vec3 facing = facing(player);
        List<LivingEntity> hit = cutTargets(player);
        ServerLevel level = player.serverLevel();
        Vec3 front = c.add(facing.scale(1.6)).add(0, player.getBbHeight() * 0.55, 0);
        if (!hit.isEmpty()) {
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, front.x, front.y, front.z, 1, 0, 0, 0, 0);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), com.soulless.registry.ModSounds.KATANA_HIT.get(),
                    SoundSource.PLAYERS, HIT_VOLUME, 0.95F + player.getRandom().nextFloat() * 0.15F);
        }
        float bonus = hit.isEmpty() ? 1 : KatanaParry.consumeBonus(player);
        // the main target: the one most squarely in front (smallest angle off the facing, then nearest)
        LivingEntity main = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity t : hit) {
            Vec3 to = new Vec3(t.getX() - c.x, 0, t.getZ() - c.z);
            double d = to.length();
            double score = (d < 1e-3 ? 0 : Math.acos(Mth.clamp(to.normalize().dot(facing), -1, 1))) * 4 + d;
            if (score < best) {
                best = score;
                main = t;
            }
        }
        for (LivingEntity t : hit) strike(player, t, charge, bonus * (t == main ? 1 : CUT_SIDE_DAMAGE), facing, 0.4F);
        if (!hit.isEmpty()) wear(player);
    }

    /** The first target along a narrow line from the chest, along the look (aimable up and down). */
    private static void thrust(ServerPlayer player, float charge) {
        Vec3 dir = player.getLookAngle();
        LivingEntity best = thrustTarget(player);
        ServerLevel level = player.serverLevel();
        if (best == null) return;
        level.playSound(null, player.getX(), player.getY(), player.getZ(), com.soulless.registry.ModSounds.KATANA_HIT.get(),
                SoundSource.PLAYERS, HIT_VOLUME, 1.15F);
        level.sendParticles(ParticleTypes.CRIT, best.getX(), best.getY(0.6), best.getZ(), 12, 0.2, 0.2, 0.2, 0.3);
        strike(player, best, charge, THRUST_DAMAGE_MULT * KatanaParry.consumeBonus(player), new Vec3(dir.x, 0, dir.z).normalize(), 0.7F);
        wear(player);
    }

    private static boolean canHit(net.minecraft.world.entity.player.Player player, LivingEntity t) {
        return t != player && t.isAlive() && t.isAttackable() && !t.isSpectator() && !player.isAlliedTo(t)
                && t != player.getVehicle() && t.getVehicle() != player;
    }

    /** One target: damage like a vanilla attack (attribute + enchantments, scaled by charge), knockback, fire aspect. */
    private static void strike(ServerPlayer player, LivingEntity t, float charge, float mult, Vec3 push, float knockback) {
        ItemStack stack = player.getMainHandItem();
        float base = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float ench = EnchantmentHelper.getDamageBonus(stack, t.getMobType());
        float damage = (base * (0.2F + charge * charge * 0.8F) + ench * charge) * mult;
        float hpBefore = t.getHealth();
        if (!t.hurt(player.damageSources().playerAttack(player), damage)) return;
        float kb = knockback + EnchantmentHelper.getKnockbackBonus(player) * 0.5F;
        t.knockback(kb, -push.x, -push.z);
        int fire = EnchantmentHelper.getFireAspect(player);
        if (fire > 0) t.setSecondsOnFire(fire * 4);
        player.setLastHurtMob(t);
        EnchantmentHelper.doPostHurtEffects(t, player);
        EnchantmentHelper.doPostDamageEffects(player, t);
        float dealt = hpBefore - t.getHealth();
        if (dealt > 2.0F) {
            player.serverLevel().sendParticles(ParticleTypes.DAMAGE_INDICATOR, t.getX(), t.getY(0.5), t.getZ(),
                    (int) (dealt * 0.5), 0.1, 0, 0.1, 0.2);
        }
    }

    private static void wear(ServerPlayer player) {
        player.getMainHandItem().hurtAndBreak(1, player, p -> p.broadcastBreakEvent(net.minecraft.world.InteractionHand.MAIN_HAND));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
        LAST.remove(event.getEntity().getUUID());
    }

    private KatanaCombat() {}
}
