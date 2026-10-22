package com.soulless.forge;

import com.soulless.item.SoulVesselItem;
import com.soulless.registry.ModBlocks;
import com.soulless.upgrade.SoulVessels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * What lies on the Soul Anvil. Idle; forging (a recipe picked on the screen: its item hovers there as a ghost and
 * fills up as the player lays each ingredient on with right clicks, and pays the souls with a vessel); or holding
 * an artifact laid on it to be upgraded or melted down. Synced to clients for the renderer (client.SoulAnvilRenderer).
 */
public class SoulAnvilBlockEntity extends BlockEntity {
    @Nullable private String recipe;
    /** Items laid on so far, per cost of the recipe. */
    private int[] filled = new int[0];
    private int soulsPaid;
    /** An artifact laid on to be worked (EMPTY: none). */
    private ItemStack held = ItemStack.EMPTY;
    /** Ticks since the last change, for the renderer's flourishes. */
    public int pulse;

    public SoulAnvilBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SOUL_ANVIL_BE.get(), pos, state);
    }

    @Nullable
    public SoulAnvilRecipes.Recipe recipe() {
        return recipe == null ? null : SoulAnvilRecipes.byId(recipe);
    }

    public int filled(int cost) {
        return cost < filled.length ? filled[cost] : 0;
    }

    public int soulsPaid() {
        return soulsPaid;
    }

    public ItemStack held() {
        return held;
    }

    public boolean idle() {
        return recipe() == null && held.isEmpty();
    }

    // ------------------------------------------------------------------ forging

    /** Starts forging (from the screen). Anything already laid on comes back first. */
    public void choose(ServerPlayer player, SoulAnvilRecipes.Recipe r) {
        cancel(player);
        if (!held.isEmpty()) return;
        recipe = r.id();
        filled = new int[r.costs().size()];
        soulsPaid = 0;
        changed();
        level.playSound(null, worldPosition, SoundEvents.SOUL_ESCAPE, SoundSource.BLOCKS, 0.8F, 1.4F);
    }

    /** Right click while forging: lay on what the held stack can give (or the souls of a vessel). True if anything went in. */
    public boolean feed(ServerPlayer player, ItemStack hand) {
        SoulAnvilRecipes.Recipe r = recipe();
        if (r == null || hand.isEmpty()) return false;
        boolean any = false;
        if (hand.getItem() instanceof SoulVesselItem && soulsPaid < r.souls()) {
            int pay = player.getAbilities().instabuild ? r.souls() - soulsPaid
                    : Math.min(r.souls() - soulsPaid, SoulVesselItem.souls(hand));
            if (!player.getAbilities().instabuild) SoulVesselItem.set(hand, SoulVesselItem.souls(hand) - pay);
            soulsPaid += pay;
            any = pay > 0;
            if (any) ((ServerLevel) level).sendParticles(ParticleTypes.SOUL, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1,
                    worldPosition.getZ() + 0.5, 10, 0.2, 0.1, 0.2, 0.02);
        } else {
            for (int i = 0; i < r.costs().size(); i++) {
                var c = r.costs().get(i);
                if (!hand.is(c.item().get()) || filled[i] >= c.count()) continue;
                int put = Math.min(c.count() - filled[i], hand.getCount());
                filled[i] += put;
                if (!player.getAbilities().instabuild) hand.shrink(put);
                any = true;
                break;
            }
        }
        if (!any) return false;
        level.playSound(null, worldPosition, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.35F, 1.6F + level.random.nextFloat() * 0.3F);
        if (complete()) finish();
        changed();
        return true;
    }

    public boolean complete() {
        SoulAnvilRecipes.Recipe r = recipe();
        if (r == null || soulsPaid < r.souls()) return false;
        for (int i = 0; i < r.costs().size(); i++) if (filled[i] < r.costs().get(i).count()) return false;
        return true;
    }

    private void finish() {
        SoulAnvilRecipes.Recipe r = recipe();
        recipe = null;
        filled = new int[0];
        soulsPaid = 0;
        ServerLevel sl = (ServerLevel) level;
        ItemEntity out = new ItemEntity(sl, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1, worldPosition.getZ() + 0.5, SoulAnvilRecipes.make(r));
        out.setDeltaMovement(0, 0.25, 0);
        out.setGlowingTag(true);
        sl.addFreshEntity(out);
        sl.playSound(null, worldPosition, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0F, 0.7F);
        sl.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 0.6F);
        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1, worldPosition.getZ() + 0.5,
                40, 0.3, 0.2, 0.3, 0.06);
    }

    /** Stops forging: every item laid on comes back and the souls go back into the vessels. */
    public void cancel(Player player) {
        SoulAnvilRecipes.Recipe r = recipe();
        if (r == null) return;
        for (int i = 0; i < r.costs().size() && i < filled.length; i++) {
            if (filled[i] > 0) give(player, new ItemStack(r.costs().get(i).item().get(), filled[i]));
        }
        if (soulsPaid > 0) SoulVessels.give(player, soulsPaid);
        recipe = null;
        filled = new int[0];
        soulsPaid = 0;
        changed();
    }

    // ------------------------------------------------------------------ an artifact on the anvil

    public void place(ItemStack stack) {
        held = stack;
        changed();
        level.playSound(null, worldPosition, SoundEvents.ARMOR_EQUIP_NETHERITE, SoundSource.BLOCKS, 1.0F, 0.8F);
    }

    public void takeBack(Player player) {
        if (held.isEmpty()) return;
        give(player, held);
        held = ItemStack.EMPTY;
        changed();
    }

    /** The artifact was changed in place (upgraded): resync. */
    public void heldChanged() {
        changed();
    }

    public void clearHeld() {
        held = ItemStack.EMPTY;
        changed();
    }

    /** Broken: everything on it drops. */
    public void dropAll() {
        if (!held.isEmpty()) net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ(), held);
        SoulAnvilRecipes.Recipe r = recipe();
        if (r != null) {
            for (int i = 0; i < r.costs().size() && i < filled.length; i++) {
                if (filled[i] > 0) net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY() + 1,
                        worldPosition.getZ(), new ItemStack(r.costs().get(i).item().get(), filled[i]));
            }
        }
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private void changed() {
        pulse = 0;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    // ------------------------------------------------------------------ save / sync

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (recipe != null) tag.putString("Recipe", recipe);
        tag.putIntArray("Filled", filled);
        tag.putInt("SoulsPaid", soulsPaid);
        if (!held.isEmpty()) tag.put("Held", held.save(new CompoundTag()));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        recipe = tag.contains("Recipe") ? tag.getString("Recipe") : null;
        filled = tag.getIntArray("Filled");
        soulsPaid = tag.getInt("SoulsPaid");
        held = tag.contains("Held") ? ItemStack.of(tag.getCompound("Held")) : ItemStack.EMPTY;
        if (recipe() != null && filled.length != recipe().costs().size()) filled = java.util.Arrays.copyOf(filled, recipe().costs().size());
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag t = new CompoundTag();
        saveAdditional(t);
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt.getTag() != null) load(pkt.getTag());
        pulse = 0;
    }
}
