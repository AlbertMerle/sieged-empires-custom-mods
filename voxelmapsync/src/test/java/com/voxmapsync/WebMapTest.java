package com.voxmapsync;

import com.voxmapsync.server.mca.McaReader;
import com.voxmapsync.server.webmap.WebColorPalette;
import com.voxmapsync.server.webmap.WebPyramidDownscaler;
import com.voxmapsync.server.webmap.WebTileRenderer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WebMapTest {

	@Test
	public void testColorPalette() {
		// Verify standard block colors
		int grassColor = WebColorPalette.getBlockColor("minecraft:grass_block");
		Assertions.assertTrue(grassColor != 0, "Grass color should not be 0");

		int stoneColor = WebColorPalette.getBlockColor("minecraft:stone");
		Assertions.assertEquals(0x7E7E7E, stoneColor);

		int waterColor = WebColorPalette.getBlockColor("minecraft:water");
		Assertions.assertEquals(WebColorPalette.DEFAULT_WATER_COLOR, waterColor);

		// Verify biome colors
		int plainsGrass = WebColorPalette.getBiomeGrassColor("minecraft:plains");
		Assertions.assertEquals(0x91BD59, plainsGrass);

		int terralithSakura = WebColorPalette.getBiomeGrassColor("terralith:sakura_grove");
		Assertions.assertEquals(0xB6DB67, terralithSakura);

		int terralithJungle = WebColorPalette.getBiomeGrassColor("terralith:tropical_jungle");
		Assertions.assertEquals(0x4FD830, terralithJungle);

		// Verify slope shading
		int flatShaded = WebColorPalette.shadeSlope(0x808080, 0, 64);
		int uphillShaded = WebColorPalette.shadeSlope(0x808080, 2, 64);
		int downhillShaded = WebColorPalette.shadeSlope(0x808080, -2, 64);

		Assertions.assertTrue(((uphillShaded >> 16) & 0xFF) > ((flatShaded >> 16) & 0xFF), "Uphill should be brighter");
		Assertions.assertTrue(((downhillShaded >> 16) & 0xFF) < ((flatShaded >> 16) & 0xFF), "Downhill should be darker");

		// Verify alpha blending
		int blended = WebColorPalette.blendAlpha(0xFF0000, 0x0000FF, 0.5f);
		int r = (blended >> 16) & 0xFF;
		int b = blended & 0xFF;
		Assertions.assertTrue(r > 120 && r < 135, "Blended R should be ~128");
		Assertions.assertTrue(b > 120 && b < 135, "Blended B should be ~128");
	}

	@Test
	public void testTileRendererAndPyramid(@TempDir Path tempDir) throws Exception {
		// Construct synthetic chunk surfaces for a 16x16 chunk region (region 0, 0)
		List<McaReader.ChunkSurface> chunks = new ArrayList<>();
		for (int cz = 0; cz < 16; cz++) {
			for (int cx = 0; cx < 16; cx++) {
				int[] heights = new int[256];
				String[] blocks = new String[256];
				String[] biomes = new String[256];
				int[] oceanHeights = new int[256];
				Arrays.fill(oceanHeights, Short.MIN_VALUE);
				String[] oceanBlocks = new String[256];
				Arrays.fill(oceanBlocks, "minecraft:air");
				int[] transHeights = new int[256];
				Arrays.fill(transHeights, Short.MIN_VALUE);
				String[] transBlocks = new String[256];
				Arrays.fill(transBlocks, "minecraft:air");
				int[] folHeights = new int[256];
				Arrays.fill(folHeights, Short.MIN_VALUE);
				String[] folBlocks = new String[256];
				Arrays.fill(folBlocks, "minecraft:air");

				for (int i = 0; i < 256; i++) {
					int lx = i % 16;
					int lz = i / 16;
					int worldX = cx * 16 + lx;
					int worldZ = cz * 16 + lz;

					biomes[i] = "minecraft:plains";
					if (worldX < 128) {
						// Land
						heights[i] = 64 + (worldX % 8);
						blocks[i] = "minecraft:grass_block";
					} else {
						// Ocean
						heights[i] = 63;
						blocks[i] = "minecraft:water";
						oceanHeights[i] = 52;
						oceanBlocks[i] = "minecraft:sand";
					}
				}

				chunks.add(new McaReader.ChunkSurface(
						cx, cz, heights, blocks, biomes,
						oceanHeights, oceanBlocks, transHeights, transBlocks,
						folHeights, folBlocks
				));
			}
		}

		// Render zoom 0 tile
		BufferedImage tile = WebTileRenderer.renderTile(0, 0, chunks);
		Assertions.assertNotNull(tile);
		Assertions.assertEquals(256, tile.getWidth());
		Assertions.assertEquals(256, tile.getHeight());

		// Verify land pixel has non-zero alpha and green tint
		int landRgb = tile.getRGB(50, 50);
		Assertions.assertEquals(0xFF, (landRgb >> 24) & 0xFF, "Land pixel alpha should be 255");
		int landG = (landRgb >> 8) & 0xFF;
		int landR = (landRgb >> 16) & 0xFF;
		int landB = landRgb & 0xFF;
		Assertions.assertTrue(landG > landR && landG > landB, "Plains grass should be green dominant");

		// Verify water pixel has non-zero alpha and blue tint
		int waterRgb = tile.getRGB(200, 200);
		Assertions.assertEquals(0xFF, (waterRgb >> 24) & 0xFF, "Water pixel alpha should be 255");
		int waterB = waterRgb & 0xFF;
		int waterR = (waterRgb >> 16) & 0xFF;
		Assertions.assertTrue(waterB > waterR, "Ocean pixel should be blue dominant");

		// Test pyramid downscaling up to zoom -5
		Path tilesDir = tempDir.resolve("tiles").resolve("overworld");
		Path zoom0Dir = tilesDir.resolve("0");
		Files.createDirectories(zoom0Dir);
		Path tile0File = zoom0Dir.resolve("0_0.png");
		ImageIO.write(tile, "PNG", tile0File.toFile());

		WebPyramidDownscaler.updatePyramid(tilesDir, 0, 0, tile, 5);

		// Check that zoom -1 to -5 files were created
		for (int z = -1; z >= -5; z--) {
			Path pyramidTile = tilesDir.resolve(Integer.toString(z)).resolve("0_0.png");
			Assertions.assertTrue(Files.exists(pyramidTile), "Pyramid zoom " + z + " tile should exist: " + pyramidTile);
			BufferedImage img = ImageIO.read(pyramidTile.toFile());
			Assertions.assertEquals(256, img.getWidth());
			Assertions.assertEquals(256, img.getHeight());
		}
	}

	@Test
	public void testStatusAndZipWriter() throws Exception {
		Assertions.assertTrue(McaReader.isFullStatus("minecraft:full"));
		Assertions.assertTrue(McaReader.isFullStatus("full"));
		Assertions.assertFalse(McaReader.isFullStatus("minecraft:features"));
		Assertions.assertFalse(McaReader.isFullStatus("minecraft:structure_starts"));
		Assertions.assertFalse(McaReader.isFullStatus(""));
		Assertions.assertFalse(McaReader.isFullStatus(null));

		// Test VoxelMap zip writing
		List<McaReader.ChunkSurface> chunks = new ArrayList<>();
		int[] heights = new int[256];
		Arrays.fill(heights, 64);
		String[] blocks = new String[256];
		Arrays.fill(blocks, "minecraft:grass_block");
		String[] biomes = new String[256];
		Arrays.fill(biomes, "minecraft:plains");
		int[] emptyH = new int[256];
		Arrays.fill(emptyH, Short.MIN_VALUE);
		String[] emptyB = new String[256];
		Arrays.fill(emptyB, "minecraft:air");

		chunks.add(new McaReader.ChunkSurface(0, 0, heights, blocks, biomes, emptyH, emptyB, emptyH, emptyB, emptyH, emptyB));

		byte[] zip = com.voxmapsync.server.VoxelMapZipWriter.writeRegion(0, 0, chunks);
		Assertions.assertNotNull(zip);
		Assertions.assertTrue(zip.length > 0, "Zip output should not be empty");

		// Test rendering from zip directly
		BufferedImage zipTile = WebTileRenderer.renderTileFromZip(zip);
		Assertions.assertNotNull(zipTile);
		Assertions.assertEquals(256, zipTile.getWidth());
		Assertions.assertEquals(256, zipTile.getHeight());
		int sampleRgb = zipTile.getRGB(5, 5);
		Assertions.assertEquals(0xFF, (sampleRgb >> 24) & 0xFF, "Alpha should be 255");

		// Test invalid zip inputs
		Assertions.assertNull(WebTileRenderer.renderTileFromZip(null));
		Assertions.assertNull(WebTileRenderer.renderTileFromZip(new byte[0]));
		Assertions.assertNull(WebTileRenderer.renderTileFromZip(new byte[]{1, 2, 3, 4}));

		// Test sentinel fraction helper
		Assertions.assertEquals(0.0f, McaReader.sentinelFraction(chunks), 0.001f);
		Assertions.assertEquals(1.0f, McaReader.sentinelFraction(null), 0.001f);
		Assertions.assertEquals(1.0f, McaReader.sentinelFraction(List.of()), 0.001f);
	}

	@Test
	public void testRegionQualityGates() throws Exception {
		int savedMinChunks = com.voxmapsync.config.SyncConfig.minChunksPerRegion;
		try {
			com.voxmapsync.config.SyncConfig.minChunksPerRegion = 256;
			com.voxmapsync.config.SyncConfig.rejectFlatWaterGarbage = true;

			List<McaReader.ChunkSurface> fullRegion = new ArrayList<>();
			for (int cz = 0; cz < 16; cz++) {
				for (int cx = 0; cx < 16; cx++) {
					fullRegion.addAll(syntheticChunk(cx, cz, false));
				}
			}
			byte[] goodZip = com.voxmapsync.server.VoxelMapZipWriter.writeRegion(0, 0, fullRegion);
			Assertions.assertTrue(com.voxmapsync.server.RegionQuality.assessZip(goodZip).ok());

			List<McaReader.ChunkSurface> flatRegion = new ArrayList<>();
			for (int cz = 0; cz < 16; cz++) {
				for (int cx = 0; cx < 16; cx++) {
					flatRegion.addAll(syntheticChunk(cx, cz, true));
				}
			}
			byte[] flatZip = com.voxmapsync.server.VoxelMapZipWriter.writeRegion(0, 0, flatRegion);
			// Complete 256-chunk tiles are accepted (real oceans look like flat water).
			Assertions.assertTrue(com.voxmapsync.server.RegionQuality.assessZip(flatZip).ok(),
					"complete flat-water region should be accepted as valid terrain");

			// Partial flat-water junk is still rejected.
			List<McaReader.ChunkSurface> partialFlat = new ArrayList<>();
			for (int cz = 0; cz < 4; cz++) {
				for (int cx = 0; cx < 4; cx++) {
					partialFlat.addAll(syntheticChunk(cx, cz, true));
				}
			}
			var partialFlatAssessment = com.voxmapsync.server.RegionQuality.assessSurfaces(partialFlat);
			Assertions.assertFalse(partialFlatAssessment.ok(), () -> "expected reject, got " + partialFlatAssessment.reason());
			Assertions.assertEquals(
					com.voxmapsync.server.RegionQuality.RejectReason.FLAT_WATER_GARBAGE,
					partialFlatAssessment.reason());

			byte[] partialZip = com.voxmapsync.server.VoxelMapZipWriter.writeRegion(0, 0, syntheticChunk(0, 0, false));
			Assertions.assertFalse(com.voxmapsync.server.RegionQuality.assessZip(partialZip).ok());

			List<McaReader.ChunkSurface> oceanRegion = new ArrayList<>();
			for (int cz = 0; cz < 16; cz++) {
				for (int cx = 0; cx < 16; cx++) {
					oceanRegion.addAll(syntheticOceanChunk(cx, cz));
				}
			}
			Assertions.assertTrue(com.voxmapsync.server.RegionQuality.isLegitimateOceanSurfaces(oceanRegion));
			byte[] oceanZip = com.voxmapsync.server.VoxelMapZipWriter.writeRegion(0, 0, oceanRegion);
			var oceanAssessment = com.voxmapsync.server.RegionQuality.assessZip(oceanZip);
			Assertions.assertTrue(oceanAssessment.ok(), () -> "legitimate ocean rejected: " + oceanAssessment.reason());
		} finally {
			com.voxmapsync.config.SyncConfig.minChunksPerRegion = savedMinChunks;
		}
	}

	private static List<McaReader.ChunkSurface> syntheticOceanChunk(int cx, int cz) {
		int[] heights = new int[256];
		String[] blocks = new String[256];
		String[] biomes = new String[256];
		int[] oceanHeights = new int[256];
		String[] oceanBlocks = new String[256];
		int[] emptyH = new int[256];
		Arrays.fill(emptyH, Short.MIN_VALUE);
		String[] emptyB = new String[256];
		Arrays.fill(emptyB, "minecraft:air");
		for (int i = 0; i < 256; i++) {
			heights[i] = 63;
			blocks[i] = "minecraft:water";
			biomes[i] = "minecraft:ocean";
			oceanHeights[i] = 52;
			oceanBlocks[i] = "minecraft:sand";
		}
		return List.of(new McaReader.ChunkSurface(cx, cz, heights, blocks, biomes,
				oceanHeights, oceanBlocks, emptyH, emptyB, emptyH, emptyB));
	}

	private static List<McaReader.ChunkSurface> syntheticChunk(int cx, int cz, boolean flatWater) {
		int[] heights = new int[256];
		String[] blocks = new String[256];
		String[] biomes = new String[256];
		int[] emptyH = new int[256];
		Arrays.fill(emptyH, Short.MIN_VALUE);
		String[] emptyB = new String[256];
		Arrays.fill(emptyB, "minecraft:air");
		for (int i = 0; i < 256; i++) {
			if (flatWater) {
				heights[i] = 64;
				blocks[i] = "minecraft:water";
			} else {
				heights[i] = 70 + (i % 5);
				blocks[i] = "minecraft:grass_block";
			}
			biomes[i] = "minecraft:plains";
		}
		return List.of(new McaReader.ChunkSurface(cx, cz, heights, blocks, biomes,
				emptyH, emptyB, emptyH, emptyB, emptyH, emptyB));
	}

	@Test
	public void testFoliageAndTransparentLayers() {
		int[] heights = new int[256 * 256];
		Arrays.fill(heights, 64);
		String[] blocks = new String[256 * 256];
		Arrays.fill(blocks, "minecraft:stone");
		String[] biomes = new String[256 * 256];
		Arrays.fill(biomes, "minecraft:plains");
		int[] oceanH = new int[256 * 256];
		Arrays.fill(oceanH, Short.MIN_VALUE);
		String[] oceanB = new String[256 * 256];
		Arrays.fill(oceanB, "minecraft:air");
		int[] transH = new int[256 * 256];
		Arrays.fill(transH, Short.MIN_VALUE);
		String[] transB = new String[256 * 256];
		Arrays.fill(transB, "minecraft:air");
		int[] folH = new int[256 * 256];
		Arrays.fill(folH, Short.MIN_VALUE);
		String[] folB = new String[256 * 256];
		Arrays.fill(folB, "minecraft:air");

		// Add foliage to first pixel and ice to second
		folH[0] = 65;
		folB[0] = "minecraft:oak_leaves";

		transH[1] = 65;
		transB[1] = "minecraft:ice";

		BufferedImage img = WebTileRenderer.renderFromArrays(
				heights, blocks, biomes, oceanH, oceanB,
				transH, transB, folH, folB
		);
		Assertions.assertNotNull(img);

		int folPixel = img.getRGB(0, 0);
		int icePixel = img.getRGB(1, 0);
		int basePixel = img.getRGB(2, 0);

		// Stone is grey (R=G=B=126 approx). Oak leaves should have green tint.
		int folG = (folPixel >> 8) & 0xFF;
		int folR = (folPixel >> 16) & 0xFF;
		Assertions.assertTrue(folG > folR, "Oak leaves pixel should be green tinted");

		// Ice pixel should be blended with ice color
		Assertions.assertNotEquals(basePixel, icePixel, "Ice pixel should differ from base stone");
	}

	@Test
	public void testFullPyramidRebuilder(@TempDir Path tempDir) throws Exception {
		Path tilesDir = tempDir.resolve("tiles").resolve("overworld");
		Path zoom0Dir = tilesDir.resolve("0");
		Files.createDirectories(zoom0Dir);

		// Create four 256x256 tiles at (0,0), (0,1), (1,0), (1,1)
		for (int x = 0; x <= 1; x++) {
			for (int z = 0; z <= 1; z++) {
				BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
				img.setRGB(10, 10, 0xFF00FF00);
				ImageIO.write(img, "PNG", zoom0Dir.resolve(x + "_" + z + ".png").toFile());
			}
		}

		WebPyramidDownscaler.rebuildFullPyramid(tilesDir, 3);

		// Zoom -1 should have one tile at 0_0.png combining all 4
		Path z1File = tilesDir.resolve("-1").resolve("0_0.png");
		Assertions.assertTrue(Files.exists(z1File), "Zoom -1 parent tile 0_0.png should exist");
		BufferedImage z1Img = ImageIO.read(z1File.toFile());
		Assertions.assertEquals(256, z1Img.getWidth());
		Assertions.assertEquals(256, z1Img.getHeight());

		// Zoom -2 and -3 should also exist
		Path z2File = tilesDir.resolve("-2").resolve("0_0.png");
		Assertions.assertTrue(Files.exists(z2File), "Zoom -2 parent tile 0_0.png should exist");
		Path z3File = tilesDir.resolve("-3").resolve("0_0.png");
		Assertions.assertTrue(Files.exists(z3File), "Zoom -3 parent tile 0_0.png should exist");
	}
}
