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
import org.lwjgl.glfw.GLFW;

/** Client half of the dev scenario: opens/creates a flat test world, frames shots, saves screenshots. */
@Mod.EventBusSubscriber(modid = Soulless.MODID, value = Dist.CLIENT)
public final class DevAutoTestClient {
    /** A world only the autotest ever uses; never the user's own worlds. */
    public static final String WORLD = "soulless_autotest";
    /** SOULLESS_PREVIEW_STRIP=1: film the preview animation from one view instead of walking around it. */
    public static final boolean PREVIEW_STRIP = System.getenv("SOULLESS_PREVIEW_STRIP") != null;
    private static boolean started;
    private static int tick = -1, upShots, introAt = -1;
    private static ArmorStand sideCam;

    @SubscribeEvent
    public static void onScreen(ScreenEvent.Init.Post event) {
        if (!DevAutoTest.ENABLED) return;
        hideWindowIfAsked(Minecraft.getInstance());
        if (started || !(event.getScreen() instanceof TitleScreen)) return;
        started = true;
        Minecraft mc = Minecraft.getInstance();
        // a paused integrated server would desync the scripted timeline from the screenshots
        mc.options.pauseOnLostFocus = false;
        mc.tell(() -> {
            // the autotest world is disposable: always start it fresh
            if (mc.getLevelSource().levelExists(WORLD)) {
                try (var access = mc.getLevelSource().createAccess(WORLD)) {
                    access.deleteLevel();
                } catch (Exception e) {
                    Soulless.LOGGER.warn("[autotest] could not reset world: {}", e.toString());
                }
            }
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
        hideWindowIfAsked(mc);
        var opts = mc.options;
        if (DevAutoTest.PREVIEW) {
            if (tick == 15) {
                opts.setCameraType(CameraType.FIRST_PERSON);
                opts.hideGui = true;
            }
            if (PREVIEW_STRIP) {   // a 7 s film strip of one animation, a frame every 3 ticks
                if (tick >= 60 && tick <= 200 && (tick - 60) % 3 == 0) shot(mc, String.format("strip_%03d", tick - 60));
                if (tick == 210) mc.stop();
                return;
            }
            for (int v = 0; v < 4; v++) {
                if (tick == 60 + v * 40) shot(mc, "preview_" + v);
                if (tick == 70 + v * 40) shot(mc, "preview_" + v + "b");
                // a strip through one wing beat (every 3 ticks)
                int k = tick - (62 + v * 40);
                if (k >= 0 && k <= 30 && k % 3 == 0) shot(mc, String.format("cycle_%d_%02d", v, k));
            }
            if (tick == 240) mc.stop();
            return;
        }
        if (DevAutoTest.KATANA) {
            katanaTick(mc);
            return;
        }
        if (DevAutoTest.FOX) {
            opts.setCameraType(CameraType.FIRST_PERSON);
            var fox = mc.level.getEntitiesOfClass(com.soulless.pet.TamedFox.class, mc.player.getBoundingBox().inflate(40))
                    .stream().findFirst().orElse(null);
            for (int s : new int[]{90, 160, 290, 380, 440, 530}) {
                if (fox != null && tick >= s - 3 && tick <= s) {
                    double dx = fox.getX() - mc.player.getX(), dz = fox.getZ() - mc.player.getZ();
                    double dy = fox.getY() + 0.3 - mc.player.getEyeY();
                    mc.player.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90));
                    mc.player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
                }
                if (tick == s) shot(mc, "fox_" + s);
            }
            if (tick == 600) shot(mc, "fox_guard");
            if (tick == 640) mc.stop();
            return;
        }
        if (DevAutoTest.STRUCT) {
            opts.hideGui = true;
            if (tick == 60) shot(mc, "struct_drowned");
            if (tick == 76) shot(mc, "struct_drowned_top");
            if (tick == 110) shot(mc, "struct_altar");
            if (tick == 150) shot(mc, "struct_eye");
            if (tick == 260) mc.stop();
            return;
        }
        if (DevAutoTest.FORGE) {
            opts.setCameraType(CameraType.FIRST_PERSON);
            var anvil = mc.player.blockPosition().relative(mc.player.getDirection(), 2);
            if (tick == 22) mc.setScreen(new com.soulless.client.anvil.ForgeScreen(anvil));
            if (tick == 24) shot(mc, "forge_categories");
            if (tick == 26 && mc.screen != null) mc.screen.mouseClicked(mc.getWindow().getGuiScaledWidth() / 2F - 150, mc.getWindow().getGuiScaledHeight() / 2F
                    - 5 * 26 / 2F, 0);
            if (tick == 28) shot(mc, "forge_melee");
            if (tick == 29 && mc.screen != null) mc.screen.mouseClicked(mc.getWindow().getGuiScaledWidth() / 2F - 150, mc.getWindow().getGuiScaledHeight() / 2F
                    - 13, 0);
            if (tick == 31) shot(mc, "forge_card");
            if (tick == 33) mc.setScreen(null);
            if (tick == 36) mc.player.setPos(mc.player.getX() - mc.player.getDirection().getStepX() * 1.5, mc.player.getY(), mc.player.getZ() - mc.player.getDirection().getStepZ() * 1.5);
            if (tick == 37) {
                mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, net.minecraft.world.phys.Vec3.atCenterOf(anvil).add(0, 0.6, 0));
            }
            if (tick == 38) shot(mc, "forge_laying");
            // the upgrade screen, as soon as the katana lies on the anvil here; a shot before and after the buying
            ItemStack onAnvil = mc.level.getBlockEntity(anvil) instanceof com.soulless.forge.SoulAnvilBlockEntity be ? be.held() : ItemStack.EMPTY;
            if (!onAnvil.isEmpty() && !(mc.screen instanceof com.soulless.client.anvil.UpgradeScreen) && upShots == 0) {
                mc.setScreen(new com.soulless.client.anvil.UpgradeScreen(anvil));
                upShots = 1;
            }
            if (upShots == 1 && mc.screen instanceof com.soulless.client.anvil.UpgradeScreen) {
                shot(mc, "forge_upgrade_0");
                upShots = 2;
            }
            if (upShots == 2 && com.soulless.upgrade.Upgrades.tier(onAnvil) >= 2) {
                shot(mc, "forge_upgrade_3");
                upShots = 3;
            }
            if (tick == 58 || tick == 98) {
                var lines = mc.player.getMainHandItem().getTooltipLines(mc.player, net.minecraft.world.item.TooltipFlag.NORMAL);
                Soulless.LOGGER.info("[autotest] forge tooltip t={}: {}", tick, lines.stream().map(net.minecraft.network.chat.Component::getString).toList());
            }
            if (tick == 75) {
                mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, net.minecraft.world.phys.Vec3.atCenterOf(anvil).add(0, 0.6, 0));
            }
            if (tick == 78) shot(mc, "forge_soul_forged_laying");
            if (tick == 170) mc.stop();
            return;
        }
        if (DevAutoTest.RENEGADE) {
            opts.setCameraType(CameraType.FIRST_PERSON);
            var him = mc.level.getEntitiesOfClass(com.soulless.boss.RenegadeEntity.class, mc.player.getBoundingBox().inflate(40)).stream().findFirst().orElse(null);
            if (him != null) {
                double dx = him.getX() - mc.player.getX(), dz = him.getZ() - mc.player.getZ();
                double dy = him.getY() + 1.2 - mc.player.getEyeY();
                mc.player.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90));
                mc.player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
                if (tick % 6 == 0 && rgShots < 120 && mc.player.getVehicle() == null) shot(mc, String.format("renegade_%04d", rgShots++));
            }
            // chained (the loss): from the front, to see the chains and him taking the blade
            opts.setCameraType(mc.player.getVehicle() != null ? (rgSeizeShots % 2 == 0 ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK) : CameraType.FIRST_PERSON);
            if (mc.player.getVehicle() != null && tick % 4 == 0 && rgSeizeShots < 40) shot(mc, String.format("renegade_seize_%03d", rgSeizeShots++));
            if (tick == 4400) mc.stop();
            return;
        }
        if (DevAutoTest.BOSS) {
            if (tick == 15) opts.setCameraType(CameraType.FIRST_PERSON);
            if (tick > 60 && tick % 30 == 0 && tick < 1800) shot(mc, String.format("boss_%04d", tick));
            // the intro, frame by frame
            if (com.soulless.client.BossIntro.active()) {
                if (introAt < 0) introAt = tick;
                int it = tick - introAt;
                if (it == 3 || it == 14 || it == 30 || it == 55) shot(mc, "boss_intro_" + it);
                if (it == 30) {
                    var c = mc.gameRenderer.getMainCamera();
                    var b = mc.level.getEntitiesOfClass(com.soulless.boss.WailingReaperEntity.class, mc.player.getBoundingBox().inflate(60)).stream().findFirst();
                    Soulless.LOGGER.info("[autotest] intro camera entity={} at {} yaw={} pitch={} | player {} | boss {}", mc.getCameraEntity().getClass().getSimpleName(),
                            c.getPosition(), c.getYRot(), c.getXRot(), mc.player.position(), b.map(e -> e.position().toString()).orElse("none"));
                }
            }
            if (tick == 1850) mc.stop();   // the run ends on its own
            return;
        }
        if (DevAutoTest.CHECK) {
            if (tick == 15) opts.setCameraType(CameraType.FIRST_PERSON);
            if (tick == 50) shot(mc, "check_tooltips");
            if (tick == 100) shot(mc, "check_hit");
            return;
        }
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
            if (DevAutoTest.TUNE_REND) {
                // align to the server: count from the moment Soul Rend actually started
                long start = ClientState.RENDS.getOrDefault(mc.player.getId(), Long.MIN_VALUE);
                c = start == Long.MIN_VALUE ? -1 : (int) (ClientState.gameTime() - start);
            }
            if (!DevAutoTest.TUNE_REND && c == 70) ClientState.slashStart = ClientState.gameTime();   // the server hit lands now
            int[] offsets = DevAutoTest.TUNE_REND
                    ? new int[]{6, 16, 60, 110, 119, 121, 122, 124, 127, 140, 175, 200, 240, 290}
                    : new int[]{3, 9, 13, 17, 20, 24, 32, 72, 74, 76, 78, 82, 88, 96, 100};
            for (int off : offsets) {
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

    /** Katana cycle length, and the ticks into it of the two attacks (kesa, then gyaku as the combo). */
    private static final int KATANA_CYCLE = 116, KATANA_KESA = 4, KATANA_GYAKU = 18, KATANA_TSUKI = 24, KATANA_RITUAL = 62;
    private static net.minecraft.world.phys.Vec3 katanaHome;
    private static final String[] KATANA_VIEWS = {"front", "side", "back", "fp"};

    /**
     * SOULLESS_KATANA: the player stands still facing south; every cycle it attacks twice (kesa-giri, gyaku-kesa).
     * Each cycle is filmed from the next view (front-left three-quarter, left side, back-right, first person),
     * a frame every tick, overwriting the last loop: edit run/katana_tune.properties and wait for the next pass.
     */
    private static void katanaTick(Minecraft mc) {
        if (tick < 40) return;
        int k = tick - 40;
        int cycle = k / KATANA_CYCLE, c = k % KATANA_CYCLE;
        String view = KATANA_VIEWS[cycle % KATANA_VIEWS.length];
        var p = mc.player;
        if (katanaHome == null) katanaHome = p.position();
        if (c == 0 || c == KATANA_RITUAL - 4) {   // back to the start after the thrust's dash - on the server too, where the hits are resolved
            p.setPos(katanaHome.x, katanaHome.y, katanaHome.z);
            p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            var server = mc.getSingleplayerServer();
            var home = katanaHome;
            if (server != null) server.execute(() -> {
                var sp = server.getPlayerList().getPlayer(p.getUUID());
                if (sp != null) sp.teleportTo(sp.serverLevel(), home.x, home.y, home.z, 0, 0);
            });
        }
        p.setYRot(0);
        p.setXRot(0);
        p.yHeadRot = p.yHeadRotO = 0;
        // the body is left to the game (and to KatanaAnim) except at the start of a cycle, where it is turned 30
        // degrees off the look so a cut that snapped it around would show
        if (c == 0) p.yBodyRot = p.yBodyRotO = 30;
        mc.options.hideGui = !view.equals("fp");   // F1 also hides the first-person hand
        if (view.equals("fp")) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (mc.getCameraEntity() != p) mc.setCameraEntity(p);
        } else {
            mc.options.setCameraType(CameraType.FIRST_PERSON);   // the camera entity is the stand: no third-person offset
            double[] off = switch (view) {
                case "front" -> new double[]{2.2, 3.4};
                case "side" -> new double[]{4.2, 0.3};
                default -> new double[]{-1.8, -3.6};
            };
            if (sideCam == null || sideCam.level() != mc.level) {
                sideCam = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
                sideCam.setInvisible(true);
                mc.level.putNonPlayerEntity(-777_777, sideCam);
            }
            double cx = p.getX() + off[0], cz = p.getZ() + off[1];
            float yaw = (float) Math.toDegrees(Math.atan2(-(p.getX() - cx), p.getZ() - cz));
            sideCam.moveTo(cx, p.getY() + 1.1 - sideCam.getEyeHeight(), cz, yaw, 6);
            sideCam.setYHeadRot(yaw);
            sideCam.yHeadRotO = yaw;
            sideCam.yRotO = yaw;
            sideCam.xRotO = 6;
            if (mc.getCameraEntity() != sideCam) mc.setCameraEntity(sideCam);
        }
        // real attack-key presses: kesa, gyaku, then a press during gyaku that queues the thrust
        // (the katana ignores the crosshair, so a stand camera picking the player is harmless)
        ClientState.showZones = true;
        if (c == KATANA_RITUAL) net.minecraft.client.KeyMapping.click(com.soulless.client.ClientModEvents.SLAM_KEY.getKey());
        if (c == KATANA_KESA || c == KATANA_GYAKU || c == KATANA_TSUKI) {
            net.minecraft.client.KeyMapping.click(mc.options.keyAttack.getKey());
        }
        if (c <= 112 && cycle >= 1) shot(mc, String.format("katana_%s_%02d", view, c));
    }

    /**
     * SOULLESS_HIDDEN=1: park the game window far off-screen so the user can keep using the PC.
     * It still renders (and screenshots still work); a minimized window would stop rendering.
     */
    private static void hideWindowIfAsked(Minecraft mc) {
        if (System.getenv("SOULLESS_HIDDEN") == null) return;
        long handle = mc.getWindow().getWindow();
        int[] x = new int[1], y = new int[1];
        GLFW.glfwGetWindowPos(handle, x, y);
        if (x[0] > -3000) {
            GLFW.glfwSetWindowSize(handle, 1280, 720);
            GLFW.glfwSetWindowPos(handle, -6000, -6000);
        }
    }

    /** CHECK mode: draw the sword's tooltip (compact on the left, detailed on the right) over the game. */
    @SubscribeEvent
    public static void onGui(net.minecraftforge.client.event.RenderGuiEvent.Post event) {
        if (!DevAutoTest.CHECK || tick < 30 || tick > 60) return;
        Minecraft mc = Minecraft.getInstance();
        ItemStack stack = new ItemStack(ModItems.SOUL_FORGED.get());
        var g = event.getGuiGraphics();
        com.soulless.client.ClientKeys.forceDetails = false;
        g.renderTooltip(mc.font, stack, 8, 24);
        com.soulless.client.ClientKeys.forceDetails = true;
        g.renderTooltip(mc.font, stack, event.getWindow().getGuiScaledWidth() / 2 - 20, 24);
        com.soulless.client.ClientKeys.forceDetails = false;
    }

    private static int rgSeizeShots;
    private static int rgShots;
    private static int questSparShot = -1, questShrineShot = -1, questBookShot = -1;
    private static int astralArrived = -1, astralNear = -1, astralTalk = -1, astralWoke = -1;

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
