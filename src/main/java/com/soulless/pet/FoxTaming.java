package com.soulless.pet;

import com.soulless.Soulless;
import com.soulless.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Taming: sweet berries to a wild white (snow) fox - one in three takes (hearts), else smoke, as with wolves. A tamed
 * fox becomes a {@link TamedFox}. Red foxes keep the vanilla behaviour (berries make them breed). Wild foxes run from
 * players unless they sneak up on them.
 */
@Mod.EventBusSubscriber(modid = Soulless.MODID)
public final class FoxTaming {
    private static final int CHANCE = 3;

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget().getClass() != Fox.class || !(event.getTarget() instanceof Fox fox) || fox.getVariant() != Fox.Type.SNOW) return;
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (!stack.is(Items.SWEET_BERRIES)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!player.getAbilities().instabuild) stack.shrink(1);
        fox.playSound(SoundEvents.FOX_EAT, 1, 1);
        if (level.random.nextInt(CHANCE) != 0) {
            level.sendParticles(ParticleTypes.SMOKE, fox.getX(), fox.getY() + 0.5, fox.getZ(), 7, 0.3, 0.3, 0.3, 0.02);
            return;
        }
        TamedFox pet = fox.convertTo(ModEntities.TAMED_FOX.get(), true);
        if (pet == null) return;
        pet.tame(player);
        level.sendParticles(ParticleTypes.HEART, pet.getX(), pet.getY() + 0.6, pet.getZ(), 7, 0.4, 0.3, 0.4, 0);
        level.playSound(null, pet.getX(), pet.getY(), pet.getZ(), SoundEvents.FOX_AMBIENT, pet.getSoundSource(), 1, 1.3F);
    }

    private FoxTaming() {}
}
