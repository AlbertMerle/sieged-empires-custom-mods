package com.tertonbiome;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

import java.util.List;

/**
 * Terrabiome-owned land placement. Water/coast/cave classification still comes
 * from the underlying multi-noise source, but Terralith's land result does not:
 * one deterministic catalog biome owns each low-frequency region.
 */
public final class DirectLandBiomeSelector {
	private DirectLandBiomeSelector() {}

	@FunctionalInterface
	public interface BiomeFinder {
		Holder<Biome> find(ResourceKey<Biome> key);
	}

	public static Holder<Biome> select(
		Holder<Biome> classified,
		int blockX,
		int blockZ,
		ClimateZone zone,
		boolean wantWet,
		BiomeFinder finder
	) {
		TerratonicbiomesConfig config = TerratonicbiomesConfig.get();
		if (!config.enforceZoneBiomes || LandBiomes.isWaterOrCoast(classified)) {
			return classified;
		}
		return selectLand(classified, blockX, blockZ, zone, wantWet, finder);
	}

	/** Select catalog land even when the underlying classification was beach. */
	public static Holder<Biome> selectLand(
		Holder<Biome> fallback,
		int blockX,
		int blockZ,
		ClimateZone zone,
		boolean wantWet,
		BiomeFinder finder
	) {
		TerratonicbiomesConfig config = TerratonicbiomesConfig.get();
		if (!config.enforceZoneBiomes) {
			return fallback;
		}
		ZoneBiomeCatalog.HotMoisture moisture = null;
		if (zone == ClimateZone.HOT && config.hotDesertJungleMix) {
			moisture = wantWet
				? ZoneBiomeCatalog.HotMoisture.WET
				: ZoneBiomeCatalog.HotMoisture.DRY;
		}
		List<ResourceKey<Biome>> candidates = ZoneBiomeCatalog.candidates(zone, false, moisture);
		if (candidates.isEmpty()) {
			candidates = ZoneBiomeCatalog.candidates(zone, false);
		}
		if (candidates.isEmpty()) {
			return fallback;
		}

		int regionChunks = config.absorbSmallLandBiomes
			? Math.max(1, config.minLandBiomeSize)
			: 1;
		int regionBlocks = regionChunks * 16;
		long owner = nearestRegion(blockX, blockZ, regionBlocks);
		int regionX = (int) (owner >> 32);
		int regionZ = (int) owner;
		int index = Math.floorMod(mix(regionX, regionZ, zone.ordinal(), moisture), candidates.size());

		Holder<Biome> selected = finder.find(candidates.get(index));
		return selected != null ? selected : fallback;
	}

	/**
	 * Jittered Voronoi owner. Nine integer-hash probes replace the old 32 full
	 * biome placements and avoid axis-aligned square biome borders.
	 */
	private static long nearestRegion(int blockX, int blockZ, int regionBlocks) {
		int baseX = Math.floorDiv(blockX, regionBlocks);
		int baseZ = Math.floorDiv(blockZ, regionBlocks);
		double bestDistance = Double.POSITIVE_INFINITY;
		int bestX = baseX;
		int bestZ = baseZ;
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				int cellX = baseX + dx;
				int cellZ = baseZ + dz;
				int jitter = mixCell(cellX, cellZ);
				double pointX = (cellX + 0.15 + ((jitter & 0xffff) / 65535.0) * 0.70) * regionBlocks;
				double pointZ = (cellZ + 0.15 + (((jitter >>> 16) & 0xffff) / 65535.0) * 0.70) * regionBlocks;
				double offX = blockX - pointX;
				double offZ = blockZ - pointZ;
				double distance = offX * offX + offZ * offZ;
				if (distance < bestDistance) {
					bestDistance = distance;
					bestX = cellX;
					bestZ = cellZ;
				}
			}
		}
		return ((long) bestX << 32) | (bestZ & 0xffffffffL);
	}

	private static int mixCell(int cellX, int cellZ) {
		int n = cellX * 0x1f1f1f1f ^ cellZ * 0x5f356495 ^ 0x4f1bbcdc;
		n ^= n >>> 16;
		n *= 0x7feb352d;
		n ^= n >>> 15;
		n *= 0x846ca68b;
		return n ^ (n >>> 16);
	}

	private static int mix(
		int regionX,
		int regionZ,
		int zone,
		ZoneBiomeCatalog.HotMoisture moisture
	) {
		int n = regionX * 0x1f1f1f1f ^ regionZ * 0x5f356495;
		n ^= zone * 0x6c8e9cf5;
		n ^= moisture == null ? 0 : (moisture.ordinal() + 1) * 0x27d4eb2d;
		n ^= n >>> 16;
		n *= 0x7feb352d;
		n ^= n >>> 15;
		n *= 0x846ca68b;
		return n ^ (n >>> 16);
	}
}
