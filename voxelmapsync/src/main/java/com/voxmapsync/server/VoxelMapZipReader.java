package com.voxmapsync.server;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads quality metrics from VoxelMap-compatible region cache zips.
 */
public final class VoxelMapZipReader {
	private static final int REGION_SIZE = VoxelMapZipWriter.REGION_SIZE;
	private static final int LAYERS = VoxelMapZipWriter.LAYERS;
	private static final int HEIGHTPOS = 0;

	private VoxelMapZipReader() {
	}

	public static float sentinelFraction(byte[] zipBytes) {
		byte[] data = readDataLayer(zipBytes);
		if (data == null) {
			return 1f;
		}
		long sentinel = 0;
		for (int pz = 0; pz < REGION_SIZE; pz++) {
			for (int px = 0; px < REGION_SIZE; px++) {
				if (getShort(data, px, pz, HEIGHTPOS) == Short.MIN_VALUE) {
					sentinel++;
				}
			}
		}
		return (float) sentinel / (float) (REGION_SIZE * REGION_SIZE);
	}

	/** Count 16×16 chunks whose NW column is explored (non-sentinel height). */
	public static int exploredChunkCount(byte[] zipBytes) {
		byte[] data = readDataLayer(zipBytes);
		if (data == null) {
			return 0;
		}
		int explored = 0;
		for (int cz = 0; cz < 16; cz++) {
			for (int cx = 0; cx < 16; cx++) {
				int px = cx * 16;
				int pz = cz * 16;
				if (getShort(data, px, pz, HEIGHTPOS) != Short.MIN_VALUE) {
					explored++;
				}
			}
		}
		return explored;
	}

	public static boolean isValidRegionZip(byte[] zipBytes) {
		return readDataLayer(zipBytes) != null;
	}

	/**
	 * Detect placeholder tiles: nearly all explored columns are height 64 + water
	 * (common mid-Chunky / bad heightmap artifact).
	 */
	public static boolean isFlatWaterGarbage(byte[] zipBytes) {
		if (isLegitimateOceanZip(zipBytes)) {
			return false;
		}
		byte[] data = readDataLayer(zipBytes);
		if (data == null) {
			return true;
		}
		java.util.Map<Integer, String> blockKey = readBlockKey(zipBytes);
		java.util.Map<Integer, String> biomeKey = readBiomeKey(zipBytes);
		long explored = 0;
		long flatWater = 0;
		long flatSeaLevel = 0;
		for (int pz = 0; pz < REGION_SIZE; pz++) {
			for (int px = 0; px < REGION_SIZE; px++) {
				int height = getShort(data, px, pz, HEIGHTPOS);
				if (height == Short.MIN_VALUE) {
					continue;
				}
				explored++;
				if (height == 63 || height == 64) {
					flatSeaLevel++;
					int blockId = getShort(data, px, pz, 2);
					String block = blockKey.getOrDefault(blockId, "minecraft:air");
					if ("minecraft:water".equals(block) || "minecraft:flowing_water".equals(block)) {
						flatWater++;
					}
				}
			}
		}
		if (explored < 64) {
			return false;
		}
		float threshold = com.voxmapsync.config.SyncConfig.flatWaterRejectFraction;
		if ((float) flatWater / (float) explored >= threshold) {
			return true;
		}
		// Fallback: uniform sea-level height across the tile (placeholder heightmap junk)
		return (float) flatSeaLevel / (float) explored >= threshold;
	}

	/**
	 * Real ocean tiles are often flat water at sea level — distinguish from Chunky placeholder
	 * junk by ocean-floor depth and/or ocean biomes on explored columns.
	 */
	public static boolean isLegitimateOceanZip(byte[] zipBytes) {
		byte[] data = readDataLayer(zipBytes);
		if (data == null) {
			return false;
		}
		java.util.Map<Integer, String> blockKey = readBlockKey(zipBytes);
		java.util.Map<Integer, String> biomeKey = readBiomeKey(zipBytes);
		long waterColumns = 0;
		long oceanFloorColumns = 0;
		long oceanBiomeColumns = 0;
		for (int pz = 0; pz < REGION_SIZE; pz++) {
			for (int px = 0; px < REGION_SIZE; px++) {
				int height = getShort(data, px, pz, HEIGHTPOS);
				if (height == Short.MIN_VALUE) {
					continue;
				}
				int blockId = getShort(data, px, pz, 2);
				String block = blockKey.getOrDefault(blockId, "minecraft:air");
				if ("minecraft:water".equals(block) || "minecraft:flowing_water".equals(block)) {
					waterColumns++;
					if (getShort(data, px, pz, 5) != Short.MIN_VALUE) {
						oceanFloorColumns++;
					}
				}
				int biomeId = getShort(data, px, pz, 20);
				String biome = biomeKey.getOrDefault(biomeId, "minecraft:plains");
				if (RegionQuality.isOceanBiome(biome)) {
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

	private static java.util.Map<Integer, String> readBiomeKey(byte[] zipBytes) {
		java.util.Map<Integer, String> ids = new java.util.HashMap<>();
		ids.put(0, "minecraft:plains");
		try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				if ("biomes".equals(entry.getName())) {
					String text = new String(zis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
					for (String line : text.split("\\r?\\n")) {
						line = line.trim();
						if (line.isEmpty()) {
							continue;
						}
						int space = line.indexOf(' ');
						if (space <= 0) {
							continue;
						}
						try {
							int id = Integer.parseInt(line.substring(0, space));
							String name = line.substring(space + 1).trim();
							ids.put(id, name);
						} catch (NumberFormatException ignored) {
						}
					}
					break;
				}
				zis.closeEntry();
			}
		} catch (IOException ignored) {
		}
		return ids;
	}

	private static java.util.Map<Integer, String> readBlockKey(byte[] zipBytes) {
		java.util.Map<Integer, String> ids = new java.util.HashMap<>();
		ids.put(0, "minecraft:air");
		try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				if ("key".equals(entry.getName())) {
					String text = new String(zis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
					for (String line : text.split("\\r?\\n")) {
						line = line.trim();
						if (line.isEmpty()) {
							continue;
						}
						int space = line.indexOf(' ');
						if (space <= 0) {
							continue;
						}
						try {
							int id = Integer.parseInt(line.substring(0, space));
							String name = line.substring(space + 1).trim();
							ids.put(id, name);
						} catch (NumberFormatException ignored) {
						}
					}
					break;
				}
				zis.closeEntry();
			}
		} catch (IOException ignored) {
		}
		return ids;
	}

	private static byte[] readDataLayer(byte[] zipBytes) {
		if (zipBytes == null || zipBytes.length == 0) {
			return null;
		}
		try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				if ("data".equals(entry.getName())) {
					byte[] data = zis.readAllBytes();
					zis.closeEntry();
					if (data.length < REGION_SIZE * REGION_SIZE * LAYERS) {
						return null;
					}
					return data;
				}
				zis.closeEntry();
			}
		} catch (IOException ignored) {
		}
		return null;
	}

	private static int getShort(byte[] data, int x, int z, int layer) {
		int layerSize = REGION_SIZE * REGION_SIZE;
		int idx0 = layer * layerSize + z * REGION_SIZE + x;
		int idx1 = (layer + 1) * layerSize + z * REGION_SIZE + x;
		if (idx1 >= data.length) {
			return Short.MIN_VALUE;
		}
		int hi = data[idx0] & 0xFF;
		int lo = data[idx1] & 0xFF;
		return (short) ((hi << 8) | lo);
	}
}
