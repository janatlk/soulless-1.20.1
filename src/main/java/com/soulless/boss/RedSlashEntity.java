package com.soulless.boss;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The red slash the Renegade leaves where he cut during his blinking steps: a glowing streak hanging in the air that
 * bites {@code delay} ticks later - whoever stands in its line takes the blow (parryable: it is his blow). It stands on
 * the ground under the cut; drawn on the clients as the katana's own red smear (client.KatanaTrails) when it bites.
 */
public class RedSlashEntity extends Entity {
    private static final EntityDataAccessor<Float> ROLL = SynchedEntityData.defineId(RedSlashEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DELAY = SynchedEntityData.defineId(RedSlashEntity.class, EntityDataSerializers.INT);
    /** The cut's line is this high over the ground it stands on. */
    private static final double HEIGHT = 1.1;
    private static final double HALF_LENGTH = 1.4, REACH = 1.0;

    @Nullable private UUID owner, target;
    private int delay = 10;
    private float damage = 3;

    public RedSlashEntity(EntityType<? extends RedSlashEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** At {@code at}, across the facing {@code yaw}, tilted by {@code roll} degrees. */
    public void setup(RenegadeEntity from, Player at, Vec3 pos, float yaw, float roll, int delay, float damage) {
        owner = from.getUUID();
        target = at.getUUID();
        moveTo(pos.x, pos.y, pos.z, yaw, 0);
        entityData.set(ROLL, roll);
        entityData.set(DELAY, delay);
        this.delay = delay;
        this.damage = damage;
    }

    public boolean from(RenegadeEntity r) {
        return r.getUUID().equals(owner);
    }

    public float roll() {
        return entityData.get(ROLL);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(ROLL, 0F);
        entityData.define(DELAY, 10);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            // the bite: the katana's red smear, swept the way his cut went
            if (tickCount == entityData.get(DELAY)) {
                int kind = roll() < -20 ? 0 : 1;
                com.soulless.client.KatanaTrails.add(this, kind, 2.0F);
                com.soulless.client.KatanaTrails.whoosh(this, kind);
            }
            return;
        }
        if (!(level() instanceof ServerLevel level)) return;
        if (tickCount == delay) {
            Entity o = owner == null ? null : level.getEntity(owner);
            Player p = target == null ? null : level.getPlayerByUUID(target);
            if (o instanceof RenegadeEntity r && p != null && inLine(p)) r.strikeAt(p, p.position(), 1, damage);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 0.7F, 1.6F);
        }
        if (tickCount >= delay + 12) discard();   // long enough for the smear to fade on the clients
    }

    /** Is the player's body within reach of the slash's line? */
    private boolean inLine(Player p) {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        Vec3 along = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 rel = p.position().add(0, p.getBbHeight() * 0.5, 0).subtract(position().add(0, HEIGHT, 0));
        double a = rel.dot(along);
        Vec3 off = rel.subtract(along.scale(Mth.clamp(a, -HALF_LENGTH, HALF_LENGTH)));
        return off.length() <= REACH + 0.4;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
