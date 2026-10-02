package com.soulless.client;

import com.soulless.ability.RendTimeline;
import com.soulless.network.ClientControlPacket;
import com.soulless.network.EffectSyncPacket;
import com.soulless.network.RendHitPacket;
import com.soulless.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class ClientPacketHandler {
    public static void slamStart(int entityId) {
        ClientState.IMPACTS.remove(entityId);
        ClientState.SLAMS.put(entityId, ClientState.gameTime());
    }

    public static void slamImpact(int entityId, double x, double y, double z, float radius) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        ClientState.IMPACTS.put(entityId, ClientState.gameTime());
        double dist = mc.player.position().distanceTo(new Vec3(x, y, z));
        ClientState.shake = Math.max(ClientState.shake, (float) Math.max(0, 1.2 - dist / 28.0));

        RandomSource rnd = level.getRandom();
        // a ring of soul fire racing outward along the ground
        int n = 110;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            double speed = 0.5 + rnd.nextDouble() * 0.2;
            level.addParticle(ModParticles.SOUL_EMBER.get(), x, y + 0.15, z, Math.cos(a) * speed, 0.02, Math.sin(a) * speed);
        }
        // pillars of soul fire erupting around the impact
        int pillars = 9;
        double phase = rnd.nextDouble() * Math.PI;
        for (int p = 0; p < pillars; p++) {
            double a = phase + Math.PI * 2 * p / pillars;
            double r = (p % 2 == 0 ? 0.62 : 0.95) * radius;
            double px = x + Math.cos(a) * r, pz = z + Math.sin(a) * r;
            for (int k = 0; k < 22; k++) {
                level.addParticle(ModParticles.SOUL_EMBER.get(), px + (rnd.nextDouble() - 0.5) * 0.5, y + rnd.nextDouble() * 0.4,
                        pz + (rnd.nextDouble() - 0.5) * 0.5, 0, 0.35 + rnd.nextDouble() * 0.55, 0);
            }
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, px, y + 0.1, pz, 0, 0.25, 0);
        }
        // a geyser where the blade went in
        for (int k = 0; k < 40; k++) {
            level.addParticle(ModParticles.SOUL_EMBER.get(), x + (rnd.nextDouble() - 0.5) * 0.4, y + 0.2, z + (rnd.nextDouble() - 0.5) * 0.4,
                    (rnd.nextDouble() - 0.5) * 0.15, 0.6 + rnd.nextDouble() * 0.7, (rnd.nextDouble() - 0.5) * 0.15);
        }
    }

    public static void effectSync(int entityId, int kind, int duration) {
        var map = kind == EffectSyncPacket.STUN ? ClientState.STUNNED : ClientState.CURSED;
        if (duration <= 0) {
            map.remove(entityId);
        } else {
            map.put(entityId, ClientState.gameTime() + duration);
        }
    }

    public static void control(int action, boolean value) {
        if (action == ClientControlPacket.SHOW_ZONES) {
            ClientState.showZones = value;
        } else if (action == ClientControlPacket.RESET_COOLDOWNS) {
            ClientState.rendReadyAt = 0;
        }
    }

    public static void rendStart(int entityId, int elapsed) {
        ClientState.RENDS.put(entityId, ClientState.gameTime() - elapsed);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getId() == entityId && elapsed == 0) {
            ClientState.rendReleaseSent = false;
        }
    }

    public static void rendHit(RendHitPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        long now = ClientState.gameTime();
        if (msg.entityId() == mc.player.getId()) {
            ClientState.rendReadyAt = now + RendTimeline.COOLDOWN;
        }
        ClientState.TRAILS.add(new SlashTrails.Trail(new Vec3(msg.x(), msg.y(), msg.z()), msg.yaw(), now));
        double dist = mc.player.position().distanceTo(new Vec3(msg.x(), msg.y(), msg.z()));
        ClientState.shake = Math.max(ClientState.shake, (float) Math.max(0, 0.7 - dist / 30.0));
        SlashTrails.burst(level, new Vec3(msg.x(), msg.y(), msg.z()), msg.yaw());
    }

    private ClientPacketHandler() {}
}
