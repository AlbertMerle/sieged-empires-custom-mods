package com.shiftyocean;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Ocean biomes for currents: vanilla tags, convention tags, this mod's
 * {@code #shiftyocean:oceans} list, and any biome whose id path is an ocean.
 */
public final class OceanBiomes {
	public static final TagKey<Biome> C_IS_OCEAN = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("c", "is_ocean"));
	public static final TagKey<Biome> C_IS_DEEP_OCEAN = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("c", "is_deep_ocean"));
	public static final TagKey<Biome> C_IS_SHALLOW_OCEAN = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("c", "is_shallow_ocean"));
	public static final TagKey<Biome> SHIFTYOCEAN_OCEANS = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(Shiftyocean.MOD_ID, "oceans"));

	private OceanBiomes() {
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
