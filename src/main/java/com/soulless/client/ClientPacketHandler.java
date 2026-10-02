package com.soulless.client;

import com.soulless.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;

public final class ClientPacketHandler {
    public static void slamStart(int entityId) {
        ClientState.SLAMS.put(entityId, ClientState.gameTime());
    }

    public static void slamImpact(double x, double y, double z, float radius) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        double dist = mc.player.position().distanceTo(new net.minecraft.world.phys.Vec3(x, y, z));
        ClientState.shake = Math.max(ClientState.shake, (float) Math.max(0, 1.0 - dist / 30.0));

        // A ring of soul fire racing outward along the ground.
        RandomSource rnd = level.getRandom();
        int n = 90;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            double speed = 0.45 + rnd.nextDouble() * 0.15;
            level.addParticle(ModParticles.SOUL_EMBER.get(), x, y + 0.15, z, Math.cos(a) * speed, 0.02, Math.sin(a) * speed);
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
