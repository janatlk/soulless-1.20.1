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
    /** The tier's damage; the real damage comes from the upgrades bought ({@link #DAMAGE}). */
    private static final int DAMAGE_MODIFIER = 4;
    /** 4 - 2.4 = 1.6 attacks per second: the cooldown matches the cut animation. */
    private static final float ATTACK_SPEED_MODIFIER = -2.4F;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SoulKatanaItem(Properties properties) {
        this(properties, DAMAGE_MODIFIER);
    }

    protected SoulKatanaItem(Properties properties, int damageModifier) {
        super(SoulForgedTier.INSTANCE, damageModifier, ATTACK_SPEED_MODIFIER, properties);
    }

    /** Attack damage by upgrade tiers bought (iron sword 6, diamond 7, netherite 8); the Edge path adds {@link #EDGE}. */
    public static final float[] DAMAGE = {5, 6, 7, 7.5F, 8.5F, 9.5F};
    public static final float EDGE = 1.5F, FLOW_SPEED = 0.15F;

    /** One attribute set per tier and last path: the damage grows with the upgrades. */
    private final java.util.List<com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute,
            net.minecraft.world.entity.ai.attributes.AttributeModifier>> stageModifiers = new java.util.ArrayList<>();

    @Override
    public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.AttributeModifier>
            getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot slot, ItemStack stack) {
        if (slot != net.minecraft.world.entity.EquipmentSlot.MAINHAND) return super.getAttributeModifiers(slot, stack);
        if (stageModifiers.isEmpty()) {
            for (int i = 0; i < DAMAGE.length * 3; i++) {
                float dmg = DAMAGE[i / 3] + (i % 3 == 1 ? EDGE : 0);
                float speed = ATTACK_SPEED_MODIFIER + (i % 3 == 2 ? (4 + ATTACK_SPEED_MODIFIER) * FLOW_SPEED : 0);
                stageModifiers.add(com.google.common.collect.ImmutableMultimap.<net.minecraft.world.entity.ai.attributes.Attribute,
                                net.minecraft.world.entity.ai.attributes.AttributeModifier>builder()
                        .put(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                                BASE_ATTACK_DAMAGE_UUID, "Katana stage", dmg - 1, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION))
                        .put(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED, new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                                BASE_ATTACK_SPEED_UUID, "Katana speed", speed, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION))
                        .build());
            }
        }
        return stageModifiers.get(modifierIndex(stack));
    }

    private static int modifierIndex(ItemStack stack) {
        int tier = Math.min(DAMAGE.length - 1, com.soulless.upgrade.Upgrades.tier(stack));
        int path = com.soulless.upgrade.Upgrades.has(stack, com.soulless.upgrade.Upgrades.KATANA_EDGE) ? 1 : com.soulless.upgrade.Upgrades.has(stack, com.soulless.upgrade.Upgrades.KATANA_FLOW) ? 2 : 0;
        return tier * 3 + path;
    }

    public static float damage(ItemStack stack) {
        return DAMAGE[Math.min(DAMAGE.length - 1, com.soulless.upgrade.Upgrades.tier(stack))] + (com.soulless.upgrade.Upgrades.has(stack, com.soulless.upgrade.Upgrades.KATANA_EDGE) ? EDGE : 0);
    }

    public static float speed(ItemStack stack) {
        return (4 + ATTACK_SPEED_MODIFIER) * (com.soulless.upgrade.Upgrades.has(stack, com.soulless.upgrade.Upgrades.KATANA_FLOW) ? 1 + FLOW_SPEED : 1);
    }

    /** It never wears: the blade that grows with its owner is never thrown away. */
    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
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
        var tree = com.soulless.upgrade.Upgrades.tree(stack);
        int tier = com.soulless.upgrade.Upgrades.tier(stack), tiers = tree == null ? 0 : tree.size();
        tooltip.add(Component.translatable("tooltip.soulless.upgrade.tier", v(tier), v(tiers)).withStyle(ChatFormatting.AQUA));
        for (String opt : com.soulless.upgrade.Upgrades.picked(stack)) {
            tooltip.add(Component.literal(" ✦ ").append(Component.translatable("upgrade.soulless." + opt)).withStyle(ChatFormatting.DARK_AQUA));
        }
        tooltip.add(Component.translatable(k + "stats", v(fmt(damage(stack))), v(fmt(speed(stack)))).withStyle(ChatFormatting.GRAY));

        tooltip.add(section("passive"));
        if (!details) {
            tooltip.add(line(k + "c.cuts", ChatFormatting.AQUA));
            tooltip.add(section("active"));
            tooltip.add(line(k + "c.combo", ChatFormatting.WHITE, lmb));
            tooltip.add(gated(stack, com.soulless.upgrade.Upgrades.KATANA_PARRY, line(k + "c.parry", ChatFormatting.AQUA, rmb)));
            tooltip.add(gated(stack, com.soulless.upgrade.Upgrades.KATANA_RESONANCE, line(k + "c.ritual", ChatFormatting.LIGHT_PURPLE, key)));
            if (!com.soulless.upgrade.Upgrades.has(stack, com.soulless.upgrade.Upgrades.KATANA_THRUST)) tooltip.add(gated(stack, com.soulless.upgrade.Upgrades.KATANA_THRUST, Component.translatable(k + "thrust_name")));
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.soulless.upgrade.where").withStyle(ChatFormatting.DARK_GRAY));
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
        tooltip.add(gated(stack, com.soulless.upgrade.Upgrades.KATANA_THRUST, line(k + "d.combo.2", ChatFormatting.GRAY, v(fmt(KatanaCombat.THRUST_DAMAGE_MULT)))));
        if (!com.soulless.upgrade.Upgrades.has(stack, com.soulless.upgrade.Upgrades.KATANA_PARRY)) tooltip.add(gated(stack, com.soulless.upgrade.Upgrades.KATANA_PARRY, line(k + "d.parry.title", ChatFormatting.AQUA, rmb)));
        else {
        tooltip.add(line(k + "d.parry.title", ChatFormatting.AQUA, rmb));
        tooltip.add(line(k + "d.parry.1", ChatFormatting.GRAY));
        tooltip.add(line(k + "d.parry.2", ChatFormatting.GRAY, v(fmt(KatanaParry.stunTicks(stack) / 20F)), v(Math.round((KatanaParry.BONUS - 1) * 100)),
                v(KatanaParry.BLESSING_TICKS / 20)));
        }
        if (!com.soulless.upgrade.Upgrades.has(stack, com.soulless.upgrade.Upgrades.KATANA_RESONANCE)) tooltip.add(gated(stack, com.soulless.upgrade.Upgrades.KATANA_RESONANCE, line(k + "d.ritual.title", ChatFormatting.LIGHT_PURPLE, key)));
        else {
        tooltip.add(line(k + "d.ritual.title", ChatFormatting.LIGHT_PURPLE, key));
        tooltip.add(line(k + "d.ritual.1", ChatFormatting.GRAY, v(fmt(ResonanceRitual.SELF_DAMAGE / 2))));
        tooltip.add(line(k + "d.ritual.2", ChatFormatting.GRAY, v(ResonanceRitual.BLESSING_SECONDS), v(Math.round(ResonanceRitual.LIFESTEAL * 100)),
                v(Math.round(ResonanceRitual.MISS_CHANCE * 100))));
        tooltip.add(line(k + "d.ritual.cd", ChatFormatting.DARK_GRAY, v(ResonanceRitual.COOLDOWN_TICKS / 20)));
        }
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable(k + "lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    /** The line as it is once the upgrade is bought; until then a locked line pointing at the Soul Anvil. */
    private static Component gated(ItemStack stack, String option, Component line) {
        if (com.soulless.upgrade.Upgrades.has(stack, option)) return line;
        return Component.translatable("tooltip.soulless.upgrade.locked", line.getString()).withStyle(ChatFormatting.DARK_GRAY);
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
