package com.soulless.client;

import com.soulless.Soulless;
import com.soulless.item.SenseiTrades;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The sensei has no big villager nose (his beard and moustache are in the profession texture instead). */
@Mod.EventBusSubscriber(modid = Soulless.MODID, value = Dist.CLIENT)
public final class SenseiRender {
    private static ModelPart nose(RenderLivingEvent<?, ?> event) {
        if (!(event.getEntity() instanceof Villager v) || !SenseiTrades.isSensei(v)) return null;
        if (!(event.getRenderer().getModel() instanceof VillagerModel<?> model)) return null;
        ModelPart head = model.getHead();
        return head.hasChild("nose") ? head.getChild("nose") : null;
    }

    @SubscribeEvent
    public static void onPre(RenderLivingEvent.Pre<?, ?> event) {
        ModelPart nose = nose(event);
        if (nose != null) nose.visible = false;
    }

    @SubscribeEvent
    public static void onPost(RenderLivingEvent.Post<?, ?> event) {
        ModelPart nose = nose(event);
        if (nose != null) nose.visible = true;
    }

    private SenseiRender() {}
}
