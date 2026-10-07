package com.soulless.npc;

import com.soulless.Soulless;
import com.soulless.item.KatanaParry;
import com.soulless.item.SoulKatanaItem;
import com.soulless.registry.ModEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The sensei of the cherry-grove dojo (world.SenseiDojoPiece). Talks (data/soulless/dialogues/sensei.json) and leads
 * the training (SenseiQuest). His sparring: he faces the student and strikes with a wooden blade, each blow announced
 * by a glint and a hiss - first slowly, faster with every parry. A parried blow stuns him a moment; a missed one stings
 * (half a heart). {@link #SPAR_GOAL} parries and the parry is learnt. Walking off, lowering the katana or speaking to
 * him pauses it.
 */
public class SenseiEntity extends NpcEntity {
    public static final int SPAR_GOAL = 5;
    private static final double SPAR_REACH = 3.5, SPAR_LEAVE = 10;
    private static final Set<UUID> SPARRING = new HashSet<>();

    @Nullable private UUID sparWith;
    private int sparTimer, sparParries, sparNag;

    public SenseiEntity(EntityType<? extends SenseiEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public ResourceLocation dialogue() {
        return Soulless.id("sensei");
    }

    public static boolean sparring(Player p) {
        return SPARRING.contains(p.getUUID());
    }

    /** Ticks until his next blow lands (-1: not sparring). */
    public int ticksToStrike() {
        return sparWith == null ? -1 : sparTimer;
    }

    void startSpar(ServerPlayer p) {
        sparWith = p.getUUID();
        SPARRING.add(p.getUUID());
        sparParries = 0;
        sparTimer = 50;
        SenseiQuest.syncParry(p);
        SenseiQuest.say(p, "dialogue.soulless.sensei.spar.start", false);
    }

    private void stopSpar(@Nullable Player p, @Nullable String line) {
        if (sparWith != null) SPARRING.remove(sparWith);
        sparWith = null;
        if (p instanceof ServerPlayer sp) {
            stopTalking(sp);
            SenseiQuest.syncParry(sp);
            if (line != null) SenseiQuest.say(sp, line, false);
        }
    }

    /** Speaking to him mid-sparring pauses it (the conversation can start it again). */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (sparWith != null && sparWith.equals(player.getUUID()) && hand == InteractionHand.MAIN_HAND) {
            if (!level().isClientSide) stopSpar(player, "dialogue.soulless.sensei.spar.pause");
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void tick() {
        super.tick();
        if (sparWith != null && level() instanceof ServerLevel level) sparTick(level);
    }

    private void sparTick(ServerLevel level) {
        Player p = level.getPlayerByUUID(sparWith);
        if (!(p instanceof ServerPlayer player) || !p.isAlive() || p.distanceTo(this) > SPAR_LEAVE) {
            stopSpar(p, "dialogue.soulless.sensei.spar.abandon");
            return;
        }
        startTalking(player);   // stand still, face the student
        if (hasEffect(ModEffects.STUN.get())) return;   // reeling from a parry
        if (sparNag > 0) sparNag--;
        if (!(player.getMainHandItem().getItem() instanceof SoulKatanaItem)) {
            if (sparNag == 0) {
                SenseiQuest.say(player, "dialogue.soulless.sensei.spar.weapon", true);
                sparNag = 60;
            }
            return;
        }
        if (player.distanceTo(this) > SPAR_REACH) {
            if (sparNag == 0) {
                SenseiQuest.say(player, "dialogue.soulless.sensei.spar.closer", true);
                sparNag = 60;
            }
            return;
        }
        sparTimer--;
        int windup = Math.max(8, 20 - sparParries * 3);
        if (sparTimer == windup) {
            // the tell: a glint over his shoulder and the hiss of a blade drawn back
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + 2.1, getZ(), 6, 0.2, 0.1, 0.2, 0.1);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.NEUTRAL, 1, 1.5F);
        }
        if (sparTimer > 0) return;
        swing(InteractionHand.MAIN_HAND);   // a swung blow: the parry can catch it
        Vec3 mid = position().add(0, 1.3, 0).lerp(player.position().add(0, 1.2, 0), 0.6);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, mid.x, mid.y, mid.z, 1, 0, 0, 0, 0);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 0.7F, 1.3F);
        int before = KatanaParry.caughtTotal(player);
        player.hurt(damageSources().mobAttack(this), 1.0F);
        if (KatanaParry.caughtTotal(player) > before) {
            sparParries++;
            if (sparParries >= SPAR_GOAL) {
                stopSpar(player, null);
                SenseiQuest.learnParry(player);
                return;
            }
            SenseiQuest.say(player, "dialogue.soulless.sensei.spar.good" + (sparParries % 3), true);
        } else {
            SenseiQuest.say(player, "dialogue.soulless.sensei.spar.miss" + random.nextInt(3), true);
        }
        sparTimer = 34 + random.nextInt(20);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (sparWith != null) SPARRING.remove(sparWith);
        super.remove(reason);
    }
}
