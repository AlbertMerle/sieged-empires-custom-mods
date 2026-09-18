package com.voxmapsync.server.mca;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Lightweight MCA reader using vanilla NBT (MC 26.2 Optional-based API).
 * Extracts VoxelMap-style surface / water / foliage layers per column.
 */
public final class McaReader implements AutoCloseable {
	private static final int SECTOR = 4096;
	/** Finished terrain only — mid-Chunky statuses produce broken map tiles. */
	private static final Set<String> FULL_STATUS = Set.of("minecraft:full", "full");
	/** Legacy permissive set — only used when {@code strictFullStatusOnly=false}. */
	private static final Set<String> LEGACY_OK_STATUS = Set.of(
			"minecraft:features", "minecraft:light", "minecraft:spawn", "minecraft:heightmaps", "minecraft:full",
			"features", "light", "spawn", "heightmaps", "full"
	);

	private final RandomAccessFile raf;
	private final int worldMinY;
	private final int worldHeight;
	private final boolean strictFullOnly;

	private McaReader(RandomAccessFile raf, int worldMinY, int worldHeight, boolean strictFullOnly) {
		this.raf = raf;
		this.worldMinY = worldMinY;
		this.worldHeight = Math.max(worldHeight, 1);
		this.strictFullOnly = strictFullOnly;
	}

	public static McaReader open(Path path, int worldMinY, int worldHeight) throws IOException {
		return open(path, worldMinY, worldHeight, true);
	}

	public static McaReader open(Path path, int worldMinY, int worldHeight, boolean strictFullOnly) throws IOException {
		RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r");
		if (raf.length() < SECTOR * 2L) {
			raf.close();
			throw new IOException("MCA too small: " + path);
		}
		return new McaReader(raf, worldMinY, worldHeight, strictFullOnly);
	}

	public List<ChunkSurface> readSurfaces(int regionX, int regionZ) throws IOException {
		return readSurfacesDetailed(regionX, regionZ).surfaces();
	}

	/**
	 * Reads surfaces and counts present / full chunks in the MCA (of 1024 slots).
	 */
	public ReadResult readSurfacesDetailed(int regionX, int regionZ) throws IOException {
		List<ChunkSurface> out = new ArrayList<>();
		int present = 0;
		int full = 0;
		for (int lx = 0; lx < 32; lx++) {
			for (int lz = 0; lz < 32; lz++) {
				CompoundTag root = readChunk(lx, lz);
				if (root == null) {
					continue;
				}
				present++;
				CompoundTag level = root.getCompound("Level").orElse(root);
				String status = level.getStringOr("Status", "");
				if (isFullStatus(status)) {
					full++;
				}
				ChunkSurface surface = parseSurface(root, regionX * 32 + lx, regionZ * 32 + lz);
				if (surface != null) {
					out.add(surface);
				}
			}
		}
		return new ReadResult(present, full, out);
	}

	public static boolean isFullStatus(String status) {
		if (status == null || status.isEmpty()) {
			return false;
		}
		String normalized = status.contains(":") ? status : "minecraft:" + status;
		return FULL_STATUS.contains(normalized) || FULL_STATUS.contains(status);
	}

	/** Fraction of height samples that are sentinel-empty across all surfaces. */
	public static float sentinelFraction(List<ChunkSurface> surfaces) {
		if (surfaces == null || surfaces.isEmpty()) {
			return 1f;
		}
		long sentinel = 0;
		long total = 0;
		for (ChunkSurface surface : surfaces) {
			for (int h : surface.heights()) {
				total++;
				if (h == Short.MIN_VALUE) {
					sentinel++;
				}
			}
		}
		return total == 0 ? 1f : (float) sentinel / (float) total;
	}

	public record ReadResult(int presentChunks, int fullChunks, List<ChunkSurface> surfaces) {
	}

	private CompoundTag readChunk(int localX, int localZ) throws IOException {
		int index = (localX + localZ * 32) * 4;
		raf.seek(index);
		int b0 = raf.readUnsignedByte();
		int b1 = raf.readUnsignedByte();
		int b2 = raf.readUnsignedByte();
		int offset = (b0 << 16) | (b1 << 8) | b2;
		int sectors = raf.readUnsignedByte();
		if (offset <= 0 || sectors <= 0) {
			return null;
		}
		long dataOffset = (long) offset * SECTOR;
		if (dataOffset + 5 > raf.length()) {
			return null;
		}
		raf.seek(dataOffset);
		int totalLength = raf.readInt();
		if (totalLength <= 1) {
			return null;
		}
		int compression = raf.readUnsignedByte();
		byte[] compressed = new byte[totalLength - 1];
		raf.readFully(compressed);
		byte[] nbtBytes = decompress(compressed, compression);
		if (nbtBytes == null) {
			return null;
		}
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(nbtBytes))) {
			return NbtIo.read(in, NbtAccounter.unlimitedHeap());
		} catch (Exception e) {
			return null;
		}
	}

	private static byte[] decompress(byte[] data, int type) throws IOException {
		return switch (type) {
			case 1 -> readAll(new GZIPInputStream(new ByteArrayInputStream(data)));
			case 2 -> readAll(new InflaterInputStream(new ByteArrayInputStream(data)));
			case 3 -> data;
			default -> null;
		};
	}

	private static byte[] readAll(java.io.InputStream in) throws IOException {
		try (in) {
			return in.readAllBytes();
		}
	}

	private ChunkSurface parseSurface(CompoundTag root, int chunkX, int chunkZ) {
		CompoundTag level = root.getCompound("Level").orElse(root);
		String status = level.getStringOr("Status", "");
		if (!status.isEmpty()) {
			String normalized = status.contains(":") ? status : "minecraft:" + status;
			Set<String> ok = strictFullOnly ? FULL_STATUS : LEGACY_OK_STATUS;
			if (!ok.contains(normalized) && !ok.contains(status)) {
				return null;
			}
		} else if (strictFullOnly) {
			return null;
		}

		int[] heights = emptyHeights();
		String[] blocks = emptyNames("minecraft:air");
		String[] biomes = emptyNames("minecraft:plains");
		int[] oceanHeights = emptyHeights();
		String[] oceanBlocks = emptyNames("minecraft:air");
		int[] transparentHeights = emptyHeights();
		String[] transparentBlocks = emptyNames("minecraft:air");
		int[] foliageHeights = emptyHeights();
		String[] foliageBlocks = emptyNames("minecraft:air");

		CompoundTag heightmaps = level.getCompoundOrEmpty("Heightmaps");
		// Match live VoxelMap: MOTION_BLOCKING, then walk down through non-opaque plants/snow.
		long[] motion = heightmaps.getLongArray("MOTION_BLOCKING").orElse(null);
		if (motion == null) {
			motion = heightmaps.getLongArray("WORLD_SURFACE").orElse(null);
		}
		if (motion == null) {
			motion = heightmaps.getLongArray("MOTION_BLOCKING_NO_LEAVES").orElse(null);
		}

		ListTag sections = level.getListOrEmpty("sections");
		if (sections.isEmpty()) {
			sections = level.getListOrEmpty("Sections");
		}
		if (sections.isEmpty() || motion == null || motion.length == 0) {
			return null;
		}

		unpackHeightmap(motion, heights);

		int populated = 0;
		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				int idx = lz * 16 + lx;
				int heightmapTop = heights[idx];
				if (heightmapTop == Short.MIN_VALUE) {
					continue;
				}
				ColumnSample sample = sampleColumn(sections, lx, lz, heightmapTop);
				if (sample == null) {
					heights[idx] = Short.MIN_VALUE;
					continue;
				}
				heights[idx] = sample.surfaceHeight;
				blocks[idx] = sample.surfaceBlock;
				biomes[idx] = sample.biome;
				oceanHeights[idx] = sample.oceanHeight;
				oceanBlocks[idx] = sample.oceanBlock;
				transparentHeights[idx] = sample.transparentHeight;
				transparentBlocks[idx] = sample.transparentBlock;
				foliageHeights[idx] = sample.foliageHeight;
				foliageBlocks[idx] = sample.foliageBlock;
				populated++;
			}
		}
		if (populated == 0) {
			return null;
		}

		return new ChunkSurface(chunkX, chunkZ, heights, blocks, biomes,
				oceanHeights, oceanBlocks, transparentHeights, transparentBlocks,
				foliageHeights, foliageBlocks);
	}

	/**
	 * Mirrors VoxelMap {@code PersistentMap.getAndStoreData} overworld path:
	 * start one above MOTION_BLOCKING heightmap, walk down until an opaque block,
	 * promote snow, then optionally scan seafloor for water/ice only.
	 */
	private ColumnSample sampleColumn(ListTag sections, int x, int z, int heightmapTop) {
		// VoxelMap: transparentHeight = chunk.getHeight(MOTION_BLOCKING) + 1
		int transparentHeight = heightmapTop + 1;
		String transparentBlock = findBlock(sections, x, transparentHeight - 1, z);
		if (transparentBlock == null) {
			return null;
		}
		transparentBlock = fluidLegacy(transparentBlock);

		int surfaceHeight = transparentHeight;
		String surfaceBlock = transparentBlock;
		String foliageBlock = "minecraft:air";

		while (!hasMapOpacity(surfaceBlock) && surfaceHeight > worldMinY) {
			foliageBlock = surfaceBlock;
			surfaceHeight--;
			String next = findBlock(sections, x, surfaceHeight - 1, z);
			if (next == null) {
				return null;
			}
			surfaceBlock = fluidLegacy(next);
		}

		if (surfaceHeight == transparentHeight) {
			transparentHeight = Short.MIN_VALUE;
			transparentBlock = "minecraft:air";
			String above = findBlock(sections, x, surfaceHeight, z);
			foliageBlock = above == null ? "minecraft:air" : above;
		}

		if (isSnow(foliageBlock)) {
			surfaceBlock = foliageBlock;
			foliageBlock = "minecraft:air";
		}
		if (foliageBlock.equals(transparentBlock)) {
			foliageBlock = "minecraft:air";
		}

		int foliageHeight = Short.MIN_VALUE;
		if (!isAir(foliageBlock)) {
			foliageHeight = surfaceHeight + 1;
		} else {
			foliageBlock = "minecraft:air";
		}

		int oceanHeight = Short.MIN_VALUE;
		String oceanBlock = "minecraft:air";

		// Live VoxelMap only treats water + regular ice as transparent sea (not packed/blue ice).
		if (isWater(surfaceBlock) || isMapIce(surfaceBlock)) {
			oceanHeight = surfaceHeight;
			oceanBlock = findBlock(sections, x, surfaceHeight - 1, z);
			if (oceanBlock == null) {
				oceanBlock = "minecraft:air";
			}
			while (oceanHeight > worldMinY + 1
					&& lightDampeningApprox(oceanBlock) < 5
					&& !isLeaves(oceanBlock)) {
				String material = oceanBlock;
				if (transparentHeight == Short.MIN_VALUE
						&& !isWater(material) && !isMapIce(material)
						&& isMotionBlockingName(material)) {
					transparentHeight = oceanHeight;
					transparentBlock = material;
				}
				if (foliageHeight == Short.MIN_VALUE
						&& oceanHeight != transparentHeight
						&& !transparentBlock.equals(material)
						&& !isWater(material) && !isMapIce(material)
						&& !isAir(material)
						&& !material.contains("bubble_column")) {
					foliageHeight = oceanHeight;
					foliageBlock = material;
				}
				oceanHeight--;
				String next = findBlock(sections, x, oceanHeight - 1, z);
				if (next == null) {
					break;
				}
				oceanBlock = next;
			}
			if (isWater(oceanBlock)) {
				oceanBlock = "minecraft:air";
			}
			if (oceanHeight <= worldMinY) {
				oceanHeight = Short.MIN_VALUE;
				oceanBlock = "minecraft:air";
			}
		}

		if (transparentHeight != Short.MIN_VALUE && isAir(transparentBlock)) {
			transparentHeight = Short.MIN_VALUE;
		}

		String biome = findBiome(sections, x, Math.max(surfaceHeight - 1, worldMinY), z);
		if (biome == null) {
			biome = "minecraft:plains";
		}

		return new ColumnSample(surfaceHeight, surfaceBlock, biome,
				oceanHeight, oceanBlock, transparentHeight, transparentBlock,
				foliageHeight, foliageBlock);
	}

	private void unpackHeightmap(long[] packed, int[] out) {
		int bits = bitsPerHeight(packed.length);
		int valuesPerLong = 64 / bits;
		long mask = (1L << bits) - 1L;
		for (int i = 0; i < 256; i++) {
			int longIndex = i / valuesPerLong;
			if (longIndex >= packed.length) {
				out[i] = Short.MIN_VALUE;
				continue;
			}
			int bitIndex = (i % valuesPerLong) * bits;
			int raw = (int) ((packed[longIndex] >>> bitIndex) & mask);
			out[i] = worldMinY + raw;
		}
	}

	private int bitsPerHeight(int longArrayLength) {
		int fromWorld = 32 - Integer.numberOfLeadingZeros(worldHeight);
		if (fromWorld >= 1 && fromWorld <= 16) {
			return fromWorld;
		}
		if (longArrayLength <= 0) {
			return 9;
		}
		int valuesPerLong = Math.max(1, (256 + longArrayLength - 1) / longArrayLength);
		return Math.max(1, 64 / valuesPerLong);
	}

	private static String findBlock(ListTag sections, int x, int y, int z) {
		int sectionY = Math.floorDiv(y, 16);
		for (int i = 0; i < sections.size(); i++) {
			CompoundTag section = sections.getCompoundOrEmpty(i);
			if (section.getByteOr("Y", Byte.MIN_VALUE) != sectionY) {
				continue;
			}
			CompoundTag blockStates = section.getCompoundOrEmpty("block_states");
			if (blockStates.isEmpty()) {
				return null;
			}
			ListTag palette = blockStates.getListOrEmpty("palette");
			if (palette.isEmpty()) {
				return null;
			}
			int localY = Math.floorMod(y, 16);
			int index = localY * 256 + z * 16 + x;
			int paletteIndex = readPaletteIndex(blockStates, palette.size(), index);
			if (paletteIndex < 0 || paletteIndex >= palette.size()) {
				return null;
			}
			CompoundTag entry = palette.getCompoundOrEmpty(paletteIndex);
			String name = entry.getStringOr("Name", "");
			return name.isEmpty() ? null : name;
		}
		return null;
	}

	private static String findBiome(ListTag sections, int x, int y, int z) {
		int sectionY = Math.floorDiv(y, 16);
		for (int i = 0; i < sections.size(); i++) {
			CompoundTag section = sections.getCompoundOrEmpty(i);
			if (section.getByteOr("Y", Byte.MIN_VALUE) != sectionY) {
				continue;
			}
			CompoundTag biomes = section.getCompoundOrEmpty("biomes");
			if (biomes.isEmpty()) {
				return "minecraft:plains";
			}
			ListTag palette = biomes.getListOrEmpty("palette");
			if (palette.isEmpty()) {
				return "minecraft:plains";
			}
			int bx = x >> 2;
			int by = Math.floorMod(y, 16) >> 2;
			int bz = z >> 2;
			int index = by * 16 + bz * 4 + bx;
			int paletteIndex = readPaletteIndex(biomes, palette.size(), index);
			if (paletteIndex < 0 || paletteIndex >= palette.size()) {
				return palette.getStringOr(0, "minecraft:plains");
			}
			return palette.getStringOr(paletteIndex, "minecraft:plains");
		}
		return "minecraft:plains";
	}

	private static int readPaletteIndex(CompoundTag container, int paletteSize, int index) {
		if (paletteSize <= 1) {
			return 0;
		}
		long[] data = container.getLongArray("data").orElse(null);
		if (data == null || data.length == 0) {
			return 0;
		}
		int bits = Math.max(1, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
		int valuesPerLong = 64 / bits;
		if (valuesPerLong <= 0) {
			return 0;
		}
		int longIndex = index / valuesPerLong;
		if (longIndex >= data.length) {
			return 0;
		}
		int bitIndex = (index % valuesPerLong) * bits;
		return (int) ((data[longIndex] >>> bitIndex) & ((1L << bits) - 1));
	}

	private static int[] emptyHeights() {
		int[] h = new int[256];
		for (int i = 0; i < 256; i++) {
			h[i] = Short.MIN_VALUE;
		}
		return h;
	}

	private static String[] emptyNames(String fill) {
		String[] a = new String[256];
		for (int i = 0; i < 256; i++) {
			a[i] = fill;
		}
		return a;
	}

	private static String fluidLegacy(String name) {
		if ("minecraft:flowing_water".equals(name) || "minecraft:water".equals(name)) {
			return "minecraft:water";
		}
		if ("minecraft:flowing_lava".equals(name) || "minecraft:lava".equals(name)) {
			return "minecraft:lava";
		}
		return name;
	}

	private static boolean isWater(String name) {
		return "minecraft:water".equals(name) || "minecraft:flowing_water".equals(name);
	}

	/** Regular ice only — packed/blue ice are solid map surface in VoxelMap. */
	private static boolean isMapIce(String name) {
		return "minecraft:ice".equals(name) || "minecraft:frosted_ice".equals(name);
	}

	/** Snow layer only — snow_block is solid surface (matches Blocks.SNOW in VoxelMap). */
	private static boolean isSnow(String name) {
		return "minecraft:snow".equals(name);
	}

	private static boolean isAir(String name) {
		return "minecraft:air".equals(name)
				|| "minecraft:cave_air".equals(name)
				|| "minecraft:void_air".equals(name);
	}

	private static boolean isLeaves(String name) {
		return name.endsWith("_leaves") || name.endsWith("_leaves_pile") || name.contains(":leaves");
	}

	/**
	 * Approximate {@code lightDampening > 0 || face occlusion} without full block shapes.
	 * Non-opaque plants/snow/glass must NOT become the map surface — that caused speckled tiles.
	 */
	private static boolean hasMapOpacity(String name) {
		if (name == null || isAir(name)) {
			return false;
		}
		if (isSnow(name)) {
			return false;
		}
		if (isPassThroughDecoration(name)) {
			return false;
		}
		if (name.contains("glass") || name.contains("stained_glass_pane")) {
			return false;
		}
		if ("minecraft:light".equals(name) || name.contains("bubble_column")) {
			return false;
		}
		return true;
	}

	private static boolean isPassThroughDecoration(String name) {
		return name.contains("grass") && !name.contains("grass_block") && !name.contains("grass_path")
				|| name.endsWith("_fern") || name.equals("minecraft:fern") || name.equals("minecraft:large_fern")
				|| name.contains("flower") || name.endsWith("_tulip") || name.contains("orchid")
				|| name.contains("lilac") || name.contains("rose_bush") || name.contains("peony")
				|| name.contains("sunflower") || name.contains("pitcher") || name.contains("torchflower")
				|| name.contains("pink_petals") || name.contains("wildflowers")
				|| name.contains("sapling") || name.contains("mushroom") && !name.contains("block")
				|| name.contains("carpet") || name.contains("rail")
				|| name.contains("torch") || name.contains("sign") || name.contains("banner")
				|| name.contains("button") || name.contains("lever") || name.contains("pressure_plate")
				|| name.contains("tripwire") || name.contains("string")
				|| name.contains("wheat") || name.contains("carrot") || name.contains("potato")
				|| name.contains("beetroot") || name.contains("torchflower_crop") || name.contains("pitcher_crop")
				|| name.contains("sweet_berry") || name.contains("cave_vines") || name.contains("twisting_vines")
				|| name.contains("weeping_vines") || name.equals("minecraft:vine")
				|| name.contains("sugar_cane") || name.contains("bamboo") && !name.contains("block")
				|| name.contains("dead_bush") || name.contains("crimson_roots") || name.contains("warped_roots")
				|| name.contains("nether_sprouts") || name.contains("hanging_roots")
				|| name.contains("spore_blossom") || name.contains("glow_lichen")
				|| name.contains("scaffolding") || name.contains("cobweb")
				|| name.contains("seagrass") || name.contains("kelp")
				|| name.contains("coral") && name.contains("fan")
				|| name.contains("sea_pickle") || name.contains("frogspawn");
	}

	private static int lightDampeningApprox(String name) {
		if (isAir(name) || isPassThroughDecoration(name) || isSnow(name)) {
			return 0;
		}
		if (isWater(name) || isMapIce(name)) {
			return 1;
		}
		if (name.contains("glass") || name.contains("leaves") || name.contains("ice")) {
			return 1;
		}
		return 15;
	}

	private static boolean isMotionBlockingName(String name) {
		return hasMapOpacity(name) || isLeaves(name) || isWater(name) || isMapIce(name);
	}

	@Override
	public void close() throws IOException {
		raf.close();
	}

	private record ColumnSample(
			int surfaceHeight, String surfaceBlock, String biome,
			int oceanHeight, String oceanBlock,
			int transparentHeight, String transparentBlock,
			int foliageHeight, String foliageBlock
	) {
	}

	public record ChunkSurface(
			int chunkX, int chunkZ,
			int[] heights, String[] blocks, String[] biomes,
			int[] oceanHeights, String[] oceanBlocks,
			int[] transparentHeights, String[] transparentBlocks,
			int[] foliageHeights, String[] foliageBlocks
	) {
	}
}
