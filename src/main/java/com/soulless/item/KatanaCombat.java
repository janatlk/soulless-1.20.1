package com.soulless.item;

import com.soulless.Soulless;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the katana combo. The client announces each attack's combo step just before the attack
 * (KatanaCutPacket); the third step, the thrust (tsuki), hits only its target - no sweep - for more damage.
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class KatanaCombat {
    public static final int THRUST_STEP = 2;
    /** The thrust deals this much more than a normal cut. */
    public static final float THRUST_DAMAGE_MULT = 1.6F;
    /** An announced step only counts for an attack that follows within this many ticks. */
    private static final int ANNOUNCE_TICKS = 4;

    private record Announced(int step, long time) {}

    private static final Map<UUID, Announced> ANNOUNCED = new HashMap<>();
    /** The player whose thrust is being resolved right now (set for the length of one attack). */
    @Nullable private static ServerPlayer thrusting;

    public static void announce(ServerPlayer player, int step) {
        if (step < 0 || step > THRUST_STEP) return;
        ANNOUNCED.put(player.getUUID(), new Announced(step, player.level().getGameTime()));
        if (com.soulless.dev.DevAutoTest.ENABLED) Soulless.LOGGER.info("[autotest] katana step {} announced", step);
    }

    /** Whether this player's attack right now is the thrust. */
    private static boolean isThrust(ServerPlayer player) {
        Announced a = ANNOUNCED.get(player.getUUID());
        return a != null && a.step == THRUST_STEP && player.level().getGameTime() - a.time <= ANNOUNCE_TICKS
                && player.getMainHandItem().getItem() instanceof SoulKatanaItem;
    }

    /** Asked by the katana when vanilla decides whether an attack sweeps. */
    public static boolean noSweep(ItemStack stack) {
        return thrusting != null && thrusting.getMainHandItem() == stack;
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        thrusting = event.getEntity() instanceof ServerPlayer sp && isThrust(sp) ? sp : null;
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (thrusting != null && event.getSource().getEntity() == thrusting && event.getSource().getDirectEntity() == thrusting) {
            event.setAmount(event.getAmount() * THRUST_DAMAGE_MULT);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) thrusting = null;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ANNOUNCED.remove(event.getEntity().getUUID());
    }

    private KatanaCombat() {}
}
