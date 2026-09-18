package com.voxmapsync.server;

import com.voxmapsync.server.mca.McaReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes VoxelMap-compatible region cache zips (DATA_VERSION 4 layout).
 * Client VoxelMap ColorManager paints from blockstate/biome/height when loaded.
 */
public final class VoxelMapZipWriter {
	public static final int REGION_SIZE = 256;
	public static final int LAYERS = 22;
	public static final int DATA_VERSION = 4;
	/** Bump when converter output semantics change so servers force a full rebuild. */
	public static final int FORMAT_VERSION = 4;

	private static final int HEIGHTPOS = 0;
	private static final int BLOCKSTATEPOS = 2;
	private static final int LIGHTPOS = 4;
	private static final int OCEANFLOORHEIGHTPOS = 5;
	private static final int OCEANFLOORBLOCKSTATEPOS = 7;
	private static final int OCEANFLOORLIGHTPOS = 9;
	private static final int TRANSPARENTHEIGHTPOS = 10;
	private static final int TRANSPARENTBLOCKSTATEPOS = 12;
	private static final int TRANSPARENTLIGHTPOS = 14;
	private static final int FOLIAGEHEIGHTPOS = 15;
	private static final int FOLIAGEBLOCKSTATEPOS = 17;
	private static final int FOLIAGELIGHTPOS = 19;
	private static final int BIOMEIDPOS = 20;

	private VoxelMapZipWriter() {
	}

	public static byte[] writeRegion(int regionX, int regionZ, List<McaReader.ChunkSurface> chunks) throws IOException {
		byte[] data = emptyData();
		Map<String, Integer> blockIds = new HashMap<>();
		Map<String, Integer> biomeIds = new HashMap<>();
		blockIds.put("minecraft:air", 0);
		int[] nextBlock = {1};
		int[] nextBiome = {1};
		biomeIds.put("minecraft:plains", nextBiome[0]++);

		int originChunkX = regionX * 16;
		int originChunkZ = regionZ * 16;

		for (McaReader.ChunkSurface chunk : chunks) {
			int localChunkX = chunk.chunkX() - originChunkX;
			int localChunkZ = chunk.chunkZ() - originChunkZ;
			if (localChunkX < 0 || localChunkX >= 16 || localChunkZ < 0 || localChunkZ >= 16) {
				continue;
			}
			for (int lz = 0; lz < 16; lz++) {
				for (int lx = 0; lx < 16; lx++) {
					int idx = lz * 16 + lx;
					int height = chunk.heights()[idx];
					if (height == Short.MIN_VALUE) {
						continue;
					}
					int px = localChunkX * 16 + lx;
					int pz = localChunkZ * 16 + lz;
					int blockId = idFor(blockIds, nextBlock, chunk.blocks()[idx]);
					int biomeId = idFor(biomeIds, nextBiome, chunk.biomes()[idx]);
					setShort(data, px, pz, HEIGHTPOS, height);
					setShort(data, px, pz, BLOCKSTATEPOS, blockId);
					setByte(data, px, pz, LIGHTPOS, (byte) 255);

					int oceanH = chunk.oceanHeights()[idx];
					if (oceanH != Short.MIN_VALUE) {
						setShort(data, px, pz, OCEANFLOORHEIGHTPOS, oceanH);
						setShort(data, px, pz, OCEANFLOORBLOCKSTATEPOS, idFor(blockIds, nextBlock, chunk.oceanBlocks()[idx]));
						setByte(data, px, pz, OCEANFLOORLIGHTPOS, (byte) 255);
					}

					int transH = chunk.transparentHeights()[idx];
					if (transH != Short.MIN_VALUE) {
						setShort(data, px, pz, TRANSPARENTHEIGHTPOS, transH);
						setShort(data, px, pz, TRANSPARENTBLOCKSTATEPOS, idFor(blockIds, nextBlock, chunk.transparentBlocks()[idx]));
						setByte(data, px, pz, TRANSPARENTLIGHTPOS, (byte) 255);
					}

					int folH = chunk.foliageHeights()[idx];
					if (folH != Short.MIN_VALUE) {
						setShort(data, px, pz, FOLIAGEHEIGHTPOS, folH);
						setShort(data, px, pz, FOLIAGEBLOCKSTATEPOS, idFor(blockIds, nextBlock, chunk.foliageBlocks()[idx]));
						setByte(data, px, pz, FOLIAGELIGHTPOS, (byte) 255);
					}

					setShort(data, px, pz, BIOMEIDPOS, biomeId);
				}
			}
		}

		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try (ZipOutputStream zos = new ZipOutputStream(bos)) {
			put(zos, "data", data);
			put(zos, "key", buildBlockKey(blockIds).getBytes(StandardCharsets.UTF_8));
			put(zos, "biomes", buildBiomeKey(biomeIds).getBytes(StandardCharsets.UTF_8));
			put(zos, "control", ("version:" + DATA_VERSION + "\r\nformat:" + FORMAT_VERSION + "\r\n")
					.getBytes(StandardCharsets.UTF_8));
		}
		return bos.toByteArray();
	}

	public static void writeToFile(Path file, byte[] zipBytes) throws IOException {
		Files.createDirectories(file.getParent());
		Files.write(file, zipBytes);
	}

	private static int idFor(Map<String, Integer> ids, int[] next, String name) {
		if (name == null || name.isBlank()) {
			name = "minecraft:air";
		}
		return ids.computeIfAbsent(name, k -> next[0]++);
	}

	private static byte[] emptyData() {
		byte[] data = new byte[REGION_SIZE * REGION_SIZE * LAYERS];
		int value = Short.MIN_VALUE;
		byte b0 = (byte) (value >> 8);
		byte b1 = (byte) value;
		int layerSize = REGION_SIZE * REGION_SIZE;
		// Surface + ocean + transparent + foliage height planes must be MIN_VALUE
		// (zeros look like Y=0 and break VoxelMap waterTransparency blending).
		int[] heightLayers = {HEIGHTPOS, OCEANFLOORHEIGHTPOS, TRANSPARENTHEIGHTPOS, FOLIAGEHEIGHTPOS};
		for (int layer : heightLayers) {
			for (int i = 0; i < layerSize; i++) {
				data[layer * layerSize + i] = b0;
				data[(layer + 1) * layerSize + i] = b1;
			}
		}
		return data;
	}

	private static void setByte(byte[] data, int x, int z, int layer, byte value) {
		data[index(x, z, layer)] = value;
	}

	private static void setShort(byte[] data, int x, int z, int layer, int value) {
		data[index(x, z, layer)] = (byte) (value >> 8);
		data[index(x, z, layer + 1)] = (byte) value;
	}

	private static int index(int x, int z, int layer) {
		return x + z * REGION_SIZE + REGION_SIZE * REGION_SIZE * layer;
	}

	private static String buildBlockKey(Map<String, Integer> ids) {
		StringBuilder sb = new StringBuilder();
		ids.entrySet().stream()
				.sorted(Map.Entry.comparingByValue())
				.forEach(e -> sb.append(e.getValue()).append(' ').append(e.getKey()).append("\r\n"));
		return sb.toString();
	}

	private static String buildBiomeKey(Map<String, Integer> ids) {
		StringBuilder sb = new StringBuilder();
		ids.entrySet().stream()
				.sorted(Map.Entry.comparingByValue())
				.forEach(e -> sb.append(e.getValue()).append(' ').append(e.getKey()).append("\r\n"));
		return sb.toString();
	}

	private static void put(ZipOutputStream zos, String name, byte[] bytes) throws IOException {
		ZipEntry entry = new ZipEntry(name);
		entry.setSize(bytes.length);
		zos.putNextEntry(entry);
		zos.write(bytes);
		zos.closeEntry();
	}
}
