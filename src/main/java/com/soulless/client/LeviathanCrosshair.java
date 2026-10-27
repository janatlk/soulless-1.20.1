package com.soulless.client;

import com.soulless.Soulless;
import com.soulless.boss.LeviathanEntity;
import com.soulless.boss.LeviathanPart;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Aiming at any piece of the Leviathan's body counts as aiming at the Leviathan: the attack indicator under the
 * crosshair shows, and a swing strikes the piece looked at. The game's own pick does not see the pieces reliably, so
 * the look is traced through them here, each frame, after it.
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID, value = Dist.CLIENT)
public final class LeviathanCrosshair {
    @SubscribeEvent
    public static void onCrosshair(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (mc.crosshairPickEntity instanceof LeviathanPart part) {
            mc.crosshairPickEntity = part.getParent();
            return;
        }
        if (mc.crosshairPickEntity instanceof LeviathanEntity) return;
        Vec3 eye = mc.player.getEyePosition(event.getPartialTick());
        double reach = mc.gameMode.hasFarPickRange() ? 6 : 3;
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) reach = Math.min(reach, mc.hitResult.getLocation().distanceTo(eye));
        if (mc.crosshairPickEntity != null) reach = Math.min(reach, mc.hitResult.getLocation().distanceTo(eye));
        Vec3 end = eye.add(mc.player.getViewVector(event.getPartialTick()).scale(reach));
        LeviathanPart best = null;
        Vec3 bestAt = null;
        double bestD = Double.MAX_VALUE;
        for (LeviathanEntity l : mc.level.getEntitiesOfClass(LeviathanEntity.class, new AABB(eye, eye).inflate(48))) {
            for (var p : l.getParts()) {
                var hit = p.getBoundingBox().clip(eye, end);
                if (hit.isEmpty()) continue;
                double d = hit.get().distanceToSqr(eye);
                if (d < bestD) {
                    bestD = d;
                    best = (LeviathanPart) p;
                    bestAt = hit.get();
                }
            }
        }
        if (best == null) return;
        mc.hitResult = new EntityHitResult(best, bestAt);
        mc.crosshairPickEntity = best.getParent();
    }

    private LeviathanCrosshair() {}
}
