package com.soulless.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.soulless.ability.RendManager;
import com.soulless.ability.RendTimeline;
import com.soulless.ability.SlamManager;
import net.minecraft.server.level.ServerPlayer;
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

    public static final String CONTROLLER = "main";
    public static final String SLAM_ANIM = "slam";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.soul_forged.idle");
    private static final RawAnimation SLAM = RawAnimation.begin().thenPlay("animation.soul_forged.slam");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private Multimap<Attribute, AttributeModifier> mainHandModifiers;

    public SoulForgedItem(Properties properties) {
        // 1 (base) + 5 (tier) + 10 = 16 attack damage, 4 - 3.3 = 0.7 attacks per second (slow, heavy slashes)
        super(SoulForgedTier.INSTANCE, 10, -3.3F, properties);
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

    /** Right click: Soul Rend. The server decides (cooldown, hunger, other abilities); the client does nothing. */
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
                    .build();
        }
        return mainHandModifiers;
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

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String k = "tooltip.soulless.soul_forged.";
        tooltip.add(Component.translatable(k + "subtitle").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        tooltip.add(Component.empty());

        tooltip.add(Component.translatable(k + "passive.title").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable(k + "passive.1", CURSE_SECONDS).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(k + "passive.2", fmt(SoulCurse.DAMAGE_PER_SECOND)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(k + "passive.3", Math.round(SoulCurse.SLOWNESS * 100)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(k + "passive.4").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(k + "passive.5").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());

        tooltip.add(Component.translatable(k + "ability.title", Component.keybind("key.soulless.slam")).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable(k + "ability.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(k + "ability.2", fmt(SlamManager.MAX_DAMAGE), fmt(SlamManager.RADIUS)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(k + "ability.3", SlamManager.CURSE_SECONDS,
                fmt(SoulCurse.DAMAGE_PER_SECOND + SlamManager.CURSE_AMPLIFIER)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(k + "ability.4").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(k + "ability.5", SlamManager.COOLDOWN_TICKS / 20).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());

        tooltip.add(Component.translatable(k + "rend.title").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable(k + "rend.1", RendTimeline.WINDUP / 20).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(k + "rend.2", fmt(RendManager.DAMAGE)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(k + "rend.3").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(k + "rend.4", RendManager.STUN_TARGETS, RendManager.STUN_SECONDS).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(k + "rend.5").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(k + "rend.6", RendManager.HUNGER_COST).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(k + "rend.7", RendTimeline.RECOVER / 20).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(k + "rend.8", RendTimeline.COOLDOWN / 20).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());

        tooltip.add(Component.translatable(k + "trait.1", fmt(REACH_BONUS)).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable(k + "trait.2").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable(k + "lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v);
    }

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
