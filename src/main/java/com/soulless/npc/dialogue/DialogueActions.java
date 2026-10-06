package com.soulless.npc.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Action types for "do" in dialogues (on a node: run when it is shown; on an option: run when it is chosen).
 * Built in:
 * <ul>
 * <li>{@code close} - ends the conversation;</li>
 * <li>{@code set_flag} - {@code flag}, {@code scope} "player"/"npc", {@code value} (default true);</li>
 * <li>{@code give} / {@code take} - {@code item}, {@code count} (default 1);</li>
 * <li>{@code advancement} - {@code id}: awards a Soulless advancement whose criterion is "done";</li>
 * <li>{@code sound} - {@code id}, {@code volume}, {@code pitch}: played at the character;</li>
 * <li>{@code command} - {@code command}: run as the player with operator rights, output hidden (anything else
 * the game can do).</li>
 * </ul>
 * A new type: {@code DialogueActions.register("name", json -> ctx -> ...)}.
 */
public final class DialogueActions {
    @FunctionalInterface
    public interface Action {
        void run(DialogueContext ctx);
    }

    private static final Map<String, Function<JsonObject, Action>> TYPES = new HashMap<>();

    public static void register(String type, Function<JsonObject, Action> factory) {
        TYPES.put(type, factory);
    }

    static {
        register("close", j -> DialogueContext::end);
        register("set_flag", j -> {
            String scope = GsonHelper.getAsString(j, "scope", "player"), flag = GsonHelper.getAsString(j, "flag");
            boolean value = GsonHelper.getAsBoolean(j, "value", true);
            return ctx -> ctx.setFlag(scope, flag, value);
        });
        register("give", j -> {
            Item item = DialogueConditions.item(GsonHelper.getAsString(j, "item"));
            int count = GsonHelper.getAsInt(j, "count", 1);
            return ctx -> {
                ItemStack stack = new ItemStack(item, count);
                if (!ctx.player.getInventory().add(stack)) ctx.player.drop(stack, false);
            };
        });
        register("take", j -> {
            Item item = DialogueConditions.item(GsonHelper.getAsString(j, "item"));
            int count = GsonHelper.getAsInt(j, "count", 1);
            return ctx -> ctx.player.getInventory().clearOrCountMatchingItems(s -> s.is(item), count,
                    ctx.player.inventoryMenu.getCraftSlots());
        });
        register("advancement", j -> {
            String name = GsonHelper.getAsString(j, "id");
            return ctx -> com.soulless.Achievements.grant(ctx.player, name);
        });
        register("sound", j -> {
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(GsonHelper.getAsString(j, "id")));
            if (sound == null) throw new JsonParseException("unknown sound " + j.get("id"));
            float volume = GsonHelper.getAsFloat(j, "volume", 1), pitch = GsonHelper.getAsFloat(j, "pitch", 1);
            return ctx -> ctx.npc.level().playSound(null, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ(), sound,
                    SoundSource.NEUTRAL, volume, pitch);
        });
        register("command", j -> {
            String command = GsonHelper.getAsString(j, "command");
            return ctx -> ctx.player.server.getCommands().performPrefixedCommand(
                    ctx.player.createCommandSourceStack().withPermission(2).withSuppressedOutput(), command);
        });
    }

    /** "do": absent, one action or a list. */
    public static List<Action> parseList(JsonElement json) {
        List<Action> out = new ArrayList<>();
        if (json == null || json.isJsonNull()) return out;
        if (json.isJsonArray()) {
            for (JsonElement e : json.getAsJsonArray()) out.add(parse(e.getAsJsonObject()));
        } else {
            out.add(parse(json.getAsJsonObject()));
        }
        return out;
    }

    private static Action parse(JsonObject o) {
        String type = GsonHelper.getAsString(o, "type");
        Function<JsonObject, Action> factory = TYPES.get(type);
        if (factory == null) throw new JsonParseException("unknown action type \"" + type + "\"");
        return factory.apply(o);
    }

    private DialogueActions() {}
}
