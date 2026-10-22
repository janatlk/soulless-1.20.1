package com.soulless.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * A soul leaving a body (network.SoulWispPacket): with a vessel to catch it, a few wisps curl up and then home in on
 * the killer, quickening as they come; with none, they rise, sway and fade away.
 */
public class SoulWisp extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final Entity to;
    private final float phase;

    private SoulWisp(ClientLevel level, double x, double y, double z, SpriteSet sprites, Entity to, float phase) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.to = to;
        this.phase = phase;
        hasPhysics = false;
        gravity = 0;
        lifetime = to != null ? 60 : 40 + random.nextInt(20);
        quadSize = 0.16F + random.nextFloat() * 0.06F;
        xd = (random.nextDouble() - 0.5) * 0.12;
        yd = 0.08 + random.nextDouble() * 0.05;
        zd = (random.nextDouble() - 0.5) * 0.12;
        setSpriteFromAge(sprites);
    }

    public static void spawn(double x, double y, double z, int toId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || Sprites.sprites == null) return;
        Entity to = toId >= 0 ? mc.level.getEntity(toId) : null;
        for (int i = 0; i < 4; i++) {
            mc.particleEngine.add(new SoulWisp(mc.level, x, y, z, Sprites.sprites, to, i * Mth.HALF_PI));
        }
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        setSpriteFromAge(sprites);
        if (to != null && to.isAlive() && age > 8) {
            // homing: steer toward the chest, ever harder, with a little spiral round the line
            Vec3 aim = to.position().add(0, to.getBbHeight() * 0.55, 0).subtract(x, y, z);
            double d = aim.length();
            if (d < 0.4) {
                remove();
                return;
            }
            Vec3 dir = aim.scale(1 / d);
            float pull = Math.min(1, (age - 8) / 20F);
            double speed = 0.12 + pull * 0.5;
            Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(Math.sin(age * 0.5 + phase) * 0.06);
            float k = 0.25F + pull * 0.5F;
            xd = Mth.lerp(k, xd, dir.x * speed + side.x);
            yd = Mth.lerp(k, yd, dir.y * speed);
            zd = Mth.lerp(k, zd, dir.z * speed + side.z);
        } else if (to == null) {
            // drifting off: rise and sway, fading
            yd = yd * 0.96 + 0.004;
            xd = xd * 0.92 + Math.sin(age * 0.3 + phase) * 0.008;
            zd = zd * 0.92 + Math.cos(age * 0.3 + phase) * 0.008;
            alpha = 1 - (float) age / lifetime;
        } else {
            xd *= 0.9;
            yd *= 0.9;
            zd *= 0.9;
        }
        move(xd, yd, zd);
    }

    @Override
    protected int getLightColor(float partial) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Registered for the soul_wisp particle type only so its sprites get loaded and kept here. */
    public static final class Sprites implements net.minecraft.client.particle.ParticleProvider<SimpleParticleType> {
        static SpriteSet sprites;

        public Sprites(SpriteSet s) {
            sprites = s;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new SoulWisp(level, x, y, z, sprites, null, 0);
        }
    }
}
