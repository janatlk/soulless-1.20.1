package com.soulless.registry;

import com.soulless.Soulless;
import com.soulless.world.SenseiDojoPiece;
import com.soulless.world.SenseiDojoStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Structures; where they generate is data: worldgen/structure, worldgen/structure_set, tags/worldgen/biome. */
public final class ModStructures {
    public static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Soulless.MODID);
    public static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, Soulless.MODID);

    public static final RegistryObject<StructureType<SenseiDojoStructure>> SENSEI_DOJO = TYPES.register("sensei_dojo",
            () -> () -> SenseiDojoStructure.CODEC);
    public static final RegistryObject<StructurePieceType> SENSEI_DOJO_PIECE = PIECES.register("sensei_dojo",
            () -> (StructurePieceType) SenseiDojoPiece::new);

    public static final RegistryObject<StructureType<com.soulless.world.AbandonedShrineStructure>> ABANDONED_SHRINE = TYPES.register("abandoned_shrine",
            () -> () -> com.soulless.world.AbandonedShrineStructure.CODEC);
    public static final RegistryObject<StructurePieceType> ABANDONED_SHRINE_PIECE = PIECES.register("abandoned_shrine",
            () -> (StructurePieceType) com.soulless.world.AbandonedShrinePiece::new);

    private ModStructures() {}
}
