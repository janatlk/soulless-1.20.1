package com.soulless.npc.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Condition types for "if" in dialogues. Each is a JSON object {@code {"type": ..., ...}}; {@code "not": true}
 * inverts any of them. Built in:
 * <ul>
 * <li>{@code flag} - {@code flag}, {@code scope} "player" (default) or "npc";</li>
 * <li>{@code has_item} - {@code item}, {@code count} (default 1): the player carries that many;</li>
 * <li>{@code advancement} - {@code id}: the player has it.</li>
 * </ul>
 * A new type: {@code DialogueConditions.register("name", json -> ctx -> ...)}.
 */
public final class DialogueConditions {
    private static final Map<String, Function<JsonObject, Predicate<DialogueContext>>> TYPES = new HashMap<>();

    public static void register(String type, Function<JsonObject, Predicate<DialogueContext>> factory) {
        TYPES.put(type, factory);
    }

    static {
        register("flag", j -> {
            String scope = GsonHelper.getAsString(j, "scope", "player"), flag = GsonHelper.getAsString(j, "flag");
            return ctx -> ctx.flag(scope, flag);
        });
        register("has_item", j -> {
            Item item = item(GsonHelper.getAsString(j, "item"));
            int count = GsonHelper.getAsInt(j, "count", 1);
            return ctx -> ctx.player.getInventory().countItem(item) >= count;
        });
        register("advancement", j -> {
            ResourceLocation id = new ResourceLocation(GsonHelper.getAsString(j, "id"));
            return ctx -> {
                Advancement adv = ctx.player.server.getAdvancements().getAdvancement(id);
                return adv != null && ctx.player.getAdvancements().getOrStartProgress(adv).isDone();
            };
        });
    }

    /** "if": absent (always true), one condition, or a list (all must hold). */
    public static Predicate<DialogueContext> parse(JsonElement json) {
        if (json == null || json.isJsonNull()) return ctx -> true;
        if (json.isJsonArray()) {
            List<Predicate<DialogueContext>> all = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray()) all.add(parse(e));
            return ctx -> all.stream().allMatch(p -> p.test(ctx));
        }
        JsonObject o = json.getAsJsonObject();
        String type = GsonHelper.getAsString(o, "type");
        Function<JsonObject, Predicate<DialogueContext>> factory = TYPES.get(type);
        if (factory == null) throw new JsonParseException("unknown condition type \"" + type + "\"");
        Predicate<DialogueContext> p = factory.apply(o);
        return GsonHelper.getAsBoolean(o, "not", false) ? p.negate() : p;
    }

    static Item item(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if (item == null) throw new JsonParseException("unknown item \"" + id + "\"");
        return item;
    }

    private DialogueConditions() {}
}
