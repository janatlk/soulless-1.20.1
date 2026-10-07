package com.soulless.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** The Renegade's particles (tools/gen_renegade_particles.py): one class, a behaviour for each kind. */
public class RenegadeParticle extends TextureSheetParticle {
    public enum Kind {
        /** A crimson flame tongue: drifts up, flickers, burns down. */
        EMBER,
        /** Inky violet smoke: swells, rises a little, thins away. */
        SMOKE,
        /** A hot spark: flies, falls, bounces, burns out. */
        SPARK,
        /** The crescent of a cut: a flash where the blade passed. */
        SLASH,
        /** A shock ring thrown out from a blow on the ground. */
        BURST,
        /** A flake of ash: tumbles slowly down, swaying. */
        ASH
    }

    private final Kind kind;
    private final SpriteSet sprites;
    private final float size0;
    private final float sway = random.nextFloat() * 6.28F;

    protected RenegadeParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, Kind kind) {
        super(level, x, y, z, vx, vy, vz);
        this.kind = kind;
        this.sprites = sprites;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.hasPhysics = false;
        switch (kind) {
            case EMBER -> {
                friction = 0.92F;
                gravity = -0.025F;
                quadSize *= 0.45F + random.nextFloat() * 0.4F;
                lifetime = 12 + random.nextInt(14);
                xd += (random.nextFloat() - 0.5F) * 0.01F;
                zd += (random.nextFloat() - 0.5F) * 0.01F;
            }
            case SMOKE -> {
                friction = 0.94F;
                gravity = -0.006F;
                quadSize *= 1.6F + random.nextFloat() * 0.9F;
                lifetime = 18 + random.nextInt(18);
                roll = oRoll = random.nextFloat() * 6.28F;
            }
            case SPARK -> {
                friction = 0.9F;
                gravity = 0.9F;
                hasPhysics = true;
                quadSize *= 0.35F + random.nextFloat() * 0.25F;
                lifetime = 6 + random.nextInt(8);
            }
            case SLASH -> {
                friction = 0.6F;
                gravity = 0;
                quadSize = 1.1F + random.nextFloat() * 0.5F;
                lifetime = 7;
                roll = oRoll = (random.nextFloat() - 0.5F) * 1.2F;
            }
            case BURST -> {
                friction = 0;
                gravity = 0;
                xd = yd = zd = 0;
                quadSize = 2.4F;
                lifetime = 9;
            }
            case ASH -> {
                friction = 0.98F;
                gravity = 0.012F;
                quadSize *= 0.5F + random.nextFloat() * 0.4F;
                lifetime = 60 + random.nextInt(50);
                roll = oRoll = random.nextFloat() * 6.28F;
            }
        }
        size0 = quadSize;
        if (kind == Kind.ASH) pickSprite(sprites);
        else setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        float k = (float) age / lifetime;
        switch (kind) {
            case EMBER -> {
                xd += Mth.sin(age * 0.7F + sway) * 0.002F;
                setSpriteFromAge(sprites);
            }
            case SMOKE -> {
                quadSize = size0 * (1 + k * 0.8F);
                alpha = 0.9F * (1 - k * k);
                oRoll = roll;
                roll += 0.02F;
                setSpriteFromAge(sprites);
            }
            case SPARK, SLASH -> setSpriteFromAge(sprites);
            case BURST -> {
                quadSize = size0 * (0.6F + k * 0.8F);
                setSpriteFromAge(sprites);
            }
            case ASH -> {
                xd += Mth.sin(age * 0.08F + sway) * 0.0015F;
                zd += Mth.cos(age * 0.07F + sway) * 0.0015F;
                oRoll = roll;
                roll += 0.05F;
                alpha = k > 0.8F ? (1 - k) * 5 : 1;
            }
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return kind == Kind.SMOKE || kind == Kind.ASH ? super.getLightColor(partialTick) : 0xF000F0;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final Kind kind;

        public Provider(SpriteSet sprites, Kind kind) {
            this.sprites = sprites;
            this.kind = kind;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new RenegadeParticle(level, x, y, z, vx, vy, vz, sprites, kind);
        }
    }
}
