package com.tertonbiome.mixin;

import com.tertonbiome.AllowedStructures;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Drops banned structure sets (vanilla + Terralith) from the generator so they
 * are not placed and do not show up in {@code /locate}.
 */
@Mixin(ChunkGeneratorStructureState.class)
public abstract class ChunkGeneratorStructureStateMixin {
	@Inject(method = "createForNormal", at = @At("HEAD"))
	private static void terrabiome$refreshAllowlist(
		RandomState randomState,
		long levelSeed,
		BiomeSource biomeSource,
		HolderLookup<StructureSet> allStructures,
		CallbackInfoReturnable<ChunkGeneratorStructureState> cir
	) {
		AllowedStructures.refresh(allStructures);
	}

	@Inject(method = "hasBiomesForStructureSet", at = @At("HEAD"), cancellable = true)
	private static void terrabiome$skipBannedSets(
		StructureSet structureSet,
		BiomeSource biomeSource,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (!AllowedStructures.isStructureSetAllowed(structureSet)) {
			cir.setReturnValue(false);
		}
	}
}

