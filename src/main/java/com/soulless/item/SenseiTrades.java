package com.soulless.item;

import com.soulless.Soulless;
import com.soulless.registry.ModItems;
import com.soulless.registry.ModVillagers;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The sensei's trades. First the training: sell him 16 rotten flesh (1 emerald), 16 bones (1), 12 gunpowder (2) and
 * 16 string (2). Once each of the four has been made at least once, he offers the Scroll of Parrying for 16 emeralds
 * - once: after it is sold, it is gone for good from this sensei.
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class SenseiTrades {
    private static final String TAG = "soulless_sensei";
    private static final Item[] LESSONS = {Items.ROTTEN_FLESH, Items.BONE, Items.GUNPOWDER, Items.STRING};
    private static final int[] COUNT = {16, 16, 12, 16}, PAY = {1, 1, 2, 2};
    public static final int SCROLL_PRICE = 16;

    public static boolean isSensei(AbstractVillager v) {
        return v instanceof Villager villager && villager.getVillagerData().getProfession() == ModVillagers.SENSEI.get();
    }

    private static CompoundTag data(AbstractVillager v) {
        CompoundTag root = v.getPersistentData();
        if (!root.contains(TAG)) root.put(TAG, new CompoundTag());
        return root.getCompound(TAG);
    }

    /** Make sure the sensei offers his lessons (once, when he first appears in the world). */
    private static void ensure(Villager v) {
        CompoundTag d = data(v);
        if (d.getBoolean("init")) return;
        MerchantOffers offers = v.getOffers();
        offers.clear();
        for (int i = 0; i < LESSONS.length; i++) {
            offers.add(new MerchantOffer(new ItemStack(LESSONS[i], COUNT[i]), new ItemStack(Items.EMERALD, PAY[i]), 16, 2, 0.05F));
        }
        d.putBoolean("init", true);
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof Villager v && isSensei(v)) ensure(v);
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide && event.getTarget() instanceof Villager v && isSensei(v)) ensure(v);
    }

    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        AbstractVillager v = event.getAbstractVillager();
        if (!isSensei(v) || !(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag d = data(v);
        MerchantOffer offer = event.getMerchantOffer();
        if (offer.getResult().is(ModItems.PARRY_SCROLL.get())) {
            // the scroll is handed over only once
            d.putBoolean("scroll_sold", true);
            v.getOffers().remove(offer);
            resend(player, v);
            return;
        }
        for (int i = 0; i < LESSONS.length; i++) {
            if (offer.getCostA().is(LESSONS[i])) d.putBoolean("lesson" + i, true);
        }
        boolean all = true;
        for (int i = 0; i < LESSONS.length; i++) all &= d.getBoolean("lesson" + i);
        if (all && !d.getBoolean("scroll_offered") && !d.getBoolean("scroll_sold")) {
            d.putBoolean("scroll_offered", true);
            v.getOffers().add(new MerchantOffer(new ItemStack(Items.EMERALD, SCROLL_PRICE), new ItemStack(ModItems.PARRY_SCROLL.get()), 1, 10, 0));
            resend(player, v);
            player.sendSystemMessage(Component.translatable("message.soulless.sensei_ready").withStyle(ChatFormatting.AQUA));
            v.level().playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1, 1);
        }
    }

    /** Refresh the open trading screen with the changed offers. */
    private static void resend(ServerPlayer player, AbstractVillager v) {
        if (player.containerMenu instanceof MerchantMenu menu && v instanceof Villager villager) {
            player.sendMerchantOffers(menu.containerId, v.getOffers(), villager.getVillagerData().getLevel(), villager.getVillagerXp(),
                    villager.showProgressBar(), villager.canRestock());
        }
    }

    private SenseiTrades() {}
}
