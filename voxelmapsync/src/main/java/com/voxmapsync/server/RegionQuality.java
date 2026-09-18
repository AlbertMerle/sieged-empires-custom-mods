package com.voxmapsync.server;

import com.voxmapsync.config.SyncConfig;
import com.voxmapsync.server.mca.McaReader;

import java.util.List;

/**
 * Central quality gates for MCA surfaces and VoxelMap region zips.
 * Rejects partial chunk grids (visible 16×16 seams), flat-water placeholders, and sparse columns.
 */
public final class RegionQuality {
	public enum RejectReason {
		NONE,
		INVALID_ZIP,
		TOO_MANY_SENTINELS,
		TOO_FEW_CHUNKS,
		FLAT_WATER_GARBAGE,
		SPARSE_CHUNK_COLUMNS
	}

	public record Assessment(RejectReason reason, String detail) {
		public boolean ok() {
			return reason == RejectReason.NONE;
		}
	}

	private static final int CHUNKS_PER_REGION = 16 * 16;
	private static final int COLUMNS_PER_CHUNK = 16 * 16;

	private RegionQuality() {
	}

	public static Assessment assessSurfaces(List<McaReader.ChunkSurface> surfaces) {
		if (surfaces == null || surfaces.isEmpty()) {
			return new Assessment(RejectReason.TOO_FEW_CHUNKS, "no surfaces");
		}
		float sentinel = McaReader.sentinelFraction(surfaces);
		if (sentinel > SyncConfig.maxSentinelFraction) {
			return new Assessment(RejectReason.TOO_MANY_SENTINELS,
					"sentinel=" + String.format("%.3f", sentinel));
		}
		// Complete region tiles (256 chunks) from MCA are real terrain — including oceans.
		if (surfaces.size() >= SyncConfig.minChunksPerRegion) {
			return new Assessment(RejectReason.NONE, "ok");
		}
		// Partial tiles: reject mid-Chunky flat-water placeholder junk.
		if (SyncConfig.rejectFlatWaterGarbage
				&& isFlatWaterSurfaces(surfaces)
				&& !isLegitimateOceanSurfaces(surfaces)) {
			return new Assessment(RejectReason.FLAT_WATER_GARBAGE, "uniform y=64 water");
		}
		return new Assessment(RejectReason.TOO_FEW_CHUNKS,
				"chunks=" + surfaces.size() + " need>=" + SyncConfig.minChunksPerRegion);
	}

	/** Per VoxelMap region tile (16×16 chunks). Every chunk must be present and column-dense. */
	public static Assessment assessRegionSubset(List<McaReader.ChunkSurface> subset, int regionX, int regionZ) {
		Assessment base = assessSurfaces(subset);
		if (!base.ok()) {
			return base;
		}
		if (subset.size() < SyncConfig.minChunksPerRegion) {
			return new Assessment(RejectReason.TOO_FEW_CHUNKS,
					"region " + regionX + "," + regionZ + " chunks=" + subset.size()
							+ " need=" + SyncConfig.minChunksPerRegion);
		}
		for (McaReader.ChunkSurface surface : subset) {
			Assessment chunk = assessChunkSurface(surface);
			if (!chunk.ok()) {
				return new Assessment(chunk.reason(),
						"region " + regionX + "," + regionZ + " " + chunk.detail());
			}
		}
		return new Assessment(RejectReason.NONE, "ok");
	}

	public static Assessment assessZip(byte[] zipBytes) {
		if (!VoxelMapZipReader.isValidRegionZip(zipBytes)) {
			return new Assessment(RejectReason.INVALID_ZIP, "missing data layer");
		}
		int explored = VoxelMapZipReader.exploredChunkCount(zipBytes);
		float sentinel = VoxelMapZipReader.sentinelFraction(zipBytes);
		if (sentinel > SyncConfig.maxSentinelFraction) {
			return new Assessment(RejectReason.TOO_MANY_SENTINELS,
					"sentinel=" + String.format("%.3f", sentinel));
		}
		// Complete region zips are real terrain (oceans included).
		if (explored >= SyncConfig.minChunksPerRegion) {
			return new Assessment(RejectReason.NONE, "ok");
		}
		// Partial zips: reject mid-Chunky flat-water placeholder junk.
		if (SyncConfig.rejectFlatWaterGarbage
				&& VoxelMapZipReader.isFlatWaterGarbage(zipBytes)
				&& !VoxelMapZipReader.isLegitimateOceanZip(zipBytes)) {
			return new Assessment(RejectReason.FLAT_WATER_GARBAGE, "uniform y=64 water");
		}
		return new Assessment(RejectReason.TOO_FEW_CHUNKS,
				"exploredChunks=" + explored + " need>=" + SyncConfig.minChunksPerRegion);
	}

	private static Assessment assessChunkSurface(McaReader.ChunkSurface surface) {
		int populated = 0;
		for (int h : surface.heights()) {
			if (h != Short.MIN_VALUE) {
				populated++;
			}
		}
		int minColumns = Math.max(1, (int) Math.ceil(COLUMNS_PER_CHUNK * SyncConfig.minColumnCoverageFraction));
		if (populated < minColumns) {
			return new Assessment(RejectReason.SPARSE_CHUNK_COLUMNS,
					"chunk " + surface.chunkX() + "," + surface.chunkZ()
							+ " columns=" + populated + " need>=" + minColumns);
		}
		return new Assessment(RejectReason.NONE, "ok");
	}

	/**
	 * Real ocean tiles are often flat water at sea level — distinguish from Chunky placeholder
	 * junk by ocean-floor depth and/or ocean biomes on explored columns.
	 */
	public static boolean isLegitimateOceanSurfaces(List<McaReader.ChunkSurface> surfaces) {
		if (surfaces == null || surfaces.isEmpty()) {
			return false;
		}
		long waterColumns = 0;
		long oceanFloorColumns = 0;
		long oceanBiomeColumns = 0;
		for (McaReader.ChunkSurface surface : surfaces) {
			for (int i = 0; i < COLUMNS_PER_CHUNK; i++) {
				int h = surface.heights()[i];
				if (h == Short.MIN_VALUE) {
					continue;
				}
				String block = surface.blocks()[i];
				if (isWaterName(block)) {
					waterColumns++;
					if (surface.oceanHeights()[i] != Short.MIN_VALUE) {
						oceanFloorColumns++;
					}
				}
				if (isOceanBiome(surface.biomes()[i])) {
					oceanBiomeColumns++;
				}
			}
		}
		if (waterColumns < 64) {
			return false;
		}
		return oceanFloorColumns >= waterColumns / 2
				|| oceanBiomeColumns >= waterColumns / 2;
	}

	static boolean isOceanBiome(String biome) {
		if (biome == null || biome.isBlank()) {
			return false;
		}
		return biome.contains("ocean") || biome.contains("river") || biome.contains("beach");
	}

	private static boolean isFlatWaterSurfaces(List<McaReader.ChunkSurface> surfaces) {
		long explored = 0;
		long flatWater = 0;
		for (McaReader.ChunkSurface surface : surfaces) {
			for (int i = 0; i < 256; i++) {
				int h = surface.heights()[i];
				if (h == Short.MIN_VALUE) {
					continue;
				}
				explored++;
				if ((h == 63 || h == 64) && isWaterName(surface.blocks()[i])) {
					flatWater++;
				}
			}
		}
		if (explored < 64) {
			return false;
		}
		return (float) flatWater / (float) explored >= SyncConfig.flatWaterRejectFraction;
	}

	private static boolean isWaterName(String name) {
		return "minecraft:water".equals(name) || "minecraft:flowing_water".equals(name);
	}
}
