package com.tertonbiome;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.Set;

/**
 * Vanilla + Terralith underground/cave biomes. None of these may appear at or
 * above {@link SurfaceBiomeBounds#minY()}. Below that Y only vanilla caves may
 * spawn (see {@link CaveBiomeRules}); Terralith caves are always banned.
 */
public final class CaveBiomeBan {
	private CaveBiomeBan() {}

	private static final TagKey<Biome> C_IS_CAVE = TagKey.create(
		Registries.BIOME,
		Identifier.fromNamespaceAndPath("c", "is_cave")
	);
	private static final TagKey<Biome> C_IS_UNDERGROUND = TagKey.create(
		Registries.BIOME,
		Identifier.fromNamespaceAndPath("c", "is_underground")
	);
	private static final TagKey<Biome> TERRALITH_CAVES = TagKey.create(
		Registries.BIOME,
		Identifier.fromNamespaceAndPath("terralith", "caves")
	);

	/** Every Terralith cave biome path (also covered by {@code terralith:caves} tag). */
	private static final Set<String> CAVE_PATHS = Set.of(
		"lush_caves",
		"dripstone_caves",
		"deep_dark",
		"sulfur_caves",
		"cave/andesite_caves",
		"cave/deep_caves",
		"cave/diorite_caves",
		"cave/frostfire_caves",
		"cave/fungal_caves",
		"cave/granite_caves",
		"cave/infested_caves",
		"cave/mantle_caves",
		"cave/thermal_caves",
		"cave/tuff_caves",
		"cave/underground_jungle"
	);

	public static boolean isVanillaCave(Holder<Biome> biome) {
		return biome.is(Biomes.LUSH_CAVES)
			|| biome.is(Biomes.DRIPSTONE_CAVES)
			|| biome.is(Biomes.DEEP_DARK)
			|| biome.is(Biomes.SULFUR_CAVES);
	}

	public static boolean isVanillaCaveKey(ResourceKey<Biome> key) {
		return key == Biomes.LUSH_CAVES
			|| key == Biomes.DRIPSTONE_CAVES
			|| key == Biomes.DEEP_DARK
			|| key == Biomes.SULFUR_CAVES;
	}

	public static boolean isCaveBiome(Holder<Biome> biome) {
		if (isVanillaCave(biome)) {
			return true;
		}
		if (biome.is(C_IS_CAVE) || biome.is(C_IS_UNDERGROUND) || biome.is(TERRALITH_CAVES)) {
			return true;
		}
		return biome.unwrapKey().map(CaveBiomeBan::isCavePath).orElse(false);
	}

	public static boolean isCaveKey(ResourceKey<Biome> key) {
		if (isVanillaCaveKey(key)) {
			return true;
		}
		return isCavePath(key);
	}

	private static boolean isCavePath(ResourceKey<Biome> key) {
		String path = key.identifier().getPath();
		if (CAVE_PATHS.contains(path)) {
			return true;
		}
		return path.startsWith("cave/") || path.contains("/cave/") || path.endsWith("_caves");
	}
}
