package com.siegedempires.session;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Ocean biomes for the join cinematic location title. Mirrors Shiftyocean's
 * coverage (vanilla/convention tags + path heuristics) without a hard dependency.
 */
public final class OceanBiomeHelper {
	private static final TagKey<Biome> C_IS_OCEAN =
			TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("c", "is_ocean"));
	private static final TagKey<Biome> C_IS_DEEP_OCEAN =
			TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("c", "is_deep_ocean"));
	private static final TagKey<Biome> C_IS_SHALLOW_OCEAN =
			TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("c", "is_shallow_ocean"));
	private static final TagKey<Biome> SHIFTYOCEAN_OCEANS =
			TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("shiftyocean", "oceans"));

	private OceanBiomeHelper() {
	}

	public static boolean isOcean(Holder<Biome> biome) {
		if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN)) {
			return true;
		}
		if (biome.is(C_IS_OCEAN) || biome.is(C_IS_DEEP_OCEAN) || biome.is(C_IS_SHALLOW_OCEAN)) {
			return true;
		}
		if (biome.is(SHIFTYOCEAN_OCEANS)) {
			return true;
		}
		return biome.unwrapKey().map(key -> pathLooksLikeOcean(key.identifier().getPath())).orElse(false);
	}

	/** {@code ocean}, {@code frozen_ocean}, {@code deep_warm_ocean}, etc. */
	static boolean pathLooksLikeOcean(String path) {
		return "ocean".equals(path)
				|| path.endsWith("_ocean")
				|| path.startsWith("ocean_")
				|| path.contains("_ocean_");
	}
}
