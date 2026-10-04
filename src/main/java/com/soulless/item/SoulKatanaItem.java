package com.soulless.item;

import com.soulless.client.KatanaClientExtensions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

/**
 * The soul katana: a fast two-handed blade. While it is held the player stands in jodan-no-kamae (blade raised
 * over the head) and attacks alternate two diagonal cuts, kesa-giri and gyaku-kesa (client.KatanaAnim).
 * Abilities are still to come.
 */
public class SoulKatanaItem extends SwordItem implements GeoItem {
    /** 1 (base) + 5 (tier) + 4 = 10 attack damage. */
    private static final int DAMAGE_MODIFIER = 4;
    /** 4 - 2.4 = 1.6 attacks per second: the cooldown matches the cut animation. */
    private static final float ATTACK_SPEED_MODIFIER = -2.4F;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SoulKatanaItem(Properties properties) {
        super(SoulForgedTier.INSTANCE, DAMAGE_MODIFIER, ATTACK_SPEED_MODIFIER, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String k = "tooltip.soulless.soul_katana.";
        tooltip.add(Component.translatable(k + "subtitle").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable(k + "stats", v(fmt(1 + getDamage())), v(fmt(4 + ATTACK_SPEED_MODIFIER)))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(k + "combo").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable(k + "thrust", v(fmt(KatanaCombat.THRUST_DAMAGE_MULT))).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(k + "parry", Component.translatable("hud.soulless.rmb")).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(k + "parry_1", v(KatanaParry.STUN_TICKS / 20), v(Math.round((KatanaParry.BONUS - 1) * 100)))
                .withStyle(ChatFormatting.GRAY));
        String key = net.minecraftforge.fml.DistExecutor.unsafeRunForDist(() -> () -> com.soulless.client.ClientKeys.slamKeyName(), () -> () -> "R");
        tooltip.add(Component.translatable(k + "ritual", key).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(k + "blessing", v(KitsuneRitual.BLESSING_SECONDS),
                v(Math.round(KitsuneRitual.LIFESTEAL * 100)), v(Math.round(KitsuneRitual.MISS_CHANCE * 100))).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(k + "ritual_cd", v(KitsuneRitual.COOLDOWN_TICKS / 20)).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Component v(Object value) {
        return Component.literal(String.valueOf(value)).withStyle(ChatFormatting.WHITE);
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(Math.round(v * 10) / 10.0);
    }

    /** The katana's hits come from where the blade goes (KatanaCombat), never from vanilla's sweep. */
    @Override
    public boolean canPerformAction(ItemStack stack, net.minecraftforge.common.ToolAction action) {
        return action != net.minecraftforge.common.ToolActions.SWORD_SWEEP && super.canPerformAction(stack, action);
    }

    /** As a dropped item it survives fire, lava and explosions. */
    @Override
    public boolean canBeHurtBy(net.minecraft.world.damagesource.DamageSource source) {
        return !source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION) && super.canBeHurtBy(source);
    }

    // ------------------------------------------------------------------ GeckoLib (static model, no item animations)

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new KatanaClientExtensions());
    }
}
