package com.soulless.npc.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * One character's conversation, read from {@code data/<namespace>/dialogues/<name>.json}. The format:
 * <pre>{@code
 * {
 *   "accent": "#B3343A",                   // the colour of the dialogue window's frame and the speaker's name
 *   "start": [                             // where a conversation begins: the first entry whose "if" holds
 *     { "if": {"type": "flag", "flag": "sensei.met", "not": true}, "node": "first" },
 *     { "node": "greet" }                  // (or simply "start": "greet")
 *   ],
 *   "nodes": {
 *     "greet": {
 *       "text": ["dialogue.x.greet.1", "dialogue.x.greet.2"],   // lang keys; several = pages, clicked through
 *       "do": [ {"type": "set_flag", "flag": "sensei.met"} ],   // actions run when the node is shown (optional)
 *       "options": [
 *         { "text": "dialogue.x.opt.who", "goto": "who" },
 *         { "text": "dialogue.x.opt.gift", "do": [{"type": "give", "item": "minecraft:bread"}], "goto": "greet" },
 *         { "text": "dialogue.x.opt.secret", "if": {"type": "flag", "scope": "npc", "flag": "scroll_sold"}, "goto": "after" },
 *         { "text": "dialogue.x.opt.bye" }                      // no "goto": the conversation ends
 *       ]
 *     }
 *   }
 * }
 * }</pre>
 * Texts are lang keys; {@code %1$s} in them is the player's name. "if" is one condition or a list (all must hold);
 * any condition takes {@code "not": true}. An option whose "if" fails is not shown. The condition and action types
 * live in {@link DialogueConditions} and {@link DialogueActions} - new ones are registered there in code.
 */
public final class Dialogue {
    public record Start(Predicate<DialogueContext> condition, String node) {}

    public record Node(List<String> pages, List<DialogueActions.Action> onShow, List<Option> options) {}

    /** {@code next} null = the conversation ends after the option's actions. */
    public record Option(String text, Predicate<DialogueContext> condition, List<DialogueActions.Action> actions, @Nullable String next) {}

    public static final String END = "end";

    public final ResourceLocation id;
    public final int accent;
    public final List<Start> start;
    public final Map<String, Node> nodes;

    private Dialogue(ResourceLocation id, int accent, List<Start> start, Map<String, Node> nodes) {
        this.id = id;
        this.accent = accent;
        this.start = start;
        this.nodes = nodes;
    }

    /** The node a conversation opens with for this player, or null if no start rule holds. */
    @Nullable
    public String startNode(DialogueContext ctx) {
        for (Start s : start) {
            if (s.condition.test(ctx)) return s.node;
        }
        return null;
    }

    public static Dialogue parse(ResourceLocation id, JsonObject json) {
        int accent = parseColor(GsonHelper.getAsString(json, "accent", "#C9A45C"));
        List<Start> start = new ArrayList<>();
        JsonElement s = json.get("start");
        if (s == null) throw new JsonParseException("missing \"start\"");
        if (s.isJsonPrimitive()) {
            start.add(new Start(ctx -> true, s.getAsString()));
        } else {
            for (JsonElement e : s.getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                start.add(new Start(DialogueConditions.parse(o.get("if")), GsonHelper.getAsString(o, "node")));
            }
        }
        Map<String, Node> nodes = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : GsonHelper.getAsJsonObject(json, "nodes").entrySet()) {
            nodes.put(e.getKey(), parseNode(e.getValue().getAsJsonObject()));
        }
        Dialogue d = new Dialogue(id, accent, start, nodes);
        d.check();
        return d;
    }

    private static Node parseNode(JsonObject o) {
        List<String> pages = new ArrayList<>();
        JsonElement t = o.get("text");
        if (t == null) throw new JsonParseException("a node needs \"text\"");
        if (t.isJsonArray()) {
            for (JsonElement p : t.getAsJsonArray()) pages.add(p.getAsString());
        } else {
            pages.add(t.getAsString());
        }
        List<Option> options = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(o, "options", new JsonArray())) {
            JsonObject opt = e.getAsJsonObject();
            String next = GsonHelper.getAsString(opt, "goto", END);
            options.add(new Option(GsonHelper.getAsString(opt, "text"), DialogueConditions.parse(opt.get("if")),
                    DialogueActions.parseList(opt.get("do")), END.equals(next) ? null : next));
        }
        return new Node(pages, DialogueActions.parseList(o.get("do")), options);
    }

    /** Every "goto" and start node must name a node that exists. */
    private void check() {
        for (Start s : start) {
            if (!nodes.containsKey(s.node)) throw new JsonParseException("start names an unknown node \"" + s.node + "\"");
        }
        for (Map.Entry<String, Node> e : nodes.entrySet()) {
            for (Option o : e.getValue().options) {
                if (o.next != null && !nodes.containsKey(o.next)) {
                    throw new JsonParseException("node \"" + e.getKey() + "\": goto an unknown node \"" + o.next + "\"");
                }
            }
        }
    }

    private static int parseColor(String s) {
        String hex = s.startsWith("#") ? s.substring(1) : s;
        return 0xFF000000 | Integer.parseInt(hex, 16);
    }
}
