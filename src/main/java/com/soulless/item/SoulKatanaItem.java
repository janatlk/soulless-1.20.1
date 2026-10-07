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

    /** Never worn away to nothing by any path: it stops one short, and {@link #inventoryTick} breaks it in two. */
    @Override
    public <T extends net.minecraft.world.entity.LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<T> onBroken) {
        return Math.max(0, Math.min(amount, stack.getMaxDamage() - 1 - stack.getDamageValue()));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && entity instanceof net.minecraft.server.level.ServerPlayer p && stack.isDamageableItem()
                && stack.getDamageValue() >= stack.getMaxDamage() - 1 && p.getInventory().getItem(slot) == stack) {
            p.getInventory().setItem(slot, KatanaOath.shatter(p));
        }
    }

    /**
     * Compact by default (a line per feature), the full breakdown while Shift is held - in the same style as Soul
     * Forged's: a stat line, then passive and active sections. Numbers come from the code.
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String k = "tooltip.soulless.soul_katana.";
        boolean details = net.minecraftforge.fml.DistExecutor.unsafeRunForDist(() -> () -> com.soulless.client.ClientKeys.detailsHeld(), () -> () -> true);
        Component lmb = Component.translatable("hud.soulless.lmb"), rmb = Component.translatable("hud.soulless.rmb");
        String key = net.minecraftforge.fml.DistExecutor.unsafeRunForDist(() -> () -> com.soulless.client.ClientKeys.slamKeyName(), () -> () -> "R");
        tooltip.add(Component.translatable(k + "subtitle").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable(k + "stats", v(fmt(1 + getDamage())), v(fmt(4 + ATTACK_SPEED_MODIFIER))).withStyle(ChatFormatting.GRAY));

        tooltip.add(section("passive"));
        if (!details) {
            tooltip.add(line(k + "c.cuts", ChatFormatting.AQUA));
            tooltip.add(section("active"));
            tooltip.add(line(k + "c.combo", ChatFormatting.WHITE, lmb));
            tooltip.add(line(k + "c.parry", ChatFormatting.AQUA, rmb));
            tooltip.add(line(k + "c.ritual", ChatFormatting.LIGHT_PURPLE, key));
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable(k + "c.more").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            return;
        }
        tooltip.add(line(k + "d.cuts.title", ChatFormatting.AQUA));
        tooltip.add(line(k + "d.cuts.1", ChatFormatting.GRAY, v(fmt(KatanaCombat.CUT_REACH))));
        tooltip.add(line(k + "d.cuts.2", ChatFormatting.GRAY, v(Math.round(KatanaCombat.CUT_SIDE_DAMAGE * 100)),
                v(Math.round(KatanaCombat.CUT_SIDE_DAMAGE_BLESSED * 100))));

        tooltip.add(section("active"));
        tooltip.add(line(k + "d.combo.title", ChatFormatting.WHITE, lmb));
        tooltip.add(line(k + "d.combo.1", ChatFormatting.GRAY));
        tooltip.add(line(k + "d.combo.2", ChatFormatting.GRAY, v(fmt(KatanaCombat.THRUST_DAMAGE_MULT))));
        tooltip.add(line(k + "d.parry.title", ChatFormatting.AQUA, rmb));
        tooltip.add(line(k + "d.parry.1", ChatFormatting.GRAY));
        tooltip.add(line(k + "d.parry.2", ChatFormatting.GRAY, v(KatanaParry.STUN_TICKS / 20), v(Math.round((KatanaParry.BONUS - 1) * 100)),
                v(KatanaParry.BLESSING_TICKS / 20)));
        tooltip.add(line(k + "d.ritual.title", ChatFormatting.LIGHT_PURPLE, key));
        tooltip.add(line(k + "d.ritual.1", ChatFormatting.GRAY, v(fmt(KitsuneRitual.SELF_DAMAGE / 2))));
        tooltip.add(line(k + "d.ritual.2", ChatFormatting.GRAY, v(KitsuneRitual.BLESSING_SECONDS), v(Math.round(KitsuneRitual.LIFESTEAL * 100)),
                v(Math.round(KitsuneRitual.MISS_CHANCE * 100))));
        tooltip.add(line(k + "d.ritual.cd", ChatFormatting.DARK_GRAY, v(KitsuneRitual.COOLDOWN_TICKS / 20)));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable(k + "lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    private static Component section(String name) {
        return Component.literal(SoulForgedItem.SECTION_MARK).append(Component.translatable("tooltip.soulless.section." + name));
    }

    private static Component line(String key, ChatFormatting style, Object... args) {
        return Component.translatable(key, args).withStyle(style);
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
