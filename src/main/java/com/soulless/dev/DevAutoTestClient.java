package com.soulless.dev;

import com.mojang.serialization.Lifecycle;
import com.soulless.Soulless;
import com.soulless.client.ClientState;
import com.soulless.registry.ModItems;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
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
    private static ArmorStand sideCam;

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
        if (DevAutoTest.TUNE) {
            // live tuning loop: same shots every cycle (overwritten), view from SOULLESS_TUNE_VIEW
            String view = System.getenv().getOrDefault("SOULLESS_TUNE_VIEW", "fp");
            if (tick == 15 || (tick > 15 && view.equals("fp") && opts.getCameraType() != CameraType.FIRST_PERSON)) {
                opts.setCameraType(view.equals("back") ? CameraType.THIRD_PERSON_BACK
                        : view.equals("front") ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
            }
            if (view.equals("side") && tick >= 40) {
                // a client-only camera stand 7 blocks to the player's side, looking at the arena
                if (sideCam == null || sideCam.level() != mc.level) {
                    sideCam = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
                    sideCam.setInvisible(true);
                    mc.level.putNonPlayerEntity(-777_777, sideCam);
                }
                sideCam.moveTo(306.0, mc.player.getY() - 0.2, 2.0, 90, 4);
                mc.player.setCustomNameVisible(false);
                sideCam.setYHeadRot(90);
                if (mc.getCameraEntity() != sideCam) mc.setCameraEntity(sideCam);
            }
            int c = tick % DevAutoTest.TUNE_CYCLE;
            if (c == 70) ClientState.slashStart = ClientState.gameTime();   // the server hit lands now
            for (int off : new int[]{3, 9, 13, 17, 20, 24, 32, 72, 74, 76, 78, 82, 88, 96, 100}) {
                if (c == off && tick > DevAutoTest.TUNE_CYCLE) shot(mc, String.format("tune_t%03d", off));
            }
            return;
        }
        switch (tick) {
            case 10 -> logTooltip(mc);
            case 15 -> {
                opts.setCameraType(CameraType.THIRD_PERSON_FRONT);
                mc.player.setXRot(10);
            }
            case 40 -> shot(mc, "00_pose_front");
            case 50 -> {
                opts.setCameraType(CameraType.THIRD_PERSON_BACK);
                mc.player.setXRot(20);
            }
            case 80 -> shot(mc, "01_cursed_hit");
            case 200 -> {
                opts.setCameraType(CameraType.THIRD_PERSON_FRONT);
                mc.player.setXRot(5);
            }
            case 360 -> opts.setCameraType(CameraType.FIRST_PERSON);
            case 385 -> shot(mc, "30_fp_idle");
            case 480 -> Soulless.LOGGER.info("[autotest] done");
            default -> {}
        }
        // the slams start on server ticks 100 / 260 / 420: back view, front view, first person
        int[] offsets = {3, 8, 11, 14, 18, 22, 27, 34, 46};
        for (int i = 0; i < offsets.length; i++) {
            if (tick == 100 + offsets[i]) shot(mc, String.format("1%d_back_t%02d", i, offsets[i]));
            if (tick == 260 + offsets[i]) shot(mc, String.format("2%d_front_t%02d", i, offsets[i]));
            if (tick == 420 + offsets[i]) shot(mc, String.format("3%d_fp_t%02d", i, offsets[i]));
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
