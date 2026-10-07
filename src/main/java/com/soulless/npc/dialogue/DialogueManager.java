package com.soulless.npc.dialogue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.soulless.Soulless;
import com.soulless.network.DialogueShowPacket;
import com.soulless.network.ModNetwork;
import com.soulless.npc.NpcEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Loads the dialogues (data/&lt;ns&gt;/dialogues/*.json, reloaded with /reload) and runs conversations. The server
 * decides everything: which node, which options the player may see, what the choice does; the client only shows
 * the window ({@code client.DialogueScreen}) and sends back the index of the chosen option.
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class DialogueManager {
    /** Walking further than this from the character ends the conversation. */
    private static final double MAX_DISTANCE = 8;

    private static final Gson GSON = new GsonBuilder().create();
    private static Map<ResourceLocation, Dialogue> dialogues = Map.of();

    /** A conversation in progress: the options shown, by their index in the node's list. A speech (no dialogue file:
     * someone who is not an NpcEntity speaks, see {@link #speak}) has {@code after} instead. */
    private record Session(int npcId, @Nullable ResourceLocation dialogue, String node, int[] shown, @Nullable Runnable after) {
        Session(int npcId, ResourceLocation dialogue, String node, int[] shown) {
            this(npcId, dialogue, node, shown, null);
        }
    }

    /**
     * {@code speaker} says {@code pages} in the dialogue window, the player answers with the one {@code answer};
     * {@code after} runs when the window goes (answered, closed, the player gone).
     */
    public static void speak(ServerPlayer player, Entity speaker, int accent, List<Component> pages, Component answer, Runnable after) {
        end(player, true);
        SESSIONS.put(player.getUUID(), new Session(speaker.getId(), null, "", new int[]{0}, after));
        ModNetwork.sendToPlayer(player, new DialogueShowPacket(speaker.getId(), speaker.getDisplayName(), accent, pages, List.of(answer)));
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    @Nullable
    public static Dialogue get(ResourceLocation id) {
        return dialogues.get(id);
    }

    @SubscribeEvent
    public static void onReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(GSON, "dialogues") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
                Map<ResourceLocation, Dialogue> loaded = new HashMap<>();
                files.forEach((id, json) -> {
                    try {
                        loaded.put(id, Dialogue.parse(id, json.getAsJsonObject()));
                    } catch (RuntimeException e) {
                        Soulless.LOGGER.error("Dialogue {} is broken: {}", id, e.getMessage());
                    }
                });
                dialogues = loaded;
                Soulless.LOGGER.info("Loaded {} dialogues", loaded.size());
            }
        });
    }

    /** The player talks to the character (right click). */
    public static void open(ServerPlayer player, NpcEntity npc) {
        Dialogue d = dialogues.get(npc.dialogue());
        if (d == null) {
            Soulless.LOGGER.warn("{} has no dialogue {}", npc, npc.dialogue());
            return;
        }
        DialogueContext ctx = new DialogueContext(player, npc);
        String node = d.startNode(ctx);
        if (node == null) return;
        npc.startTalking(player);
        show(ctx, d, node);
    }

    /** The client chose option {@code index} of the shown ones, or -1: the window was closed. */
    public static void choose(ServerPlayer player, int index) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return;
        if (s.after != null) {
            end(player, true);
            return;
        }
        Entity e = player.level().getEntity(s.npcId);
        Dialogue d = dialogues.get(s.dialogue);
        if (index < 0 || !(e instanceof NpcEntity npc) || d == null || index >= s.shown.length) {
            end(player, false);
            return;
        }
        Dialogue.Node node = d.nodes.get(s.node);   // /reload may have changed the dialogue mid-conversation
        if (node == null || s.shown[index] >= node.options().size()) {
            end(player, true);
            return;
        }
        Dialogue.Option option = node.options().get(s.shown[index]);
        DialogueContext ctx = new DialogueContext(player, npc);
        if (!option.condition().test(ctx)) {   // things changed since it was shown: show the node again
            show(ctx, d, s.node);
            return;
        }
        for (DialogueActions.Action a : option.actions()) a.run(ctx);
        if (ctx.ended() || option.next() == null) {
            end(player, true);
            return;
        }
        show(ctx, d, option.next());
    }

    /** Chooses the shown option whose text is {@code textKey} (scripted runs: the dev autotest). False if none. */
    public static boolean chooseText(ServerPlayer player, String textKey) {
        Session s = SESSIONS.get(player.getUUID());
        if (s != null && s.after != null) {
            end(player, true);
            return true;
        }
        Dialogue d = s == null || s.dialogue == null ? null : dialogues.get(s.dialogue);
        if (d == null) return false;
        Dialogue.Node node = d.nodes.get(s.node);
        for (int i = 0; i < s.shown.length; i++) {
            if (node.options().get(s.shown[i]).text().equals(textKey)) {
                choose(player, i);
                return true;
            }
        }
        return false;
    }

    private static void show(DialogueContext ctx, Dialogue d, String nodeId) {
        Dialogue.Node node = d.nodes.get(nodeId);
        for (DialogueActions.Action a : node.onShow()) a.run(ctx);
        if (ctx.ended()) {
            end(ctx.player, true);
            return;
        }
        Component name = ctx.player.getName();
        List<Component> pages = new ArrayList<>();
        for (String p : node.pages()) pages.add(Component.translatable(p, name));
        List<Component> options = new ArrayList<>();
        List<Integer> shown = new ArrayList<>();
        for (int i = 0; i < node.options().size(); i++) {
            Dialogue.Option o = node.options().get(i);
            if (!o.condition().test(ctx)) continue;
            shown.add(i);
            options.add(Component.translatable(o.text(), name));
        }
        SESSIONS.put(ctx.player.getUUID(), new Session(ctx.npc.getId(), d.id, nodeId, shown.stream().mapToInt(Integer::intValue).toArray()));
        ModNetwork.sendToPlayer(ctx.player, new DialogueShowPacket(ctx.npc.getId(), ctx.npc.getDisplayName(), d.accent, pages, options));
    }

    /** Ends the conversation; {@code tellClient} closes the window there. */
    public static void end(ServerPlayer player, boolean tellClient) {
        Session s = SESSIONS.remove(player.getUUID());
        if (s == null) return;
        if (player.level().getEntity(s.npcId) instanceof NpcEntity npc) npc.stopTalking(player);
        if (tellClient) ModNetwork.sendToPlayer(player, DialogueShowPacket.close());
        if (s.after != null) s.after.run();
    }

    public static boolean talking(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 10 != 0) return;
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return;
        if (s.after != null) {
            if (!player.isAlive()) end(player, true);
            return;
        }
        Entity e = player.level().getEntity(s.npcId);
        if (!(e instanceof NpcEntity npc) || !npc.isAlive() || !player.isAlive() || player.distanceTo(npc) > MAX_DISTANCE) {
            end(player, true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) end(player, false);
    }

    private DialogueManager() {}
}
