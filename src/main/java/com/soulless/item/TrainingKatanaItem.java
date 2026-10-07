package com.soulless.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The sensei's Training Katana, a wooden bokken: the katana's stance, cuts and thrust (and the parry once the sensei
 * has taught it), but little damage and no magic - no harakiri, no Kitsune's blessing; it wears out like any tool.
 */
public class TrainingKatanaItem extends SoulKatanaItem {
    /** 1 (base) + 5 (tier) - 3 = 3 attack damage. */
    private static final int DAMAGE_MODIFIER = -3;

    public TrainingKatanaItem(Properties properties) {
        super(properties, DAMAGE_MODIFIER);
    }

    @Override
    public boolean isTraining() {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.soulless.training_katana").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.soulless.training_katana.stats", 1 + (int) getDamage()).withStyle(ChatFormatting.GRAY));
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> questLines(tooltip));
        tooltip.add(Component.translatable("tooltip.soulless.training_katana.lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    /** The training as a checklist: steps done ticked off, the current one highlighted, the rest dim. */
    private static void questLines(List<Component> tooltip) {
        int stage = com.soulless.client.ClientState.questStage;
        boolean scroll = net.minecraft.client.Minecraft.getInstance().player != null && net.minecraft.client.Minecraft.getInstance().player
                .getInventory().contains(new ItemStack(com.soulless.registry.ModItems.FAMILY_SCROLL.get()));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.soulless.quest.title").withStyle(ChatFormatting.GOLD));
        String[] steps = {"spar", "ask", "cleanse", "return", "scroll", "done"};
        for (int i = 0; i < 5; i++) {
            Object arg = i == 2 ? Math.min(com.soulless.client.ClientState.questKills, com.soulless.npc.SenseiQuest.CLEANSE_GOAL) : "";
            String key = "tooltip.soulless.quest." + steps[i] + (i == 4 && scroll ? "_found" : "");
            if (i < stage) {
                tooltip.add(Component.literal(" ✔ ").withStyle(ChatFormatting.DARK_GREEN)
                        .append(Component.translatable(key, i == 2 ? com.soulless.npc.SenseiQuest.CLEANSE_GOAL : arg, com.soulless.npc.SenseiQuest.CLEANSE_GOAL).withStyle(ChatFormatting.DARK_GRAY)));
            } else if (i == stage) {
                tooltip.add(Component.literal(" ▸ ").withStyle(ChatFormatting.YELLOW)
                        .append(Component.translatable(key, arg, com.soulless.npc.SenseiQuest.CLEANSE_GOAL).withStyle(ChatFormatting.WHITE)));
            } else {
                tooltip.add(Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.translatable(key, arg, com.soulless.npc.SenseiQuest.CLEANSE_GOAL).withStyle(ChatFormatting.DARK_GRAY)));
            }
        }
        if (stage >= 5) tooltip.add(Component.translatable("tooltip.soulless.quest.done").withStyle(ChatFormatting.AQUA));
    }
}
