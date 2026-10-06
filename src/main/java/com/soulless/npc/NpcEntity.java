package com.soulless.npc;

import com.soulless.npc.dialogue.DialogueManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

/**
 * The base of every talking character (the sensei and those to come). What a character gets for free:
 * <ul>
 * <li>right click opens its dialogue ({@link #dialogue()}, data/&lt;ns&gt;/dialogues/&lt;name&gt;.json);</li>
 * <li>flags of its own for the dialogue conditions ({@link #flags()}), saved with it;</li>
 * <li>a home it strolls around ({@link #setHome}), stops and faces whoever talks with it;</li>
 * <li>it cannot be hurt (except by /kill, the void and players in creative), never despawns.</li>
 * </ul>
 * A new character: a subclass with {@link #dialogue()}, an entity type in ModEntities (attributes:
 * {@link #createAttributes}), a renderer (client.NpcRenderer for a villager-shaped one) and its dialogue JSON.
 */
public abstract class NpcEntity extends PathfinderMob {
    private static final int HOME_RADIUS = 6;

    private CompoundTag flags = new CompoundTag();
    @Nullable private BlockPos home;
    @Nullable private UUID talkingTo;

    protected NpcEntity(EntityType<? extends NpcEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.5);
    }

    /** The dialogue file this character speaks: {@code soulless:sensei} = data/soulless/dialogues/sensei.json. */
    public abstract ResourceLocation dialogue();

    public CompoundTag flags() {
        return flags;
    }

    public void setHome(BlockPos pos) {
        home = pos.immutable();
        restrictTo(home, HOME_RADIUS);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new FaceListenerGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    // ---- talking

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.NAME_TAG) || stack.getItem() instanceof net.minecraft.world.item.SpawnEggItem) {
            return super.mobInteract(player, hand);
        }
        if (hand != InteractionHand.MAIN_HAND || !isAlive()) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) DialogueManager.open(sp, this);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    public void startTalking(Player player) {
        talkingTo = player.getUUID();
        getNavigation().stop();
    }

    public void stopTalking(Player player) {
        if (player.getUUID().equals(talkingTo)) talkingTo = null;
    }

    @Nullable
    private Player listener() {
        return talkingTo == null ? null : level().getPlayerByUUID(talkingTo);
    }

    /** Stand still and look at whoever is talking. */
    private final class FaceListenerGoal extends Goal {
        FaceListenerGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return listener() != null;
        }

        @Override
        public void start() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            Player p = listener();
            if (p != null) getLookControl().setLookAt(p, 30, 30);
        }
    }

    // ---- an immortal resident

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean creative = source.getEntity() instanceof Player p && p.getAbilities().instabuild;
        if (!creative && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    // ---- saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("NpcFlags", flags);
        if (home != null) tag.put("Home", NbtUtils.writeBlockPos(home));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        flags = tag.getCompound("NpcFlags");
        if (tag.contains("Home")) setHome(NbtUtils.readBlockPos(tag.getCompound("Home")));
    }
}
