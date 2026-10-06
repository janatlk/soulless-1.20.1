package com.soulless.npc;

import com.soulless.Soulless;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** The sensei of the cherry-grove dojo (world.SenseiDojoPiece). Talks: data/soulless/dialogues/sensei.json. */
public class SenseiEntity extends NpcEntity {
    public SenseiEntity(EntityType<? extends SenseiEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public ResourceLocation dialogue() {
        return Soulless.id("sensei");
    }
}
