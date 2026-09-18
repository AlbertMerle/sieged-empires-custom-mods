package com.voxmapsync.server.webmap;

import com.voxmapsync.server.mca.McaReader;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Renders 256x256 pixel PNG map tiles from MCA chunk surfaces or VoxelMap cache zips.
 * Accurately reproduces VoxelMap in-game topography, hillshading, water depth, and biomes.
 */
public final class WebTileRenderer {
	public static final int TILE_SIZE = 256;
	private static final int LAYERS = 22;

	private WebTileRenderer() {
	}

	public static BufferedImage renderTile(int regionX, int regionZ, List<McaReader.ChunkSurface> chunks) {
		int[] heights = new int[TILE_SIZE * TILE_SIZE];
		Arrays.fill(heights, Short.MIN_VALUE);
		String[] blocks = new String[TILE_SIZE * TILE_SIZE];
		String[] biomes = new String[TILE_SIZE * TILE_SIZE];
		int[] oceanHeights = new int[TILE_SIZE * TILE_SIZE];
		Arrays.fill(oceanHeights, Short.MIN_VALUE);
		String[] oceanBlocks = new String[TILE_SIZE * TILE_SIZE];
		int[] transparentHeights = new int[TILE_SIZE * TILE_SIZE];
		Arrays.fill(transparentHeights, Short.MIN_VALUE);
		String[] transparentBlocks = new String[TILE_SIZE * TILE_SIZE];
		int[] foliageHeights = new int[TILE_SIZE * TILE_SIZE];
		Arrays.fill(foliageHeights, Short.MIN_VALUE);
		String[] foliageBlocks = new String[TILE_SIZE * TILE_SIZE];

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
					int cIdx = lz * 16 + lx;
					int px = localChunkX * 16 + lx;
					int pz = localChunkZ * 16 + lz;
					int tIdx = pz * TILE_SIZE + px;

					heights[tIdx] = chunk.heights()[cIdx];
					blocks[tIdx] = chunk.blocks()[cIdx];
					biomes[tIdx] = chunk.biomes()[cIdx];
					oceanHeights[tIdx] = chunk.oceanHeights()[cIdx];
					oceanBlocks[tIdx] = chunk.oceanBlocks()[cIdx];
					transparentHeights[tIdx] = chunk.transparentHeights()[cIdx];
					transparentBlocks[tIdx] = chunk.transparentBlocks()[cIdx];
					foliageHeights[tIdx] = chunk.foliageHeights()[cIdx];
					foliageBlocks[tIdx] = chunk.foliageBlocks()[cIdx];
				}
			}
		}

		return renderFromArrays(heights, blocks, biomes, oceanHeights, oceanBlocks,
				transparentHeights, transparentBlocks, foliageHeights, foliageBlocks);
	}

	public static BufferedImage renderTileFromZip(byte[] zipBytes) {
		if (zipBytes == null || zipBytes.length == 0) {
			return null;
		}
		try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
			return renderTileFromZipStream(zis);
		} catch (Exception e) {
			return null;
		}
	}

	public static BufferedImage renderTileFromZipFile(Path zipPath) {
		if (zipPath == null || !Files.exists(zipPath)) {
			return null;
		}
		try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
			return renderTileFromZipStream(zis);
		} catch (Exception e) {
			return null;
		}
	}

	private static BufferedImage renderTileFromZipStream(ZipInputStream zis) throws IOException {
		byte[] data = null;
		Map<Integer, String> blockKey = new HashMap<>();
		Map<Integer, String> biomeKey = new HashMap<>();

		ZipEntry entry;
		while ((entry = zis.getNextEntry()) != null) {
			String name = entry.getName();
			if ("data".equals(name)) {
				data = zis.readAllBytes();
			} else if ("key".equals(name)) {
				String text = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
				parseKeyMapping(text, blockKey);
			} else if ("biomes".equals(name)) {
				String text = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
				parseKeyMapping(text, biomeKey);
			}
			zis.closeEntry();
		}

		if (data == null || data.length < TILE_SIZE * TILE_SIZE * LAYERS) {
			return null;
		}

		int[] heights = new int[TILE_SIZE * TILE_SIZE];
		String[] blocks = new String[TILE_SIZE * TILE_SIZE];
		String[] biomes = new String[TILE_SIZE * TILE_SIZE];
		int[] oceanHeights = new int[TILE_SIZE * TILE_SIZE];
		String[] oceanBlocks = new String[TILE_SIZE * TILE_SIZE];
		int[] transparentHeights = new int[TILE_SIZE * TILE_SIZE];
		String[] transparentBlocks = new String[TILE_SIZE * TILE_SIZE];
		int[] foliageHeights = new int[TILE_SIZE * TILE_SIZE];
		String[] foliageBlocks = new String[TILE_SIZE * TILE_SIZE];

		for (int pz = 0; pz < TILE_SIZE; pz++) {
			for (int px = 0; px < TILE_SIZE; px++) {
				int tIdx = pz * TILE_SIZE + px;

				int h = getShort(data, px, pz, 0); // HEIGHTPOS = 0
				heights[tIdx] = h;
				if (h != Short.MIN_VALUE) {
					int bId = getShort(data, px, pz, 2); // BLOCKSTATEPOS = 2
					blocks[tIdx] = blockKey.getOrDefault(bId, "minecraft:air");
					int bioId = getShort(data, px, pz, 20); // BIOMEIDPOS = 20
					biomes[tIdx] = biomeKey.getOrDefault(bioId, "minecraft:plains");
				}

				int oceanH = getShort(data, px, pz, 5); // OCEANFLOORHEIGHTPOS = 5
				oceanHeights[tIdx] = oceanH;
				if (oceanH != Short.MIN_VALUE) {
					int obId = getShort(data, px, pz, 7);
					oceanBlocks[tIdx] = blockKey.getOrDefault(obId, "minecraft:dirt");
				}

				int transH = getShort(data, px, pz, 10); // TRANSPARENTHEIGHTPOS = 10
				transparentHeights[tIdx] = transH;
				if (transH != Short.MIN_VALUE) {
					int tbId = getShort(data, px, pz, 12);
					transparentBlocks[tIdx] = blockKey.getOrDefault(tbId, "minecraft:air");
				}

				int folH = getShort(data, px, pz, 15); // FOLIAGEHEIGHTPOS = 15
				foliageHeights[tIdx] = folH;
				if (folH != Short.MIN_VALUE) {
					int fbId = getShort(data, px, pz, 17);
					foliageBlocks[tIdx] = blockKey.getOrDefault(fbId, "minecraft:air");
				}
			}
		}

		return renderFromArrays(heights, blocks, biomes, oceanHeights, oceanBlocks,
				transparentHeights, transparentBlocks, foliageHeights, foliageBlocks);
	}

	private static short getShort(byte[] data, int x, int z, int layer) {
		int layerSize = TILE_SIZE * TILE_SIZE;
		int idx0 = layer * layerSize + z * TILE_SIZE + x;
		int idx1 = (layer + 1) * layerSize + z * TILE_SIZE + x;
		int hi = data[idx0] & 0xFF;
		int lo = data[idx1] & 0xFF;
		return (short) ((hi << 8) | lo);
	}

	private static void parseKeyMapping(String text, Map<Integer, String> map) {
		if (text == null || text.isBlank()) return;
		String[] lines = text.split("\\r?\\n");
		for (String line : lines) {
			line = line.trim();
			if (line.isEmpty()) continue;
			int space = line.indexOf(' ');
			if (space > 0) {
				try {
					int id = Integer.parseInt(line.substring(0, space));
					String name = line.substring(space + 1).trim();
					map.put(id, name);
				} catch (NumberFormatException ignored) {
				}
			}
		}
	}

	public static BufferedImage renderFromArrays(
			int[] heights, String[] blocks, String[] biomes,
			int[] oceanHeights, String[] oceanBlocks,
			int[] transparentHeights, String[] transparentBlocks,
			int[] foliageHeights, String[] foliageBlocks) {

		BufferedImage image = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);

		for (int pz = 0; pz < TILE_SIZE; pz++) {
			for (int px = 0; px < TILE_SIZE; px++) {
				int idx = pz * TILE_SIZE + px;
				int height = heights[idx];
				if (height == Short.MIN_VALUE) {
					// transparent void
					image.setRGB(px, pz, 0x00000000);
					continue;
				}

				String block = blocks[idx];
				String biome = biomes[idx];

				// North-West slope comparison
				int northY = (pz > 0) ? heights[(pz - 1) * TILE_SIZE + px] : height;
				int westY = (px > 0) ? heights[pz * TILE_SIZE + (px - 1)] : height;
				if (northY == Short.MIN_VALUE) northY = height;
				if (westY == Short.MIN_VALUE) westY = height;
				int slopeDiff = (height - northY) + (height - westY);

				int finalColor;
				String transBlock = transparentBlocks[idx];
				int transH = transparentHeights[idx];
				int oceanH = oceanHeights[idx];
				String oceanBlock = oceanBlocks[idx];

				boolean isWaterCol = WebColorPalette.isWater(block) || (transBlock != null && WebColorPalette.isWater(transBlock));
				boolean isIceCol = WebColorPalette.isIce(block) || (transBlock != null && WebColorPalette.isIce(transBlock));

				if (isWaterCol) {
					// Render ocean floor with depth shading and biome water tint
					String floorBlock = oceanBlock != null && !WebColorPalette.isWater(oceanBlock) ? oceanBlock : "minecraft:dirt";
					int oceanColor = WebColorPalette.getBlockColor(floorBlock);
					if (WebColorPalette.isGrassTinted(floorBlock)) {
						oceanColor = WebColorPalette.getBiomeGrassColor(biome);
					}
					int floorHeight = oceanH != Short.MIN_VALUE ? oceanH : height;
					int shadedOcean = WebColorPalette.shadeSlope(oceanColor, slopeDiff / 2, floorHeight);

					int waterColor = WebColorPalette.getBiomeWaterColor(biome);
					int waterTopY = (transH != Short.MIN_VALUE && WebColorPalette.isWater(transBlock)) ? transH : height;
					int waterDepth = (oceanH != Short.MIN_VALUE && waterTopY > oceanH) ? (waterTopY - oceanH) : 3;
					float waterAlpha = Math.clamp(0.42f + waterDepth * 0.04f, 0.42f, 0.88f);
					finalColor = WebColorPalette.blendAlpha(shadedOcean, waterColor, waterAlpha);
				} else {
					int baseColor = WebColorPalette.getBlockColor(block);
					if (WebColorPalette.isGrassTinted(block)) {
						baseColor = WebColorPalette.getBiomeGrassColor(biome);
					} else if (WebColorPalette.isFoliageTinted(block)) {
						baseColor = WebColorPalette.getBiomeFoliageColor(biome);
					}
					finalColor = WebColorPalette.shadeSlope(baseColor, slopeDiff, height);

					// Ice or stained glass over solid
					if (isIceCol) {
						int iceColor = WebColorPalette.getBlockColor("minecraft:ice");
						finalColor = WebColorPalette.blendAlpha(finalColor, iceColor, 0.65f);
					} else if (transH != Short.MIN_VALUE && transBlock != null) {
						int transColor = WebColorPalette.getBlockColor(transBlock);
						finalColor = WebColorPalette.blendAlpha(finalColor, transColor, 0.65f);
					}
				}

				// Foliage overlay (leaves, flowers, crops)
				int folH = foliageHeights[idx];
				String folBlock = foliageBlocks[idx];
				if (folH != Short.MIN_VALUE && folBlock != null && !WebColorPalette.isWater(folBlock)) {
					int folColor = WebColorPalette.getBlockColor(folBlock);
					if (WebColorPalette.isFoliageTinted(folBlock)) {
						folColor = WebColorPalette.getBiomeFoliageColor(biome);
					} else if (WebColorPalette.isGrassTinted(folBlock)) {
						folColor = WebColorPalette.getBiomeGrassColor(biome);
					}
					finalColor = WebColorPalette.blendAlpha(finalColor, folColor, 0.85f);
				}

				image.setRGB(px, pz, 0xFF000000 | finalColor);
			}
		}

		return image;
	}
}
