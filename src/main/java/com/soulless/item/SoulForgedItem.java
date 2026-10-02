package com.soulless.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.soulless.ability.RendManager;
import com.soulless.ability.RendTimeline;
import com.soulless.ability.SlamManager;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.fml.DistExecutor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import com.soulless.client.SoulForgedClientExtensions;
import com.soulless.effect.SoulCurse;
import com.soulless.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Soul Forged: a legendary two-handed greatsword.
 * Hits apply the Soul Curse; the slam key (R by default) triggers {@link SlamManager}; right click {@link RendManager}.
 */
public class SoulForgedItem extends SwordItem implements GeoItem {
    public static final int CURSE_SECONDS = 5;
    public static final double REACH_BONUS = 1.0;
    private static final UUID REACH_UUID = UUID.fromString("5d1f3c0e-7a51-4b8e-9a43-1f5b8c2d7e90");
    /** A heavy two-hander: slightly slower while held. */
    public static final double HEAVY_SLOWNESS = 0.08;
    private static final UUID HEAVY_UUID = UUID.fromString("7c3b9e21-4f6a-4d8b-a1c2-3e5f7a9b1c2d");

    public static final String CONTROLLER = "main";
    public static final String SLAM_ANIM = "slam";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.soul_forged.idle");
    private static final RawAnimation SLAM = RawAnimation.begin().thenPlay("animation.soul_forged.slam");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private Multimap<Attribute, AttributeModifier> mainHandModifiers;

    public SoulForgedItem(Properties properties) {
        // 1 (base) + 5 (tier) + 10 = 16 attack damage, 4 - 3.3 = 0.7 attacks per second (slow, heavy slashes)
        super(SoulForgedTier.INSTANCE, 10, ATTACK_SPEED_MODIFIER, properties);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!attacker.level().isClientSide) {
            SoulCurse.apply(target, CURSE_SECONDS * 20, 0, attacker);
            attacker.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                    ModSounds.CURSE_IGNITE.get(), SoundSource.PLAYERS, 0.8F, 0.9F + attacker.getRandom().nextFloat() * 0.2F);
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    /** Right click: Soul Rend. The server decides (cooldown, other abilities); the client does nothing. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            RendManager.tryStart(sp);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != EquipmentSlot.MAINHAND) {
            return super.getAttributeModifiers(slot, stack);
        }
        if (mainHandModifiers == null) {
            mainHandModifiers = ImmutableMultimap.<Attribute, AttributeModifier>builder()
                    .putAll(super.getAttributeModifiers(slot, stack))
                    .put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(REACH_UUID, "Soul Forged reach", REACH_BONUS, AttributeModifier.Operation.ADDITION))
                    .put(Attributes.MOVEMENT_SPEED, new AttributeModifier(HEAVY_UUID, "Soul Forged weight", -HEAVY_SLOWNESS, AttributeModifier.Operation.MULTIPLY_TOTAL))
                    .build();
        }
        return mainHandModifiers;
    }

    /** As a dropped item it survives fire, lava and explosions. */
    @Override
    public boolean canBeHurtBy(net.minecraft.world.damagesource.DamageSource source) {
        return !source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION) && super.canBeHurtBy(source);
    }

    // Soul fire replaces vanilla fire, so Fire Aspect would only fight the curse.
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return enchantment != Enchantments.FIRE_ASPECT && super.canApplyAtEnchantingTable(stack, enchantment);
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return !EnchantmentHelper.getEnchantments(book).containsKey(Enchantments.FIRE_ASPECT) && super.isBookEnchantable(stack, book);
    }

    /**
     * Compact by default (one line per feature + a stat line), the full breakdown while Shift is held.
     * Numbers come from the code, so they always match the game. The vanilla "When in Main Hand" block is
     * replaced by the stat line (see {@code client.SoulTooltip}).
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String k = "tooltip.soulless.soul_forged.";
        boolean details = DistExecutor.unsafeRunForDist(() -> () -> com.soulless.client.ClientKeys.detailsHeld(), () -> () -> true);
        String slamKey = DistExecutor.unsafeRunForDist(() -> () -> com.soulless.client.ClientKeys.slamKeyName(), () -> () -> "R");
        String rendKey = Component.translatable("hud.soulless.rmb").getString();
        float damage = 1 + getDamage();
        float speed = 4 + ATTACK_SPEED_MODIFIER;

        tooltip.add(Component.translatable(k + "subtitle").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable(k + "stats", v(fmt(damage)), v(fmt(speed)), v(fmt(REACH_BONUS))).withStyle(ChatFormatting.GRAY));

        if (!details) {
            tooltip.add(section("passive"));
            tooltip.add(line(k + "c.curse", ChatFormatting.LIGHT_PURPLE, v(CURSE_SECONDS)));
            tooltip.add(line(k + "c.traits", ChatFormatting.DARK_AQUA));
            tooltip.add(section("active"));
            tooltip.add(line(k + "c.slam", ChatFormatting.LIGHT_PURPLE, slamKey, v(fmt(SlamManager.BASE_DAMAGE)),
                    v(fmt(SlamManager.DAMAGE_CAP)), v(SlamManager.COOLDOWN_TICKS / 20)));
            tooltip.add(line(k + "c.rend", ChatFormatting.GOLD, rendKey, v(fmt(RendManager.DAMAGE)), v(RendTimeline.COOLDOWN / 20)));
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable(k + "c.more").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            return;
        }

        tooltip.add(section("passive"));
        tooltip.add(line(k + "d.curse.title", ChatFormatting.LIGHT_PURPLE));
        tooltip.add(line(k + "d.curse.1", ChatFormatting.GRAY, v(CURSE_SECONDS), v(fmt(SoulCurse.DAMAGE_PER_SECOND))));
        tooltip.add(line(k + "d.curse.2", ChatFormatting.GRAY, v(Math.round(SoulCurse.SLOWNESS * 100))));
        tooltip.add(line(k + "d.traits.title", ChatFormatting.DARK_AQUA));
        tooltip.add(line(k + "d.traits.1", ChatFormatting.GRAY));
        tooltip.add(line(k + "d.traits.2", ChatFormatting.GRAY, v(Math.round(HEAVY_SLOWNESS * 100))));
        tooltip.add(line(k + "d.traits.3", ChatFormatting.GRAY));
        tooltip.add(line(k + "d.traits.4", ChatFormatting.GRAY));

        tooltip.add(section("active"));
        tooltip.add(line(k + "d.slam.title", ChatFormatting.LIGHT_PURPLE, slamKey, v(SlamManager.COOLDOWN_TICKS / 20)));
        tooltip.add(line(k + "d.slam.1", ChatFormatting.GRAY));
        tooltip.add(line(k + "d.slam.2", ChatFormatting.GRAY, v(fmt(SlamManager.BASE_DAMAGE)), v(fmt(SlamManager.RADIUS))));
        tooltip.add(line(k + "d.slam.4", ChatFormatting.GRAY, v(fmt(SlamManager.DAMAGE_PER_BLOCK)), v(fmt(SlamManager.DAMAGE_CAP))));
        tooltip.add(line(k + "d.slam.3", ChatFormatting.GRAY, v(SlamManager.CURSE_SECONDS),
                v(fmt(SoulCurse.DAMAGE_PER_SECOND + SlamManager.CURSE_AMPLIFIER))));
        tooltip.add(line(k + "d.slam.5", ChatFormatting.DARK_GRAY));

        tooltip.add(line(k + "d.rend.title", ChatFormatting.GOLD, rendKey, v(RendTimeline.COOLDOWN / 20)));
        tooltip.add(line(k + "d.rend.1", ChatFormatting.GRAY, v(RendTimeline.WINDUP / 20), v(fmt(RendManager.DAMAGE))));
        tooltip.add(line(k + "d.rend.5", ChatFormatting.GRAY));
        tooltip.add(line(k + "d.rend.2", ChatFormatting.GRAY));
        tooltip.add(line(k + "d.rend.3", ChatFormatting.GRAY, v(RendManager.STUN_TARGETS), v(RendManager.STUN_SECONDS)));
        tooltip.add(line(k + "d.rend.4", ChatFormatting.GRAY, v(RendTimeline.RECOVER / 20)));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable(k + "lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    /** Marker for a section divider; the client tooltip renderer swaps it for a drawn header (SoulTooltip). */
    public static final String SECTION_MARK = "⁣";

    private static Component section(String name) {
        return Component.literal(SECTION_MARK).append(Component.translatable("tooltip.soulless.section." + name));
    }

    /** The item name in a pale-lilac to deep-violet gradient (client only; servers keep the plain translation). */
    @Override
    public Component getName(ItemStack stack) {
        Component plain = super.getName(stack);
        return DistExecutor.unsafeRunForDist(() -> () -> gradient(plain.getString(), 0xD17CFF, 0x8A22E0), () -> () -> plain);
    }

    private static Component gradient(String text, int from, int to) {
        MutableComponent out = Component.empty();
        int n = Math.max(1, text.length() - 1);
        for (int i = 0; i < text.length(); i++) {
            float t = i / (float) n;
            int r = (int) Mth.lerp(t, from >> 16 & 255, to >> 16 & 255);
            int g = (int) Mth.lerp(t, from >> 8 & 255, to >> 8 & 255);
            int b = (int) Mth.lerp(t, from & 255, to & 255);
            out.append(Component.literal(String.valueOf(text.charAt(i))).withStyle(s -> s.withColor(r << 16 | g << 8 | b)));
        }
        return out;
    }

    private static Component line(String key, ChatFormatting style, Object... args) {
        return Component.translatable(key, args).withStyle(style);
    }

    /** A highlighted number inside a tooltip line. */
    private static Component v(Object value) {
        return Component.literal(String.valueOf(value)).withStyle(ChatFormatting.WHITE);
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(Math.round(v * 10) / 10.0);
    }

    /** 4 - 3.45 = 0.55 attacks per second: a slow, heavy blade. */
    private static final float ATTACK_SPEED_MODIFIER = -3.45F;

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, CONTROLLER, 2, state -> state.setAndContinue(IDLE))
                .triggerableAnim(SLAM_ANIM, SLAM));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new SoulForgedClientExtensions());
    }
}
