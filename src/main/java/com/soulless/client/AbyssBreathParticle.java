package com.soulless.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * A puff of the Leviathan's breath (tools/gen_breath_particles.py), thrown from its jaws with the speed the server
 * gives it: it bursts out fast and small and blinding white-violet, slows as the water takes it, swells and roils,
 * cools through violet to a deep purple smoke and thins away. Many together make the breath a churning cone rather
 * than a line.
 */
public class AbyssBreathParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float size0, spin, swirl = random.nextFloat() * 6.28F;

    protected AbyssBreathParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);
        this.sprites = sprites;
        xd = vx;
        yd = vy;
        zd = vz;
        hasPhysics = true;   // they break against walls
        friction = 0.88F;
        gravity = -0.004F;   // the warm breath lifts, barely
        lifetime = 22 + random.nextInt(14);
        quadSize = 0.25F + random.nextFloat() * 0.2F;
        size0 = quadSize;
        roll = oRoll = random.nextFloat() * 6.28F;
        spin = (random.nextFloat() - 0.5F) * 0.12F;
        setSpriteFromAge(sprites);
        color(0);
    }

    /** White-violet heart at first, through violet, to a deep purple smoke. */
    private void color(float k) {
        float r, g, b;
        if (k < 0.2F) {
            float t = k / 0.2F;
            r = Mth.lerp(t, 0.97F, 0.82F);
            g = Mth.lerp(t, 0.9F, 0.55F);
            b = 1F;
        } else if (k < 0.55F) {
            float t = (k - 0.2F) / 0.35F;
            r = Mth.lerp(t, 0.82F, 0.5F);
            g = Mth.lerp(t, 0.55F, 0.2F);
            b = Mth.lerp(t, 1F, 0.78F);
        } else {
            float t = (k - 0.55F) / 0.45F;
            r = Mth.lerp(t, 0.5F, 0.16F);
            g = Mth.lerp(t, 0.2F, 0.06F);
            b = Mth.lerp(t, 0.78F, 0.3F);
        }
        setColor(r, g, b);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        float k = (float) age / lifetime;
        // swells fast at first, then slowly; roils sideways as it slows
        quadSize = size0 * (1 + 7 * (1 - (1 - k) * (1 - k)));
        float slow = 1 - Math.min(1, (float) Math.sqrt(xd * xd + yd * yd + zd * zd) * 2);
        xd += Mth.sin(age * 0.35F + swirl) * 0.006F * slow;
        zd += Mth.cos(age * 0.31F + swirl) * 0.006F * slow;
        yd += Mth.sin(age * 0.27F + swirl * 2) * 0.004F * slow;
        oRoll = roll;
        roll += spin * (1 + slow);
        alpha = k < 0.7F ? 0.95F : 0.95F * (1 - (k - 0.7F) / 0.3F);
        color(k);
        setSpriteFromAge(sprites);
    }

    /** Bright while it is young: the heart of the breath lights the water round it. */
    @Override
    protected int getLightColor(float partial) {
        float k = (age + partial) / lifetime;
        return k < 0.45F ? 0xF000F0 : super.getLightColor(partial);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new AbyssBreathParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
