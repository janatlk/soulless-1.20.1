package com.soulless.world;

import com.soulless.Soulless;
import com.soulless.item.KatanaOath;
import com.soulless.npc.KitsuneEntity;
import com.soulless.pet.TamedFox;
import com.soulless.registry.ModEntities;
import com.soulless.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The astral: the dream where Kitsune sleeps (STORY_KATANA.md, act 3). An empty starry sky and nothing else - an
 * invisible floor to walk on (barrier blocks), Kitsune lying at the middle.
 * <ul>
 * <li>Getting there: sleep under a roof with the Broken Legendary Katana on you while your tamed white fox sleeps
 * beside the bed (within 4 blocks). Without the blade: "you feel you've forgotten something".</li>
 * <li>Only the blade comes along; everything else is kept (player data) and given back on waking. In the dream nobody
 * hungers or gets hurt, nothing can be built or broken; falling off the floor puts you back.</li>
 * <li>Waking: back beside the bed; alone on the server, the night has passed.</li>
 * </ul>
 * Data: data/soulless/dimension/astral.json, dimension_type/astral.json.
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class Astral {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, Soulless.id("astral"));
    private static final int FLOOR_Y = 99, FLOOR_RADIUS = 40;
    private static final Vec3 ARRIVE = new Vec3(0.5, FLOOR_Y + 1, 9.5);
    private static final Vec3 KITSUNE = new Vec3(0.5, FLOOR_Y + 1, -3.5);
    private static final String TAG = "soulless_astral";
    /** The sleep timer at which the dream takes hold (a full night's sleep is 100). */
    private static final int DREAM_AT = 60;

    public static boolean in(Player p) {
        return p.level().dimension() == LEVEL;
    }

    private static boolean hasBlade(Player p) {
        return p.getInventory().contains(new ItemStack(ModItems.BROKEN_KATANA.get()));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        if (in(p)) {
            p.getFoodData().setFoodLevel(20);
            p.clearFire();
            if (p.getY() < FLOOR_Y - 12) p.teleportTo(ARRIVE.x, ARRIVE.y, ARRIVE.z);
            if (!p.getPersistentData().contains(TAG) && !p.isCreative()) leave(p);   // came by some other way
            return;
        }
        if (p.isSleeping() && p.getSleepTimer() == DREAM_AT) tryDream(p);
    }

    private static void tryDream(ServerPlayer p) {
        if (KatanaOath.chose(p)) return;
        BlockPos bed = p.getSleepingPos().orElse(null);
        if (bed == null) return;
        Level level = p.level();
        boolean fox = !level.getEntitiesOfClass(TamedFox.class, new AABB(bed).inflate(4), f -> f.isOwner(p) && f.isSleeping()).isEmpty();
        if (!fox || level.canSeeSky(bed.above())) return;   // "sleep is not deep under the open sky"
        if (!hasBlade(p)) {
            p.displayClientMessage(Component.translatable("message.soulless.astral_forgot").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
            return;
        }
        enter(p, bed);
    }

    /** Into the dream, carrying only the broken blade; {@code bed}: where to wake (null: where the player stood). */
    public static void enter(ServerPlayer p, @Nullable BlockPos bed) {
        ServerLevel astral = p.server.getLevel(LEVEL);
        if (astral == null || in(p)) return;
        prepare(astral);
        var inv = p.getInventory();
        ItemStack blade = ItemStack.EMPTY;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(ModItems.BROKEN_KATANA.get())) {
                blade = inv.removeItemNoUpdate(i);
                break;
            }
        }
        CompoundTag d = new CompoundTag();
        d.put("Inv", inv.save(new ListTag()));
        d.putString("Dim", p.level().dimension().location().toString());
        if (bed != null) d.put("Bed", NbtUtils.writeBlockPos(bed));
        d.putDouble("X", p.getX());
        d.putDouble("Y", p.getY());
        d.putDouble("Z", p.getZ());
        d.putFloat("Yaw", p.getYRot());
        p.getPersistentData().put(TAG, d);
        inv.clearContent();
        if (!blade.isEmpty()) {
            inv.setItem(0, blade);
            inv.selected = 0;
        }
        if (p.isSleeping()) p.stopSleepInBed(true, true);
        p.teleportTo(astral, ARRIVE.x, ARRIVE.y, ARRIVE.z, 180, 0);
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
        astral.playSound(null, ARRIVE.x, ARRIVE.y, ARRIVE.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5F, 0.6F);
    }

    /** Waking up: back where the player slept, with everything they left behind (and whatever they bring back). */
    public static void leave(ServerPlayer p) {
        CompoundTag d = p.getPersistentData().getCompound(TAG);
        ServerLevel home = null;
        if (d.contains("Dim")) home = p.server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(d.getString("Dim"))));
        if (home == null || home.dimension() == LEVEL) home = p.server.overworld();
        var inv = p.getInventory();
        List<ItemStack> brought = new ArrayList<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (!inv.getItem(i).isEmpty()) brought.add(inv.getItem(i).copy());
        }
        inv.clearContent();
        if (d.contains("Inv")) inv.load(d.getList("Inv", 10));
        p.getPersistentData().remove(TAG);

        Vec3 to = null;
        float yaw = d.getFloat("Yaw");
        if (d.contains("Bed")) {
            BlockPos bed = NbtUtils.readBlockPos(d.getCompound("Bed"));
            BlockState s = home.getBlockState(bed);
            if (s.getBlock() instanceof BedBlock) {
                to = BedBlock.findStandUpPosition(EntityType.PLAYER, home, bed, s.getValue(BedBlock.FACING), yaw).orElse(null);
            }
        }
        if (to == null && d.contains("X")) to = new Vec3(d.getDouble("X"), d.getDouble("Y"), d.getDouble("Z"));
        if (to == null) to = Vec3.atBottomCenterOf(home.getSharedSpawnPos());
        p.teleportTo(home, to.x, to.y, to.z, yaw, 0);
        for (ItemStack s : brought) {
            if (!inv.add(s)) p.drop(s, false);
        }
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false));
        // alone on the server: the night went by while they dreamt
        if (home.dimension() == Level.OVERWORLD && p.server.getPlayerCount() == 1 && !home.isDay()) {
            home.setDayTime((home.getDayTime() / 24000 + 1) * 24000);
        }
    }

    /** The floor and Kitsune, made the first time anyone dreams. */
    private static void prepare(ServerLevel astral) {
        BlockPos centre = new BlockPos(0, FLOOR_Y, 0);
        if (!astral.getBlockState(centre).is(Blocks.BARRIER)) {
            for (int x = -FLOOR_RADIUS; x <= FLOOR_RADIUS; x++) {
                for (int z = -FLOOR_RADIUS; z <= FLOOR_RADIUS; z++) {
                    astral.setBlock(centre.offset(x, 0, z), Blocks.BARRIER.defaultBlockState(), 2);
                }
            }
        }
        if (astral.getEntitiesOfClass(KitsuneEntity.class, new AABB(centre).inflate(64)).isEmpty()) {
            KitsuneEntity k = ModEntities.KITSUNE.get().create(astral);
            if (k != null) {
                k.moveTo(KITSUNE.x, KITSUNE.y, KITSUNE.z, 0, 0);   // facing south, toward where the sleeper arrives
                k.setYHeadRot(0);
                k.setYBodyRot(0);
                k.setHome(BlockPos.containing(KITSUNE));
                astral.addFreshEntity(k);
            }
        }
    }

    // ---- nothing hurts, nothing changes in the dream

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof Player p && in(p)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (in(event.getPlayer()) && !event.getPlayer().isCreative()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player p && in(p) && !p.isCreative()) event.setCanceled(true);
    }

    private Astral() {}
}
