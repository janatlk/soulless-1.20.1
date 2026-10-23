package com.soulless.item;

import com.soulless.Soulless;
import com.soulless.registry.ModItems;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/**
 * The Leviathan's armour (helmet from its head, scaled chest, leggings and boots), forged at the Soul Anvil from its
 * scales. Worn, it is drawn with its own model (client.LeviathanArmorModel) on the textures under textures/armor.
 * The numbers are netherite's for now.
 */
public class LeviathanArmorItem extends ArmorItem {
    public static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        @Override
        public int getDurabilityForType(Type type) {
            return new int[]{481, 555, 592, 407}[3 - type.ordinal()];
        }

        @Override
        public int getDefenseForType(Type type) {
            return switch (type) {
                case HELMET -> 3;
                case CHESTPLATE -> 8;
                case LEGGINGS -> 6;
                case BOOTS -> 3;
            };
        }

        @Override
        public int getEnchantmentValue() {
            return 15;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_NETHERITE;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(ModItems.LEVIATHAN_SCALE.get());
        }

        @Override
        public String getName() {
            return Soulless.MODID + ":leviathan";
        }

        @Override
        public float getToughness() {
            return 3;
        }

        @Override
        public float getKnockbackResistance() {
            return 0.1F;
        }
    };

    public LeviathanArmorItem(Type type, Properties props) {
        super(MATERIAL, type, props);
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.Level level,
                                java.util.List<net.minecraft.network.chat.Component> lines, net.minecraft.world.item.TooltipFlag flag) {
        lines.add(net.minecraft.network.chat.Component.translatable("tooltip.soulless.leviathan_armor")
                .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE, net.minecraft.ChatFormatting.ITALIC));
        if (getType() == Type.CHESTPLATE) {
            lines.add(net.minecraft.network.chat.Component.translatable("tooltip.soulless.deep_dash")
                    .withStyle(net.minecraft.ChatFormatting.AQUA));
            lines.add(net.minecraft.network.chat.Component.translatable("tooltip.soulless.deep_dash.desc",
                    com.soulless.ability.DeepDash.COOLDOWN_TICKS / 20).withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String layer) {
        return Soulless.MODID + ":textures/armor/leviathan_armor_" + (slot == EquipmentSlot.LEGS ? 2 : 1) + ".png";
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                return com.soulless.client.LeviathanArmorModels.get(slot == EquipmentSlot.LEGS);
            }
        });
    }
}
