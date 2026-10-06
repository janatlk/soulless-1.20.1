package com.soulless.npc.dialogue;

import com.soulless.npc.NpcEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Who is talking to whom, handed to conditions and actions. Flags are simple named booleans:
 * <ul>
 * <li>player flags - kept on the player (survive death), shared by all characters: name them per character,
 * e.g. {@code sensei.met};</li>
 * <li>npc flags - kept on this one character, e.g. {@code scroll_sold} on a given sensei.</li>
 * </ul>
 */
public final class DialogueContext {
    private static final String PLAYER_TAG = "soulless_dialogue";

    public final ServerPlayer player;
    public final NpcEntity npc;
    private boolean ended;

    public DialogueContext(ServerPlayer player, NpcEntity npc) {
        this.player = player;
        this.npc = npc;
    }

    /** An action asks the conversation to stop here (it closes the window or opens another screen). */
    public void end() {
        ended = true;
    }

    public boolean ended() {
        return ended;
    }

    public boolean flag(String scope, String name) {
        return "npc".equals(scope) ? npc.flags().getBoolean(name) : playerFlags(player).getBoolean(name);
    }

    public void setFlag(String scope, String name, boolean value) {
        CompoundTag tag = "npc".equals(scope) ? npc.flags() : playerFlags(player);
        if (value) tag.putBoolean(name, true);
        else tag.remove(name);
    }

    public static CompoundTag playerFlags(Player player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        if (!persisted.contains(PLAYER_TAG)) persisted.put(PLAYER_TAG, new CompoundTag());
        return persisted.getCompound(PLAYER_TAG);
    }
}
