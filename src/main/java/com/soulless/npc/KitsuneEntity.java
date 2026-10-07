package com.soulless.npc;

import com.soulless.Soulless;
import com.soulless.item.KatanaOath;
import com.soulless.npc.dialogue.DialogueActions;
import com.soulless.registry.ModItems;
import com.soulless.world.Astral;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * Kitsune, asleep in the astral (world.Astral) - bound there by the Wailing Reaper, its seals circling her in violet.
 * She wakes when spoken to (data/soulless/dialogues/kitsune.json). Given the oath, she rises and reforges the broken
 * blade in the sleeper's hands, then sends them back to wake. She cannot be hurt and never moves from her place.
 */
public class KitsuneEntity extends NpcEntity implements GeoEntity {
    public static final int SLEEP = 0, AWAKE = 1, REFORGE = 2;
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(KitsuneEntity.class, EntityDataSerializers.INT);
    /** Reforging: the blade is whole at this tick, the sleeper wakes at the end. */
    private static final int REFORGED_AT = 50, REFORGE_END = 85;
    private static final DustParticleOptions SEAL = new DustParticleOptions(new Vector3f(0.55F, 0.2F, 0.95F), 1.4F);

    private static final RawAnimation SLEEPING = RawAnimation.begin().thenLoop("animation.kitsune.sleep");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.kitsune.idle");
    private static final RawAnimation RISE = RawAnimation.begin().thenPlayAndHold("animation.kitsune.reforge");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private UUID reforging;
    private int reforgeTicks;
    private int lonely;

    public KitsuneEntity(EntityType<? extends KitsuneEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return NpcEntity.createAttributes().add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }

    @Override
    public ResourceLocation dialogue() {
        return Soulless.id("kitsune");
    }

    @Override
    protected boolean roams() {
        return false;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(STATE, SLEEP);
    }

    public int state() {
        return entityData.get(STATE);
    }

    private void setState(int s) {
        entityData.set(STATE, s);
    }

    /** Her body stays where it lies; only the head follows the one she talks to. */
    @Override
    protected BodyRotationControl createBodyControl() {
        return new BodyRotationControl(this) {
            @Override
            public void clientTick() {}
        };
    }

    @Override
    public int getMaxHeadYRot() {
        return 55;
    }

    @Override
    public void startTalking(Player player) {
        super.startTalking(player);
        if (state() == SLEEP) {
            setState(AWAKE);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.FOX_AMBIENT, SoundSource.NEUTRAL, 1.2F, 0.5F);
        }
    }

    // ---- the oath and the reforging (dialogue actions)

    /** Dialogue actions of her conversation: soulless:oath, soulless:refuse, soulless:reforge, soulless:wake. */
    public static void registerDialogueActions() {
        DialogueActions.register("soulless:oath", j -> ctx -> {
            ctx.setFlag("player", KatanaOath.SWORN, true);
            if (ctx.npc instanceof KitsuneEntity k) k.startReforge(ctx.player);
        });
        DialogueActions.register("soulless:refuse", j -> ctx -> ctx.setFlag("player", KatanaOath.REFUSED, true));
        // the oath already given, a blade broken again: reforged with no new oath
        DialogueActions.register("soulless:reforge", j -> ctx -> {
            if (ctx.npc instanceof KitsuneEntity k) k.startReforge(ctx.player);
        });
        DialogueActions.register("soulless:wake", j -> ctx -> {
            ctx.end();
            Astral.leave(ctx.player);
        });
    }

    private void startReforge(ServerPlayer player) {
        reforging = player.getUUID();
        reforgeTicks = 0;
        setState(REFORGE);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 1.2F, 1.3F);
    }

    private void reforgeTick(ServerLevel level) {
        Player p = reforging == null ? null : level.getPlayerByUUID(reforging);
        if (!(p instanceof ServerPlayer player)) {
            reforging = null;
            setState(AWAKE);
            return;
        }
        reforgeTicks++;
        if (reforgeTicks < REFORGED_AT) {
            // light rises around her as she calls it up; small sparks gather at the blade in the sleeper's hands
            if (reforgeTicks % 3 == 0) {
                double ang = reforgeTicks * 0.3;
                level.sendParticles(ParticleTypes.END_ROD, getX() + Math.cos(ang) * 1.8, getY() + 0.3 + reforgeTicks * 0.04,
                        getZ() + Math.sin(ang) * 1.8, 1, 0, 0.02, 0, 0);
            }
            if (reforgeTicks % 4 == 0) {
                var look = player.getLookAngle();
                double hx = player.getX() + look.x * 1.4, hy = player.getEyeY() - 0.7, hz = player.getZ() + look.z * 1.4;
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hx, hy, hz, 1, 0.15, 0.1, 0.15, 0.01);
            }
            if (reforgeTicks % 10 == 0) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5F,
                        0.8F + reforgeTicks / (float) REFORGED_AT);
            }
        } else if (reforgeTicks == REFORGED_AT) {
            var look = player.getLookAngle();
            double hx = player.getX() + look.x * 0.9, hy = player.getEyeY() - 0.5, hz = player.getZ() + look.z * 0.9;
            level.sendParticles(ParticleTypes.FLASH, hx, hy, hz, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hx, hy, hz, 8, 0.15, 0.15, 0.15, 0.2);
            level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 2, getZ(), 14, 0.4, 0.4, 0.4, 0.12);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2, 1);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1, 1.4F);
            reforge(player);
        } else if (reforgeTicks >= REFORGE_END) {
            reforging = null;
            setState(AWAKE);
            Astral.leave(player);
        }
    }

    /** The broken blade in the player's inventory becomes the Legendary Katana. */
    private static void reforge(ServerPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(ModItems.BROKEN_KATANA.get())) {
                inv.setItem(i, new ItemStack(ModItems.SOUL_KATANA.get()));
                return;
            }
        }
        if (!inv.add(new ItemStack(ModItems.SOUL_KATANA.get()))) player.drop(new ItemStack(ModItems.SOUL_KATANA.get()), false);
    }

    // ---- life in the astral

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (state() == REFORGE) reforgeTick(level);
        boolean watched = level.getNearestPlayer(this, 40) != null;
        // with nobody left in the dream she falls back asleep
        lonely = watched ? 0 : lonely + 1;
        if (lonely > 100 && state() == AWAKE) setState(SLEEP);
        if (watched && tickCount % 3 == 0) {
            // the Reaper's seals: a slow violet ring around her, a wisp now and then
            double a = tickCount * 0.03;
            for (int i = 0; i < 4; i++) {
                double ang = a + i * Math.PI / 2;
                level.sendParticles(SEAL, getX() + Math.cos(ang) * 2.8, getY() + 0.1, getZ() + Math.sin(ang) * 2.8, 1, 0, 0, 0, 0);
            }
            if (random.nextInt(4) == 0) {
                level.sendParticles(ParticleTypes.END_ROD, getX() + random.nextGaussian() * 0.9, getY() + 0.4 + random.nextDouble() * 0.8,
                        getZ() + random.nextGaussian() * 0.9, 0, 0, 1, 0, 0.015);
            }
            if (random.nextInt(6) == 0) {
                level.sendParticles(ParticleTypes.WITCH, getX() + random.nextGaussian() * 1.2, getY() + 0.6 + random.nextDouble(),
                        getZ() + random.nextGaussian() * 1.2, 1, 0, 0.02, 0, 0);
            }
        }
    }

    /** Nothing can wound her (only /kill and the void). */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {}

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return state() == SLEEP ? SoundEvents.FOX_SLEEP : SoundEvents.FOX_AMBIENT;
    }

    @Override
    public float getVoicePitch() {
        return 0.55F;
    }

    // ---- animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 15, event -> {
            int s = state();
            event.setAnimation(s == SLEEP ? SLEEPING : s == REFORGE ? RISE : IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
