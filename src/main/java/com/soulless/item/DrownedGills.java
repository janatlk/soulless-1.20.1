package com.soulless.item;

import com.soulless.Soulless;
import com.soulless.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Drowned One's Gills, a relic worn round the neck (Curios necklace slot, or anywhere in the inventory without
 * Curios). In water: the wearer breathes as a fish (Water Breathing) and swims half again as
 * fast. Out of it, they breathe as a fish too: the air runs out as it would under water, and once it is gone they
 * choke - until they are back in the water or out in the rain.
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class DrownedGills {
    public static final double SWIM_BONUS = 0.5;
    /** Near the Leviathan (anywhere in its arena) the gills drive the wearer on harder still. */
    public static final double HUNT_BONUS = 1.15;
    private static final double HUNT_RANGE = com.soulless.world.DrownedSoulsStructure.ARENA_R + 16;
    private static final UUID SWIM_ID = UUID.fromString("2a7d9c41-5e1b-4f33-a8d0-6b9e1c4f7a25");
    private static final UUID HUNT_ID = UUID.fromString("7c1e4b2a-93d5-4f60-b1a8-2e6d9f0c5b47");
    private static final boolean CURIOS = ModList.get().isLoaded("curios");
    /** The air each wearer has left on land (the game would refill it every tick otherwise). */
    private static final Map<UUID, Integer> AIR = new HashMap<>();

    /** Durability: one point for every 10 s it works in the water (about 3 hours); mended with prismarine crystals
     * at an anvil. Worn out (one point left) it stops working until mended, but never breaks. */
    public static final int DURABILITY = 1080;

    /** The gills the player wears (Curios necklace slot, or anywhere in the inventory), not worn out; or empty. */
    public static ItemStack stack(Player player) {
        ItemStack s = CURIOS ? CuriosCompat.find(player, ModItems.DROWNED_GILLS.get())
                : player.getInventory().items.stream().filter(i -> i.is(ModItems.DROWNED_GILLS.get())).findFirst().orElse(ItemStack.EMPTY);
        return s.isEmpty() || s.getDamageValue() >= s.getMaxDamage() - 1 ? ItemStack.EMPTY : s;
    }

    public static boolean worn(Player player) {
        return !stack(player).isEmpty();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        var swim = p.getAttribute(ForgeMod.SWIM_SPEED.get());
        boolean on = worn(p) && !p.isCreative() && !p.isSpectator();
        boolean wet = p.isInWater();
        if (swim != null) {
            boolean has = swim.getModifier(SWIM_ID) != null;
            if (on && wet && !has) swim.addTransientModifier(new AttributeModifier(SWIM_ID, "Drowned gills", SWIM_BONUS, AttributeModifier.Operation.MULTIPLY_BASE));
            else if ((!on || !wet) && has) swim.removeModifier(SWIM_ID);
            // the Leviathan near: checked twice a second
            if (p.tickCount % 10 == 0) {
                boolean hunt = on && wet && !p.level().getEntitiesOfClass(com.soulless.boss.LeviathanEntity.class,
                        p.getBoundingBox().inflate(HUNT_RANGE), e -> e.isAlive()).isEmpty();
                boolean hasHunt = swim.getModifier(HUNT_ID) != null;
                if (hunt && !hasHunt) swim.addTransientModifier(new AttributeModifier(HUNT_ID, "Drowned gills: the Leviathan near", HUNT_BONUS, AttributeModifier.Operation.MULTIPLY_BASE));
                else if (!hunt && hasHunt) swim.removeModifier(HUNT_ID);
            }
        }
        if (!on) {
            AIR.remove(p.getUUID());
            return;
        }
        // wear: a point every 10 s in the water
        if (wet && p.tickCount % 200 == 0) {
            ItemStack g = stack(p);
            g.setDamageValue(g.getDamageValue() + 1);
            if (g.getDamageValue() >= g.getMaxDamage() - 1)
                p.displayClientMessage(Component.translatable("message.soulless.gills_worn").withStyle(ChatFormatting.RED), true);
        }
        if (wet || p.isInWaterOrRain()) {
            p.setAirSupply(p.getMaxAirSupply());
            AIR.put(p.getUUID(), p.getMaxAirSupply());
            if (wet && p.tickCount % 80 == 0) p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 260, 0, true, false, true));
            return;
        }
        // on dry land: the breath runs out as a fish's does
        int air = AIR.getOrDefault(p.getUUID(), p.getMaxAirSupply()) - 1;
        if (air <= -20) {
            air = 0;
            p.hurt(p.damageSources().drown(), 2.0F);
        }
        AIR.put(p.getUUID(), air);
        p.setAirSupply(air);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        AIR.remove(event.getEntity().getUUID());
    }

    /** The item itself. */
    public static class GillsItem extends Item {
        public GillsItem(Properties props) {
            super(props);
        }

        @Override
        public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
            return repair.is(net.minecraft.world.item.Items.PRISMARINE_CRYSTALS);
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.soulless.drowned_gills.desc").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
            tooltip.add(Component.translatable("tooltip.soulless.drowned_gills.water", Math.round(SWIM_BONUS * 100)).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip.soulless.drowned_gills.hunt", Math.round(HUNT_BONUS * 100)).withStyle(ChatFormatting.DARK_PURPLE));
            tooltip.add(Component.translatable("tooltip.soulless.drowned_gills.land").withStyle(ChatFormatting.RED));
            if (stack.getDamageValue() >= stack.getMaxDamage() - 1)
                tooltip.add(Component.translatable("tooltip.soulless.drowned_gills.worn").withStyle(ChatFormatting.RED));
            tooltip.add(Component.translatable("tooltip.soulless.drowned_gills.repair").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.soulless.drowned_gills.slot").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private DrownedGills() {}
}
