package com.tertonbiome;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * Terralith skylands occupy a thin ocean-climate slot and place floating islands
 * as biome features ({@code terralith:skylands/...} at Y≈230). Ban both the biome
 * pick and those features so sky islands do not generate.
 */
public final class SkylandBan {
	private SkylandBan() {}

	public static final ResourceKey<Biome> SNOWY_SHIELD = terralith("snowy_shield");
	public static final ResourceKey<Biome> HIGHLANDS = terralith("highlands");
	public static final ResourceKey<Biome> BLOOMING_VALLEY = terralith("blooming_valley");
	public static final ResourceKey<Biome> TROPICAL_JUNGLE = terralith("tropical_jungle");

	/**
	 * Erosion just above Terralith's skyland window (skylands use erosion ≤ 0.05
	 * in continentalness −0.745…−0.455). Re-querying with this lets Terralith
	 * pick the neighboring ocean/land parameter instead of a hardcoded biome.
	 */
	public static final float EROSION_OUTSIDE_SKYLANDS = 0.35f;

	public static boolean isSkylandBiome(Holder<Biome> biome) {
		return biome.unwrapKey().map(SkylandBan::isSkylandBiomeKey).orElse(false);
	}

	public static boolean isSkylandBiomeKey(ResourceKey<Biome> key) {
		Identifier id = key.identifier();
		if (!"terralith".equals(id.getNamespace())) {
			return false;
		}
		String path = id.getPath();
		return path.equals("skylands") || path.startsWith("skylands_");
	}

	public static boolean isSkylandFeature(Holder<ConfiguredFeature<?, ?>> feature) {
		return feature.unwrapKey().map(key -> {
			Identifier id = key.identifier();
			return "terralith".equals(id.getNamespace()) && id.getPath().startsWith("skylands/");
		}).orElse(false);
	}

	/**
	 * Ground biomes matching the old worldgen2 climate map, then vanilla fallbacks.
	 * Used only if an erosion re-query still returns a skyland.
	 */
	@SuppressWarnings("unchecked")
	public static ResourceKey<Biome>[] fallbacksFor(Holder<Biome> skyland) {
		String path = skyland.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
		return switch (path) {
			case "skylands_winter" -> new ResourceKey[] {
				SNOWY_SHIELD, Biomes.SNOWY_TAIGA, Biomes.SNOWY_PLAINS, Biomes.PLAINS
			};
			case "skylands_summer" -> new ResourceKey[] {
				TROPICAL_JUNGLE, Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.SAVANNA
			};
			case "skylands_spring" -> new ResourceKey[] {
				BLOOMING_VALLEY, Biomes.FLOWER_FOREST, Biomes.FOREST, Biomes.PLAINS
			};
			case "skylands_autumn", "skylands" -> new ResourceKey[] {
				HIGHLANDS, Biomes.FOREST, Biomes.TAIGA, Biomes.PLAINS
			};
			default -> new ResourceKey[] {
				HIGHLANDS, Biomes.FOREST, Biomes.PLAINS
			};
		};
	}

	private static ResourceKey<Biome> terralith(String path) {
		return ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("terralith", path));
	}
}
