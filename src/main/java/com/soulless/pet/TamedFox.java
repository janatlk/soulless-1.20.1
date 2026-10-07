package com.soulless.pet;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

/**
 * A white fox tamed with sweet berries (see {@link FoxTaming}). It is a vanilla fox in looks - the model, the snow
 * texture, the sitting pose and the curled-up sleeping pose - with a pet's mind instead of a wild one:
 * <ul>
 * <li>follows its owner (runs to catch up, teleports to them when left far behind);</li>
 * <li>right click by the owner: sit / follow (a sitting fox stays where it is);</li>
 * <li>when a bed is near and so is the owner, it climbs onto the bed and sleeps there, curled up - on the owner's
 * own bed while they sleep; it gets up when the owner walks away;</li>
 * <li>sweet berries heal it; its owner's blows (sweeps, abilities) never hurt it; it hunts nothing, flees nobody,
 * never despawns, picks nothing up.</li>
 * </ul>
 */
public class TamedFox extends Fox {
    private static final EntityDataAccessor<Boolean> LYING = SynchedEntityData.defineId(TamedFox.class, EntityDataSerializers.BOOLEAN);

    @Nullable private UUID owner;
    private boolean orderedToSit;

    public TamedFox(EntityType<? extends TamedFox> type, Level level) {
        super(type, level);
        setCanPickUpLoot(false);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Fox.createAttributes().add(Attributes.MAX_HEALTH, 20);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(LYING, false);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();   // sets up the fox's own target goals, which its spawn / load code expects to exist
        goalSelector.removeAllGoals(g -> true);
        targetSelector.removeAllGoals(g -> true);
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new SitGoal());
        goalSelector.addGoal(2, new LieOnBedGoal());
        goalSelector.addGoal(3, new FollowOwnerGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /** The wild fox's hunting comes back on spawn / load: a pet hunts nothing. */
    private void noHunting() {
        targetSelector.removeAllGoals(g -> true);
        setTarget(null);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data, tag);
        setVariant(Type.SNOW);
        noHunting();
        return out;
    }

    // ---- owner

    public void tame(Player player) {
        owner = player.getUUID();
        setCanPickUpLoot(false);   // convertTo copied the wild fox's "picks things up"
        setVariant(Type.SNOW);
        noHunting();
        setHealth(getMaxHealth());
    }

    @Nullable
    public Player getOwner() {
        return owner == null ? null : level().getPlayerByUUID(owner);
    }

    public boolean isOwner(Player player) {
        return player.getUUID().equals(owner);
    }

    // ---- poses

    /** Curled up on a bed. The vanilla fox model and texture draw a sleeping fox from this. */
    @Override
    public boolean isSleeping() {
        return entityData.get(LYING);
    }

    private void setLying(boolean lying) {
        entityData.set(LYING, lying);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isOwner(player) || stack.is(Items.NAME_TAG)) return InteractionResult.PASS;   // a name tag works as usual
        if (stack.is(Items.SWEET_BERRIES) && getHealth() < getMaxHealth()) {
            if (!level().isClientSide) {
                heal(4);
                if (!player.getAbilities().instabuild) stack.shrink(1);
                playSound(SoundEvents.FOX_EAT, 1, 1);
                ((ServerLevel) level()).sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,
                        getX(), getY() + 0.7, getZ(), 2, 0.3, 0.2, 0.3, 0);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level().isClientSide) {
            orderedToSit = !orderedToSit;
            setLying(false);
            getNavigation().stop();
            player.displayClientMessage(Component.translatable(orderedToSit ? "message.soulless.fox_sit" : "message.soulless.fox_follow")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof Player p && isOwner(p)) return false;
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide) setLying(false);
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && getOwner() instanceof ServerPlayer p) p.sendSystemMessage(getCombatTracker().getDeathMessage());
        super.die(source);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;   // no breeding
    }

    @Nullable
    @Override
    public Fox getBreedOffspring(ServerLevel level, AgeableMob other) {
        return null;
    }

    // ---- saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putBoolean("OrderedToSit", orderedToSit);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        orderedToSit = tag.getBoolean("OrderedToSit");
        noHunting();
    }

    // ---- goals

    /** Ordered to sit: sit and stay. */
    private final class SitGoal extends Goal {
        SitGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return orderedToSit && !isInWaterOrBubble() && onGround();
        }

        @Override
        public boolean canContinueToUse() {
            return orderedToSit && !isInWaterOrBubble();
        }

        @Override
        public void start() {
            getNavigation().stop();
            setSitting(true);
        }

        @Override
        public void tick() {
            if (!isSitting()) setSitting(true);   // the fox's own tick drops the pose in water
        }

        @Override
        public void stop() {
            setSitting(false);
        }
    }

    /** Keep up with the owner; left far behind, appear next to them. */
    private final class FollowOwnerGoal extends Goal {
        private static final double START = 6, STOP = 2.5, TELEPORT = 14;
        private int recalc;

        FollowOwnerGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Nullable
        private Player owner() {
            Player p = getOwner();
            return p == null || p.isSpectator() || !p.isAlive() ? null : p;
        }

        @Override
        public boolean canUse() {
            Player p = owner();
            return !orderedToSit && p != null && distanceToSqr(p) > START * START;
        }

        @Override
        public boolean canContinueToUse() {
            Player p = owner();
            return !orderedToSit && p != null && distanceToSqr(p) > STOP * STOP;
        }

        @Override
        public void start() {
            recalc = 0;
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            Player p = owner();
            if (p == null) return;
            getLookControl().setLookAt(p, 10, getMaxHeadXRot());
            if (--recalc > 0) return;
            recalc = 10;
            if (distanceToSqr(p) >= TELEPORT * TELEPORT) teleportNear(p);
            else getNavigation().moveTo(p, 1.4);
        }

        private void teleportNear(Player p) {
            BlockPos base = p.blockPosition();
            for (int i = 0; i < 10; i++) {
                int dx = random.nextIntBetweenInclusive(-3, 3), dz = random.nextIntBetweenInclusive(-3, 3);
                if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
                for (int dy = 1; dy >= -1; dy--) {
                    BlockPos pos = base.offset(dx, dy, dz);
                    if (level().getBlockState(pos.below()).isFaceSturdy(level(), pos.below(), net.minecraft.core.Direction.UP)
                            && level().noCollision(TamedFox.this, getBoundingBox().move(Vec3.atBottomCenterOf(pos).subtract(position())))) {
                        moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, getYRot(), getXRot());
                        getNavigation().stop();
                        return;
                    }
                }
            }
        }
    }

    /**
     * A bed near both the fox and its owner: climb onto it and sleep, curled up. While the owner sleeps, their own bed
     * (the foot end). Gets up when the owner walks off, is ordered to sit, or the bed is gone.
     */
    private final class LieOnBedGoal extends Goal {
        private static final int SEARCH = 8;
        private static final double OWNER_NEAR = 10;
        @Nullable private BlockPos bed;
        private int tries;
        private int cooldown;

        LieOnBedGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (orderedToSit || --cooldown > 0) return false;
            cooldown = 40;
            Player p = getOwner();
            if (p == null || distanceToSqr(p) > OWNER_NEAR * OWNER_NEAR) return false;
            bed = findBed(p);
            return bed != null;
        }

        @Nullable
        private BlockPos findBed(Player p) {
            if (p.isSleeping() && p.getSleepingPos().isPresent()) {
                BlockPos head = p.getSleepingPos().get();
                BlockState s = level().getBlockState(head);
                if (s.getBlock() instanceof BedBlock) {
                    BlockPos foot = head.relative(s.getValue(BedBlock.FACING).getOpposite());
                    if (free(foot)) return foot;
                }
            }
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            BlockPos at = blockPosition();
            for (BlockPos pos : BlockPos.betweenClosed(at.offset(-SEARCH, -2, -SEARCH), at.offset(SEARCH, 2, SEARCH))) {
                if (!free(pos) || pos.distSqr(p.blockPosition()) > OWNER_NEAR * OWNER_NEAR) continue;
                double d = pos.distSqr(at);
                // the foot end first: the head end is where the owner lies down
                if (level().getBlockState(pos).getOptionalValue(BedBlock.PART).orElse(null) == BedPart.FOOT) d -= 4;
                if (d < bestD) {
                    bestD = d;
                    best = pos.immutable();
                }
            }
            return best;
        }

        /** A bed block with room above it and no other fox on it. */
        private boolean free(BlockPos pos) {
            if (!level().getBlockState(pos).is(BlockTags.BEDS) || !level().isEmptyBlock(pos.above())) return false;
            return level().getEntitiesOfClass(TamedFox.class, new net.minecraft.world.phys.AABB(pos).expandTowards(0, 1, 0),
                    f -> f != TamedFox.this).isEmpty();
        }

        private Vec3 spot() {
            return new Vec3(bed.getX() + 0.5, bed.getY() + 0.5625, bed.getZ() + 0.5);
        }

        @Override
        public boolean canContinueToUse() {
            Player p = getOwner();
            if (orderedToSit || bed == null || p == null || !level().getBlockState(bed).is(BlockTags.BEDS)) return false;
            if (bed.distSqr(p.blockPosition()) > (OWNER_NEAR + 2) * (OWNER_NEAR + 2)) return false;
            return isSleeping() || tries < 200;
        }

        @Override
        public void start() {
            tries = 0;
            getNavigation().moveTo(bed.getX() + 0.5, bed.getY() + 1, bed.getZ() + 0.5, 1.1);
        }

        @Override
        public void tick() {
            if (isSleeping()) {
                getNavigation().stop();
                return;
            }
            tries++;
            Vec3 spot = spot();
            double flat = (getX() - spot.x) * (getX() - spot.x) + (getZ() - spot.z) * (getZ() - spot.z);
            if (flat < 0.5 && Math.abs(getY() - spot.y) < 0.5) {
                // on the bed: settle in the middle and curl up
                getNavigation().stop();
                moveTo(spot.x, spot.y, spot.z, getYRot(), 0);
                setDeltaMovement(Vec3.ZERO);
                setLying(true);
                playSound(SoundEvents.FOX_SLEEP, 0.6F, 1);
            } else if (flat < 2.5) {
                getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, 1.0);
                if (getY() < spot.y - 0.2 && onGround()) getJumpControl().jump();
            } else if (tries % 20 == 0) {
                getNavigation().moveTo(bed.getX() + 0.5, bed.getY() + 1, bed.getZ() + 0.5, 1.1);
            }
        }

        @Override
        public void stop() {
            setLying(false);
            bed = null;
            cooldown = 100;   // just got up: don't lie straight back down
        }
    }
}
