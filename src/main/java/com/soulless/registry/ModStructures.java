package com.soulless.registry;

import com.soulless.Soulless;
import com.soulless.world.DrownedSoulsStructure;
import com.soulless.world.SoulAltarStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Structures; where they generate is data: worldgen/structure, worldgen/structure_set, tags/worldgen/biome. */
public final class ModStructures {
    public static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Soulless.MODID);
    public static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, Soulless.MODID);

    public static final RegistryObject<StructureType<SoulAltarStructure>> SOUL_ALTAR = TYPES.register("soul_altar", () -> () -> SoulAltarStructure.CODEC);
    public static final RegistryObject<StructurePieceType> SOUL_ALTAR_PIECE = PIECES.register("soul_altar",
            () -> (StructurePieceType) SoulAltarStructure.Piece::new);

    public static final RegistryObject<StructureType<DrownedSoulsStructure>> DROWNED_SOULS = TYPES.register("drowned_souls",
            () -> () -> DrownedSoulsStructure.CODEC);
    public static final RegistryObject<StructurePieceType> DROWNED_SOULS_PIECE = PIECES.register("drowned_souls",
            () -> (StructurePieceType) DrownedSoulsStructure.Piece::new);

    /** What each seeking eye flies toward (data/soulless/tags/worldgen/structure). */
    public static final TagKey<Structure> EYE_OF_LAMENT = TagKey.create(Registries.STRUCTURE, Soulless.id("eye_of_lament_located"));
    public static final TagKey<Structure> EYE_OF_DROWNED = TagKey.create(Registries.STRUCTURE, Soulless.id("eye_of_drowned_located"));

    private ModStructures() {}
}
