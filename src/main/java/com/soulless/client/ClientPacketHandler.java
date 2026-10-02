package com.soulless.client;

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

    public static void curseSync(int entityId, int duration) {
        if (duration <= 0) {
            ClientState.CURSED.remove(entityId);
        } else {
            ClientState.CURSED.put(entityId, ClientState.gameTime() + duration);
        }
    }

    private ClientPacketHandler() {}
}
