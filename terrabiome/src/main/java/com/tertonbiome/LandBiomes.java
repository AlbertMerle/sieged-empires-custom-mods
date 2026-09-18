package com.tertonbiome;

import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

/** Shared land vs water/coast checks for post-filters. */
public final class LandBiomes {
	private LandBiomes() {}

	public static boolean isWaterOrCoast(Holder<Biome> biome) {
		return biome.is(BiomeTags.IS_OCEAN)
			|| biome.is(BiomeTags.IS_RIVER)
			|| biome.is(BiomeTags.IS_BEACH);
	}

	public static boolean isLand(Holder<Biome> biome) {
		return !isWaterOrCoast(biome);
	}
}
