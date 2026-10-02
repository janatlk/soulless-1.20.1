package com.soulless.dev;

import com.mojang.serialization.Lifecycle;
import com.soulless.Soulless;
import com.soulless.registry.ModItems;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client half of the dev scenario: opens/creates a flat test world, frames shots, saves screenshots. */
@Mod.EventBusSubscriber(modid = Soulless.MODID, value = Dist.CLIENT)
public final class DevAutoTestClient {
    private static final String WORLD = "soulless_test";
    private static boolean started;
    private static int tick = -1;

    @SubscribeEvent
    public static void onScreen(ScreenEvent.Init.Post event) {
        if (!DevAutoTest.ENABLED || started || !(event.getScreen() instanceof TitleScreen)) return;
        started = true;
        Minecraft mc = Minecraft.getInstance();
        // a paused integrated server would desync the scripted timeline from the screenshots
        mc.options.pauseOnLostFocus = false;
        mc.tell(() -> {
            if (mc.getLevelSource().levelExists(WORLD)) {
                mc.createWorldOpenFlows().loadLevel(event.getScreen(), WORLD);
            } else {
                GameRules rules = new GameRules();
                LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.NORMAL, true, rules,
                        WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(1234L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions());
            }
        });
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!DevAutoTest.ENABLED || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            tick = -1;
            return;
        }
        if (tick < 0) tick = 0;
        tick++;
        var opts = mc.options;
        switch (tick) {
            case 10 -> {
                opts.hideGui = false;
                logTooltip(mc);
            }
            case 25 -> {
                opts.setCameraType(CameraType.THIRD_PERSON_FRONT);
                mc.player.setXRot(10);
            }
            case 45 -> shot(mc, "01_pose_front");
            case 50 -> opts.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 58 -> mc.player.setXRot(25);
            case 70 -> shot(mc, "02_hold_back");
            case 80 -> shot(mc, "03_cursed_hit");
            case 104 -> shot(mc, "04_slam_windup");
            case 109 -> shot(mc, "05_slam_strike");
            case 116 -> shot(mc, "06_slam_impact");
            case 135 -> shot(mc, "07_cracks");
            case 175 -> shot(mc, "08_cursed_mobs");
            case 200 -> opts.setCameraType(CameraType.FIRST_PERSON);
            case 230 -> shot(mc, "09_first_person");
            case 294 -> shot(mc, "10_fp_windup");
            case 299 -> shot(mc, "11_fp_strike");
            case 306 -> shot(mc, "12_fp_impact");
            case 330 -> {
                opts.hideGui = false;
                shot(mc, "13_fp_after");
                Soulless.LOGGER.info("[autotest] done");
            }
            default -> {}
        }
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "autotest_" + name + ".png", mc.getMainRenderTarget(),
                msg -> Soulless.LOGGER.info("[autotest] screenshot {}", name));
    }

    private static void logTooltip(Minecraft mc) {
        ItemStack stack = new ItemStack(ModItems.SOUL_FORGED.get());
        for (Component line : stack.getTooltipLines(mc.player, TooltipFlag.NORMAL)) {
            Soulless.LOGGER.info("[autotest] tooltip | {}", line.getString());
        }
    }

    private DevAutoTestClient() {}
}
