package com.soulless;

import net.minecraft.advancements.Advancement;
import net.minecraft.server.level.ServerPlayer;

/**
 * Advancements earned by deeds the vanilla triggers can't see (data/soulless/advancements: their criterion is
 * "minecraft:impossible", named "done"): the first Soul Rend beam, the first Soul Crush, the first parry, three
 * caught in one parry, the Kitsune ritual, dying with honour, buying the Scroll of Parrying.
 */
public final class Achievements {
    public static void grant(ServerPlayer player, String name) {
        Advancement adv = player.server.getAdvancements().getAdvancement(Soulless.id(name));
        if (adv != null && !player.getAdvancements().getOrStartProgress(adv).isDone()) {
            player.getAdvancements().award(adv, "done");
        }
    }

    private Achievements() {}
}
