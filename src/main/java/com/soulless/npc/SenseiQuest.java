package com.soulless.npc;

import com.soulless.Soulless;
import com.soulless.item.KatanaOath;
import com.soulless.item.SoulKatanaItem;
import com.soulless.network.ClientControlPacket;
import com.soulless.network.ModNetwork;
import com.soulless.npc.dialogue.DialogueActions;
import com.soulless.npc.dialogue.DialogueContext;
import com.soulless.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The sensei's training (STORY_KATANA.md, acts 1-2), kept as player dialogue flags (they survive death):
 * <ol>
 * <li>{@link #STUDENT}: he accepts the player and hands over the Training Katana;</li>
 * <li>{@link #PARRY}: the sparring (SenseiEntity) - parry his blows {@link SenseiEntity#SPAR_GOAL} times; this is how
 * the parry is learnt at all;</li>
 * <li>{@link #CLEANSE} / {@link #CLEANSED}: kill {@link #CLEANSE_GOAL} monsters with the Training Katana without
 * striking a single animal or villager (that starts the count over);</li>
 * <li>{@link #SHRINE}: he gives a map to the abandoned shrine where the Scroll of the Family History lies;</li>
 * <li>{@link #BLADE}: the scroll brought back, he gives the Broken Legendary Katana.</li>
 * </ol>
 * The dialogue (data/soulless/dialogues/sensei.json) reads these flags; its actions are registered here.
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class SenseiQuest {
    public static final String STUDENT = "sensei.student", PARRY = "sensei.parry", CLEANSE = "sensei.cleanse",
            CLEANSED = "sensei.cleansed", SHRINE = "sensei.shrine", BLADE = "sensei.blade", KILLS = "sensei.kills";
    public static final int CLEANSE_GOAL = 10;
    public static final TagKey<Structure> SHRINE_MAPS = TagKey.create(Registries.STRUCTURE, Soulless.id("abandoned_shrine"));
    public static final TagKey<Structure> DOJO_MAPS = TagKey.create(Registries.STRUCTURE, Soulless.id("sensei_dojo"));

    private static CompoundTag flags(Player p) {
        return DialogueContext.playerFlags(p);
    }

    public static boolean has(Player p, String flag) {
        return flags(p).getBoolean(flag);
    }

    /** The parry works once the sensei has taught it - or right now, in his sparring (or in creative). */
    public static boolean canParry(Player p) {
        return p.getAbilities().instabuild || has(p, PARRY) || SenseiEntity.sparring(p);
    }

    public static void syncParry(ServerPlayer p) {
        ModNetwork.sendToPlayer(p, new ClientControlPacket(ClientControlPacket.PARRY_KNOWN, canParry(p)));
    }

    /** The sensei says something: in chat, or briefly over the hotbar. */
    public static void say(Player p, String key, boolean brief) {
        Component line = Component.translatable("chat.soulless.sensei_says", Component.translatable(key)).withStyle(ChatFormatting.GRAY);
        if (brief) p.displayClientMessage(line, true);
        else p.sendSystemMessage(line);
    }

    static void learnParry(ServerPlayer p) {
        flags(p).putBoolean(PARRY, true);
        syncParry(p);
        com.soulless.Achievements.grant(p, "sensei_student");
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.2F);
        say(p, "dialogue.soulless.sensei.spar.done", false);
    }

    // ---- the cleansing

    private static boolean cleansing(Player p) {
        return has(p, CLEANSE) && !has(p, CLEANSED);
    }

    private static boolean training(Player p) {
        return p.getMainHandItem().getItem() instanceof SoulKatanaItem k && k.isTraining();
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || !(event.getEntity() instanceof Enemy)) return;
        if (!cleansing(p) || !training(p)) return;
        CompoundTag f = flags(p);
        int kills = f.getInt(KILLS) + 1;
        f.putInt(KILLS, kills);
        p.displayClientMessage(Component.translatable("message.soulless.cleanse_count", kills, CLEANSE_GOAL).withStyle(ChatFormatting.GRAY), true);
        if (kills >= CLEANSE_GOAL) {
            f.putBoolean(CLEANSED, true);
            p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1);
            p.sendSystemMessage(Component.translatable("message.soulless.cleanse_done").withStyle(ChatFormatting.AQUA));
        }
    }

    /** From KatanaCombat: an aimed blow of the Training Katana landed on an animal or a villager. */
    public static void onInnocentStruck(ServerPlayer p, LivingEntity target) {
        if (!cleansing(p) || !training(p) || !KatanaOath.innocent(target)) return;
        if (flags(p).getInt(KILLS) > 0) flags(p).putInt(KILLS, 0);
        p.displayClientMessage(Component.translatable("message.soulless.cleanse_reset").withStyle(ChatFormatting.RED), true);
    }

    // ---- maps

    /** A map to the nearest structure of {@code tag} (an explorer's map with a red X), or empty when none is near. */
    public static ItemStack explorerMap(ServerLevel level, BlockPos from, TagKey<Structure> tag, String nameKey) {
        BlockPos pos = level.findNearestMapStructure(tag, from, 100, false);
        if (pos == null) return ItemStack.EMPTY;
        ItemStack map = MapItem.create(level, pos.getX(), pos.getZ(), (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(level, map);
        MapItemSavedData.addTargetDecoration(map, pos, "+", MapDecoration.Type.RED_X);
        map.setHoverName(Component.translatable(nameKey));
        return map;
    }

    private static void give(ServerPlayer p, ItemStack stack) {
        if (!p.getInventory().add(stack)) p.drop(stack, false);
    }

    // ---- dialogue actions

    public static void registerDialogueActions() {
        DialogueActions.register("soulless:sensei_train", j -> ctx -> {
            ctx.setFlag("player", STUDENT, true);
            give(ctx.player, new ItemStack(ModItems.TRAINING_KATANA.get()));
        });
        DialogueActions.register("soulless:spar", j -> ctx -> {
            ctx.end();
            if (ctx.npc instanceof SenseiEntity s) s.startSpar(ctx.player);
        });
        DialogueActions.register("soulless:cleanse", j -> ctx -> {
            ctx.setFlag("player", CLEANSE, true);
            flags(ctx.player).putInt(KILLS, 0);
        });
        DialogueActions.register("soulless:shrine_map", j -> ctx -> {
            ctx.setFlag("player", SHRINE, true);
            ItemStack map = explorerMap(ctx.player.serverLevel(), ctx.npc.blockPosition(), SHRINE_MAPS, "item.soulless.map.shrine");
            if (map.isEmpty()) say(ctx.player, "dialogue.soulless.sensei.no_shrine", false);
            else give(ctx.player, map);
        });
        DialogueActions.register("soulless:give_blade", j -> ctx -> {
            ctx.player.getInventory().clearOrCountMatchingItems(s -> s.is(ModItems.FAMILY_SCROLL.get()), 1,
                    ctx.player.inventoryMenu.getCraftSlots());
            ctx.setFlag("player", BLADE, true);
            give(ctx.player, new ItemStack(ModItems.BROKEN_KATANA.get()));
        });
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) syncParry(p);
    }

    private SenseiQuest() {}
}
