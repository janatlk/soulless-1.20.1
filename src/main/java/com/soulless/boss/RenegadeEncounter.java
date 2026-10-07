package com.soulless.boss;

import com.soulless.Soulless;
import com.soulless.item.KatanaOath;
import com.soulless.npc.dialogue.DialogueContext;
import com.soulless.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The first meeting with the Renegade (STORY_KATANA.md, act 6). A couple of minutes after Kitsune reforges the blade,
 * somewhere quiet in the overworld, dark smoke gathers round the player, a voice speaks, and they are pulled into the
 * dark astral (data dimension soulless:dark_astral: an empty black sky, an invisible floor). He rises, says his piece
 * and attacks (RenegadeEntity). The player cannot die there: a blow that would kill leaves half a heart and ends the
 * fight - he scoffs and leaves; he comes again some minutes later. Beaten, he kneels, swears it is not over and
 * vanishes. Either way the player wakes where they stood. Harakiri (R) is not known yet in this fight: the sensei
 * teaches it afterwards.
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class RenegadeEncounter {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, Soulless.id("dark_astral"));
    /** Player flags: the time he is due (game time), and the fight won. */
    public static final String DUE = "renegade.due", DEFEATED = "renegade.defeated";
    private static final int FIRST_DELAY = 2400, RETRY_DELAY = 6000;
    private static final int FLOOR_Y = 99, FLOOR_RADIUS = 26;
    private static final Vec3 ARRIVE = new Vec3(0.5, FLOOR_Y + 1, 8.5), HIM = new Vec3(0.5, FLOOR_Y + 1, -6.5);
    private static final String TAG = "soulless_renegade";
    private static final DustParticleOptions SMOKE = new DustParticleOptions(new Vector3f(0.1F, 0.03F, 0.08F), 2.2F);

    /** A fight under way: the player, his entity, the boss bar, where it stands (pull -> talk -> fight -> end). */
    private static final class Fight {
        final UUID player;
        RenegadeEntity him;
        final ServerBossEvent bar = new ServerBossEvent(Component.translatable("entity.soulless.renegade"), BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.NOTCHED_10);
        int stage;   // 0: being pulled in, 1: in the dark, talking and fighting, 2: over
        int t;
        boolean won;

        Fight(UUID player) {
            this.player = player;
        }
    }

    private static final Map<UUID, Fight> FIGHTS = new HashMap<>();

    public static boolean in(Player p) {
        return p.level().dimension() == LEVEL;
    }

    /** Called when Kitsune has reforged the blade for the oath: he will come for it. */
    public static void schedule(ServerPlayer p, int delay) {
        CompoundTag f = DialogueContext.playerFlags(p);
        if (f.getBoolean(DEFEATED)) return;
        f.putLong(DUE, p.serverLevel().getGameTime() + delay);
    }

    public static void scheduleFirst(ServerPlayer p) {
        schedule(p, FIRST_DELAY);
    }

    private static void line(ServerPlayer p, String key) {
        p.sendSystemMessage(Component.translatable("chat.soulless.renegade_says", Component.translatable(key)).withStyle(ChatFormatting.DARK_RED));
    }

    /** Start the meeting now (also /soulless renegade). */
    public static void begin(ServerPlayer p) {
        if (FIGHTS.containsKey(p.getUUID()) || in(p) || com.soulless.world.Astral.in(p)) return;
        CompoundTag d = new CompoundTag();
        d.putString("Dim", p.level().dimension().location().toString());
        d.putDouble("X", p.getX());
        d.putDouble("Y", p.getY());
        d.putDouble("Z", p.getZ());
        p.getPersistentData().put(TAG, d);
        DialogueContext.playerFlags(p).remove(DUE);
        FIGHTS.put(p.getUUID(), new Fight(p.getUUID()));
        line(p, "dialogue.soulless.renegade.call");
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.HOSTILE, 1, 0.6F);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        Fight f = FIGHTS.get(p.getUUID());
        if (f != null) {
            fightTick(p, f);
            return;
        }
        if (in(p)) {   // stranded here with no fight (a crash, a relog): back home
            leave(p);
            return;
        }
        if (p.tickCount % 20 != 0) return;
        CompoundTag flags = DialogueContext.playerFlags(p);
        long due = flags.getLong(DUE);
        if (due == 0 || p.serverLevel().getGameTime() < due || flags.getBoolean(DEFEATED)) return;
        // somewhere quiet: on the ground, awake, not in water, not just hurt, not in the astral
        if (!p.onGround() || p.isSleeping() || p.isInWater() || p.isCreative() || p.isSpectator()
                || com.soulless.world.Astral.in(p) || p.tickCount - p.getLastHurtByMobTimestamp() < 200) return;
        if (!KatanaOath.sworn(p)) return;
        begin(p);
    }

    private static void fightTick(ServerPlayer p, Fight f) {
        f.t++;
        ServerLevel level = p.serverLevel();
        if (f.stage == 0) {
            // dark smoke closes round the player, then the pull
            if (f.t % 2 == 0) level.sendParticles(SMOKE, p.getX(), p.getY() + 1, p.getZ(), 10, 1.2 - f.t * 0.012, 0.9, 1.2 - f.t * 0.012, 0);
            if (f.t == 40) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
            if (f.t == 70) {
                ServerLevel dark = p.server.getLevel(LEVEL);
                if (dark == null) {
                    end(p, f, false);
                    return;
                }
                prepare(dark);
                p.teleportTo(dark, ARRIVE.x, ARRIVE.y, ARRIVE.z, 180, 0);
                RenegadeEntity him = ModEntities.RENEGADE.get().create(dark);
                if (him == null) {
                    end(p, f, false);
                    return;
                }
                him.moveTo(HIM.x, HIM.y, HIM.z, 0, 0);
                dark.addFreshEntity(him);
                him.setFoe(p, 150);
                f.him = him;
                f.bar.addPlayer(p);
                f.stage = 1;
                f.t = 0;
            }
            return;
        }
        RenegadeEntity him = f.him;
        if (in(p) && p.getY() < FLOOR_Y - 8) {   // off the edge of the dark: back onto the floor
            p.teleportTo(ARRIVE.x, ARRIVE.y, ARRIVE.z);
            p.fallDistance = 0;
        }
        if (f.stage == 1) {
            if (him == null || !him.isAlive() || !in(p)) {
                end(p, f, false);
                return;
            }
            f.bar.setProgress(him.getHealth() / him.getMaxHealth());
            if (f.t == 25) him.talk();
            if (f.t == 30) line(p, "dialogue.soulless.renegade.intro1");
            if (f.t == 80) line(p, "dialogue.soulless.renegade.intro2");
            if (f.t == 130) line(p, "dialogue.soulless.renegade.intro3");
            if (him.defeated()) {
                f.won = true;
                f.stage = 2;
                f.t = 0;
                line(p, "dialogue.soulless.renegade.defeat1");
            }
            return;
        }
        // stage 2: the fight is over - his last words, then back
        if (f.won) {
            if (f.t == 60) line(p, "dialogue.soulless.renegade.defeat2");
            if (f.t == 110 && him != null) {
                level.sendParticles(SMOKE, him.getX(), him.getY() + 1, him.getZ(), 60, 0.5, 1, 0.5, 0);
                level.playSound(null, him.getX(), him.getY(), him.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1, 0.5F);
                him.discard();
            }
            if (f.t >= 140) end(p, f, true);
        } else if (f.t >= 50) {
            end(p, f, false);
        }
    }

    /** The fight is over: back where the player stood; beaten, he comes again later; won, it is done. */
    private static void end(ServerPlayer p, Fight f, boolean won) {
        Soulless.LOGGER.info("Renegade fight with {} over: {} (stage {}, his health {})", p.getName().getString(), won ? "won" : "lost",
                f.stage, f.him == null ? -1 : f.him.getHealth());
        FIGHTS.remove(p.getUUID());
        f.bar.removeAllPlayers();
        if (f.him != null && f.him.isAlive()) f.him.discard();
        CompoundTag flags = DialogueContext.playerFlags(p);
        if (won) {
            flags.putBoolean(DEFEATED, true);
            com.soulless.Achievements.grant(p, "renegade_beaten");
        } else {
            schedule(p, RETRY_DELAY);
        }
        leave(p);
        p.sendSystemMessage(Component.translatable(won ? "message.soulless.renegade_won" : "message.soulless.renegade_lost")
                .withStyle(won ? ChatFormatting.AQUA : ChatFormatting.GRAY));
    }

    private static void leave(ServerPlayer p) {
        CompoundTag d = p.getPersistentData().getCompound(TAG);
        p.getPersistentData().remove(TAG);
        ServerLevel home = null;
        if (d.contains("Dim")) home = p.server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(d.getString("Dim"))));
        if (home == null || home.dimension() == LEVEL) home = p.server.overworld();
        Vec3 to = d.contains("X") ? new Vec3(d.getDouble("X"), d.getDouble("Y"), d.getDouble("Z")) : Vec3.atBottomCenterOf(home.getSharedSpawnPos());
        p.fallDistance = 0;
        if (in(p)) p.teleportTo(home, to.x, to.y, to.z, p.getYRot(), 0);
        p.fallDistance = 0;
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false));
    }

    /** The floor of the dark astral, made the first time. */
    private static void prepare(ServerLevel dark) {
        BlockPos centre = new BlockPos(0, FLOOR_Y, 0);
        if (dark.getBlockState(centre).is(Blocks.BARRIER)) return;
        for (int x = -FLOOR_RADIUS; x <= FLOOR_RADIUS; x++) {
            for (int z = -FLOOR_RADIUS; z <= FLOOR_RADIUS; z++) {
                if (x * x + z * z <= FLOOR_RADIUS * FLOOR_RADIUS) dark.setBlock(centre.offset(x, 0, z), Blocks.BARRIER.defaultBlockState(), 2);
            }
        }
    }

    /** Nobody dies here: a blow that would kill leaves half a heart, and the Renegade has won this time. */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !in(p)) return;
        Fight f = FIGHTS.get(p.getUUID());
        if (event.getAmount() < p.getHealth() - 0.5F) return;
        event.setAmount(Math.max(0, p.getHealth() - 1));
        if (f != null && f.stage == 1) {
            f.stage = 2;
            f.won = false;
            f.t = 0;
            if (f.him != null) f.him.talk();
            line(p, "dialogue.soulless.renegade.win");
        }
    }

    /** Ambient: the edge of the dark marked by drifting ash, embers falling round the player. */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != LEVEL) return;
        if (level.getGameTime() % 4 != 0) return;
        for (ServerPlayer p : level.players()) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ASH, p.getX(), p.getY() + 3, p.getZ(), 8, 6, 2, 6, 0);
            double a = level.random.nextDouble() * Math.PI * 2;
            level.sendParticles(SMOKE, Math.cos(a) * FLOOR_RADIUS, FLOOR_Y + 1 + level.random.nextDouble() * 3, Math.sin(a) * FLOOR_RADIUS, 1, 0, 0, 0, 0);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Fight f = FIGHTS.remove(event.getEntity().getUUID());
        if (f == null) return;
        f.bar.removeAllPlayers();
        if (f.him != null) f.him.discard();
        if (event.getEntity() instanceof ServerPlayer p) schedule(p, RETRY_DELAY);
    }

    @Nullable
    public static RenegadeEntity fighting(Player p) {
        Fight f = FIGHTS.get(p.getUUID());
        return f == null ? null : f.him;
    }

    private RenegadeEncounter() {}
}
