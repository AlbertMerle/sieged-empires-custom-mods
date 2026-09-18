package com.voxmapsync.server.webmap;

import java.util.HashMap;
import java.util.Map;

/**
 * Palette lookup and color blending for web map tile rendering.
 * Matches VoxelMap in-game map rendering colors, biome tints, and transparency.
 */
public final class WebColorPalette {
	private static final Map<String, Integer> BLOCK_COLORS = new HashMap<>(512);
	private static final Map<String, Integer> BIOME_GRASS_COLORS = new HashMap<>(256);
	private static final Map<String, Integer> BIOME_FOLIAGE_COLORS = new HashMap<>(256);
	private static final Map<String, Integer> BIOME_WATER_COLORS = new HashMap<>(256);

	public static final int DEFAULT_GRASS_TINT = 0x91BD59;
	public static final int DEFAULT_FOLIAGE_TINT = 0x77AB2F;
	public static final int DEFAULT_WATER_COLOR = 0x3F76E4;

	static {
		initBlockColors();
		initBiomeColors();
	}

	private WebColorPalette() {
	}

	public static int getBlockColor(String blockId) {
		if (blockId == null || blockId.isBlank() || blockId.equals("minecraft:air") || blockId.equals("minecraft:cave_air") || blockId.equals("minecraft:void_air")) {
			return 0;
		}
		Integer color = BLOCK_COLORS.get(blockId);
		if (color != null) {
			return color;
		}
		// Strip namespace if needed
		String name = blockId.contains(":") ? blockId.substring(blockId.indexOf(':') + 1) : blockId;
		color = BLOCK_COLORS.get(name);
		if (color != null) {
			return color;
		}
		// Suffix / keyword heuristics
		if (name.contains("grass_block") || name.contains("moss")) return 0x5B8731;
		if (name.contains("dirt") || name.contains("mud") || name.contains("farmland")) return 0x866043;
		if (name.contains("sandstone")) return 0xD8CB9B;
		if (name.contains("red_sandstone")) return 0xBA6322;
		if (name.contains("red_sand")) return 0xBF6721;
		if (name.contains("sand")) return 0xDBD3A0;
		if (name.contains("stone") || name.contains("andesite")) return 0x7A7A7A;
		if (name.contains("granite")) return 0x9A6C5B;
		if (name.contains("diorite") || name.contains("calcite")) return 0xD4D4D4;
		if (name.contains("tuff") || name.contains("basalt") || name.contains("slate")) return 0x484848;
		if (name.contains("snow") || name.contains("powder_snow")) return 0xF5FBFB;
		if (name.contains("blue_ice")) return 0x74A8FD;
		if (name.contains("packed_ice")) return 0x8DB5F8;
		if (name.contains("ice")) return 0xA0C0FF;
		if (name.contains("water")) return DEFAULT_WATER_COLOR;
		if (name.contains("lava")) return 0xD96719;
		if (name.contains("leaves") || name.contains("leaf")) return 0x4A6B29;
		if (name.contains("wood") || name.contains("log") || name.contains("planks")) return 0x9E7E4F;
		if (name.contains("terracotta")) return 0x985E43;
		if (name.contains("concrete")) return 0x808080;
		if (name.contains("wool") || name.contains("carpet")) return 0xDDDDDD;

		// Deterministic hash fallback for any unknown modded block
		int hash = blockId.hashCode();
		int r = 100 + (Math.abs(hash) % 100);
		int g = 100 + (Math.abs(hash >> 8) % 100);
		int b = 100 + (Math.abs(hash >> 16) % 100);
		return (r << 16) | (g << 8) | b;
	}

	public static boolean isGrassTinted(String blockId) {
		if (blockId == null) return false;
		return blockId.contains("grass_block") || blockId.contains("short_grass") || blockId.contains("tall_grass")
				|| blockId.contains("fern") || blockId.contains("large_fern") || blockId.contains("sugar_cane");
	}

	public static boolean isFoliageTinted(String blockId) {
		if (blockId == null) return false;
		return blockId.contains("oak_leaves") || blockId.contains("jungle_leaves") || blockId.contains("acacia_leaves")
				|| blockId.contains("dark_oak_leaves") || blockId.contains("vine") || blockId.contains("mangrove_leaves");
	}

	public static boolean isWater(String blockId) {
		return blockId != null && blockId.contains("water");
	}

	public static boolean isIce(String blockId) {
		return blockId != null && blockId.contains("ice");
	}

	public static int getBiomeGrassColor(String biomeId) {
		if (biomeId == null) return DEFAULT_GRASS_TINT;
		Integer c = BIOME_GRASS_COLORS.get(biomeId);
		return c != null ? c : DEFAULT_GRASS_TINT;
	}

	public static int getBiomeFoliageColor(String biomeId) {
		if (biomeId == null) return DEFAULT_FOLIAGE_TINT;
		Integer c = BIOME_FOLIAGE_COLORS.get(biomeId);
		return c != null ? c : DEFAULT_FOLIAGE_TINT;
	}

	public static int getBiomeWaterColor(String biomeId) {
		if (biomeId == null) return DEFAULT_WATER_COLOR;
		Integer c = BIOME_WATER_COLORS.get(biomeId);
		return c != null ? c : DEFAULT_WATER_COLOR;
	}

	public static int multiplyColors(int rgb1, int rgb2) {
		int r1 = (rgb1 >> 16) & 0xFF;
		int g1 = (rgb1 >> 8) & 0xFF;
		int b1 = rgb1 & 0xFF;
		int r2 = (rgb2 >> 16) & 0xFF;
		int g2 = (rgb2 >> 8) & 0xFF;
		int b2 = rgb2 & 0xFF;
		int r = (r1 * r2) / 255;
		int g = (g1 * g2) / 255;
		int b = (b1 * b2) / 255;
		return (r << 16) | (g << 8) | b;
	}

	public static int blendAlpha(int baseRgb, int overRgb, float alpha) {
		if (alpha <= 0.01f) return baseRgb;
		if (alpha >= 0.99f) return overRgb;
		int r1 = (baseRgb >> 16) & 0xFF;
		int g1 = (baseRgb >> 8) & 0xFF;
		int b1 = baseRgb & 0xFF;
		int r2 = (overRgb >> 16) & 0xFF;
		int g2 = (overRgb >> 8) & 0xFF;
		int b2 = overRgb & 0xFF;
		int r = Math.clamp(Math.round(r1 * (1f - alpha) + r2 * alpha), 0, 255);
		int g = Math.clamp(Math.round(g1 * (1f - alpha) + g2 * alpha), 0, 255);
		int b = Math.clamp(Math.round(b1 * (1f - alpha) + b2 * alpha), 0, 255);
		return (r << 16) | (g << 8) | b;
	}

	public static int shadeSlope(int rgb, int slopeDiff, int height) {
		float factor = 1.0f;
		if (slopeDiff > 0) {
			factor += Math.min(0.35f, slopeDiff * 0.08f);
		} else if (slopeDiff < 0) {
			factor -= Math.min(0.40f, -slopeDiff * 0.08f);
		}
		// subtle elevation gradience
		factor += (height - 64) * 0.0008f;
		factor = Math.clamp(factor, 0.45f, 1.45f);

		int r = Math.clamp(Math.round(((rgb >> 16) & 0xFF) * factor), 0, 255);
		int g = Math.clamp(Math.round(((rgb >> 8) & 0xFF) * factor), 0, 255);
		int b = Math.clamp(Math.round((rgb & 0xFF) * factor), 0, 255);
		return (r << 16) | (g << 8) | b;
	}

	private static void initBlockColors() {
		// Surface / terrain
		BLOCK_COLORS.put("minecraft:grass_block", 0x5B8731);
		BLOCK_COLORS.put("minecraft:dirt", 0x866043);
		BLOCK_COLORS.put("minecraft:coarse_dirt", 0x77553B);
		BLOCK_COLORS.put("minecraft:podzol", 0x5C3E20);
		BLOCK_COLORS.put("minecraft:rooted_dirt", 0x90674C);
		BLOCK_COLORS.put("minecraft:mud", 0x3C393D);
		BLOCK_COLORS.put("minecraft:muddy_mangrove_roots", 0x44392E);
		BLOCK_COLORS.put("minecraft:farmland", 0x482C17);
		BLOCK_COLORS.put("minecraft:dirt_path", 0x947541);
		BLOCK_COLORS.put("minecraft:mycelium", 0x6F6265);
		BLOCK_COLORS.put("minecraft:moss_block", 0x596E2D);
		BLOCK_COLORS.put("minecraft:moss_carpet", 0x596E2D);

		// Stone & underground
		BLOCK_COLORS.put("minecraft:stone", 0x7E7E7E);
		BLOCK_COLORS.put("minecraft:smooth_stone", 0x9F9F9F);
		BLOCK_COLORS.put("minecraft:cobblestone", 0x6D6D6D);
		BLOCK_COLORS.put("minecraft:mossy_cobblestone", 0x5C6A50);
		BLOCK_COLORS.put("minecraft:stone_bricks", 0x757575);
		BLOCK_COLORS.put("minecraft:mossy_stone_bricks", 0x647259);
		BLOCK_COLORS.put("minecraft:granite", 0x9A6C5B);
		BLOCK_COLORS.put("minecraft:polished_granite", 0x9B6C5C);
		BLOCK_COLORS.put("minecraft:diorite", 0xC0C0C0);
		BLOCK_COLORS.put("minecraft:polished_diorite", 0xC6C6C6);
		BLOCK_COLORS.put("minecraft:andesite", 0x848484);
		BLOCK_COLORS.put("minecraft:polished_andesite", 0x858585);
		BLOCK_COLORS.put("minecraft:deepslate", 0x4D4D54);
		BLOCK_COLORS.put("minecraft:cobbled_deepslate", 0x3E3E43);
		BLOCK_COLORS.put("minecraft:tuff", 0x585954);
		BLOCK_COLORS.put("minecraft:calcite", 0xD8D5CD);
		BLOCK_COLORS.put("minecraft:dripstone_block", 0x866B5F);
		BLOCK_COLORS.put("minecraft:bedrock", 0x333333);
		BLOCK_COLORS.put("minecraft:obsidian", 0x141021);
		BLOCK_COLORS.put("minecraft:crying_obsidian", 0x1E0C38);

		// Sand & desert
		BLOCK_COLORS.put("minecraft:sand", 0xD8CB9B);
		BLOCK_COLORS.put("minecraft:sandstone", 0xD8CB9B);
		BLOCK_COLORS.put("minecraft:smooth_sandstone", 0xDCCFA3);
		BLOCK_COLORS.put("minecraft:red_sand", 0xBF6721);
		BLOCK_COLORS.put("minecraft:red_sandstone", 0xB55E1C);
		BLOCK_COLORS.put("minecraft:smooth_red_sandstone", 0xBA6322);
		BLOCK_COLORS.put("minecraft:gravel", 0x737070);
		BLOCK_COLORS.put("minecraft:clay", 0x9EA4B0);

		// Snow & ice
		BLOCK_COLORS.put("minecraft:snow", 0xF5FBFB);
		BLOCK_COLORS.put("minecraft:snow_block", 0xF5FBFB);
		BLOCK_COLORS.put("minecraft:powder_snow", 0xF5FBFB);
		BLOCK_COLORS.put("minecraft:ice", 0x91B5F5);
		BLOCK_COLORS.put("minecraft:packed_ice", 0x8EB3F8);
		BLOCK_COLORS.put("minecraft:blue_ice", 0x74A8FD);

		// Liquids
		BLOCK_COLORS.put("minecraft:water", DEFAULT_WATER_COLOR);
		BLOCK_COLORS.put("minecraft:lava", 0xD96719);

		// Terracotta
		BLOCK_COLORS.put("minecraft:terracotta", 0x985E43);
		BLOCK_COLORS.put("minecraft:white_terracotta", 0xD1B1A1);
		BLOCK_COLORS.put("minecraft:orange_terracotta", 0xA05325);
		BLOCK_COLORS.put("minecraft:magenta_terracotta", 0x95576C);
		BLOCK_COLORS.put("minecraft:light_blue_terracotta", 0x706C8A);
		BLOCK_COLORS.put("minecraft:yellow_terracotta", 0xBA8524);
		BLOCK_COLORS.put("minecraft:lime_terracotta", 0x677535);
		BLOCK_COLORS.put("minecraft:pink_terracotta", 0xA04D4E);
		BLOCK_COLORS.put("minecraft:gray_terracotta", 0x392A24);
		BLOCK_COLORS.put("minecraft:light_gray_terracotta", 0x876B61);
		BLOCK_COLORS.put("minecraft:cyan_terracotta", 0x575B5B);
		BLOCK_COLORS.put("minecraft:purple_terracotta", 0x7A4958);
		BLOCK_COLORS.put("minecraft:blue_terracotta", 0x4A3B5B);
		BLOCK_COLORS.put("minecraft:brown_terracotta", 0x4D3223);
		BLOCK_COLORS.put("minecraft:green_terracotta", 0x4C522A);
		BLOCK_COLORS.put("minecraft:red_terracotta", 0x8E3B2E);
		BLOCK_COLORS.put("minecraft:black_terracotta", 0x251610);

		// Foliage / Leaves
		BLOCK_COLORS.put("minecraft:oak_leaves", 0x4A6B29);
		BLOCK_COLORS.put("minecraft:spruce_leaves", 0x3B593B);
		BLOCK_COLORS.put("minecraft:birch_leaves", 0x68863A);
		BLOCK_COLORS.put("minecraft:jungle_leaves", 0x3B7924);
		BLOCK_COLORS.put("minecraft:acacia_leaves", 0x5F6B2A);
		BLOCK_COLORS.put("minecraft:dark_oak_leaves", 0x375A24);
		BLOCK_COLORS.put("minecraft:mangrove_leaves", 0x506B29);
		BLOCK_COLORS.put("minecraft:cherry_leaves", 0xE891B0);
		BLOCK_COLORS.put("minecraft:azalea_leaves", 0x5B7A28);
		BLOCK_COLORS.put("minecraft:flowering_azalea_leaves", 0x8B6848);
		BLOCK_COLORS.put("minecraft:short_grass", 0x5B8731);
		BLOCK_COLORS.put("minecraft:tall_grass", 0x5B8731);
		BLOCK_COLORS.put("minecraft:fern", 0x5B8731);
		BLOCK_COLORS.put("minecraft:large_fern", 0x5B8731);
		BLOCK_COLORS.put("minecraft:dandelion", 0xF7E23B);
		BLOCK_COLORS.put("minecraft:poppy", 0xED302C);
		BLOCK_COLORS.put("minecraft:blue_orchid", 0x2EADED);
		BLOCK_COLORS.put("minecraft:allium", 0xB766E5);
		BLOCK_COLORS.put("minecraft:azure_bluet", 0xD6E8E8);
		BLOCK_COLORS.put("minecraft:red_tulip", 0xED302C);
		BLOCK_COLORS.put("minecraft:orange_tulip", 0xEB6E1C);
		BLOCK_COLORS.put("minecraft:white_tulip", 0xEDEDED);
		BLOCK_COLORS.put("minecraft:pink_tulip", 0xE88EA8);
		BLOCK_COLORS.put("minecraft:oxeye_daisy", 0xDCE0E0);
		BLOCK_COLORS.put("minecraft:cornflower", 0x4169E1);
		BLOCK_COLORS.put("minecraft:lily_of_the_valley", 0xF2F2F2);
		BLOCK_COLORS.put("minecraft:sunflower", 0xF7D82C);
		BLOCK_COLORS.put("minecraft:lilac", 0xB766E5);
		BLOCK_COLORS.put("minecraft:rose_bush", 0xC42A27);
		BLOCK_COLORS.put("minecraft:peony", 0xE88EA8);
		BLOCK_COLORS.put("minecraft:lily_pad", 0x208030);
		BLOCK_COLORS.put("minecraft:seagrass", 0x317F36);
		BLOCK_COLORS.put("minecraft:tall_seagrass", 0x317F36);
		BLOCK_COLORS.put("minecraft:kelp", 0x4D6827);
		BLOCK_COLORS.put("minecraft:kelp_plant", 0x4D6827);
		BLOCK_COLORS.put("minecraft:sugar_cane", 0x76A838);
		BLOCK_COLORS.put("minecraft:bamboo", 0x5D8E24);
		BLOCK_COLORS.put("minecraft:cactus", 0x527D24);
		BLOCK_COLORS.put("minecraft:dead_bush", 0x7D5726);

		// Wood & construction
		BLOCK_COLORS.put("minecraft:oak_planks", 0xA2824E);
		BLOCK_COLORS.put("minecraft:spruce_planks", 0x674A2C);
		BLOCK_COLORS.put("minecraft:birch_planks", 0xC6B679);
		BLOCK_COLORS.put("minecraft:jungle_planks", 0x9B6B4C);
		BLOCK_COLORS.put("minecraft:acacia_planks", 0xA85A32);
		BLOCK_COLORS.put("minecraft:dark_oak_planks", 0x3C2712);
		BLOCK_COLORS.put("minecraft:mangrove_planks", 0x763631);
		BLOCK_COLORS.put("minecraft:cherry_planks", 0xDC9A97);
		BLOCK_COLORS.put("minecraft:bamboo_planks", 0xB89E47);
		BLOCK_COLORS.put("minecraft:bricks", 0x964336);
		BLOCK_COLORS.put("minecraft:mud_bricks", 0x89674F);
		BLOCK_COLORS.put("minecraft:glass", 0xC8E0E8);
		BLOCK_COLORS.put("minecraft:tinted_glass", 0x3D3540);
	}

	private static void initBiomeColors() {
		// Vanilla biomes - grass tints
		BIOME_GRASS_COLORS.put("minecraft:plains", 0x91BD59);
		BIOME_GRASS_COLORS.put("minecraft:sunflower_plains", 0x91BD59);
		BIOME_GRASS_COLORS.put("minecraft:forest", 0x79C05A);
		BIOME_GRASS_COLORS.put("minecraft:flower_forest", 0x79C05A);
		BIOME_GRASS_COLORS.put("minecraft:birch_forest", 0x88BB67);
		BIOME_GRASS_COLORS.put("minecraft:old_growth_birch_forest", 0x88BB67);
		BIOME_GRASS_COLORS.put("minecraft:dark_forest", 0x507A32);
		BIOME_GRASS_COLORS.put("minecraft:jungle", 0x59C93C);
		BIOME_GRASS_COLORS.put("minecraft:sparse_jungle", 0x64C73F);
		BIOME_GRASS_COLORS.put("minecraft:bamboo_jungle", 0x59C93C);
		BIOME_GRASS_COLORS.put("minecraft:taiga", 0x86B783);
		BIOME_GRASS_COLORS.put("minecraft:old_growth_pine_taiga", 0x86B783);
		BIOME_GRASS_COLORS.put("minecraft:old_growth_spruce_taiga", 0x86B783);
		BIOME_GRASS_COLORS.put("minecraft:snowy_taiga", 0x80B497);
		BIOME_GRASS_COLORS.put("minecraft:snowy_plains", 0x80B497);
		BIOME_GRASS_COLORS.put("minecraft:snowy_slopes", 0x80B497);
		BIOME_GRASS_COLORS.put("minecraft:ice_spikes", 0x80B497);
		BIOME_GRASS_COLORS.put("minecraft:jagged_peaks", 0x80B497);
		BIOME_GRASS_COLORS.put("minecraft:frozen_peaks", 0x80B497);
		BIOME_GRASS_COLORS.put("minecraft:stony_peaks", 0x8AB689);
		BIOME_GRASS_COLORS.put("minecraft:grove", 0x80B497);
		BIOME_GRASS_COLORS.put("minecraft:meadow", 0x83BB6D);
		BIOME_GRASS_COLORS.put("minecraft:cherry_grove", 0xB6DB67);
		BIOME_GRASS_COLORS.put("minecraft:swamp", 0x6A7039);
		BIOME_GRASS_COLORS.put("minecraft:mangrove_swamp", 0x6A7039);
		BIOME_GRASS_COLORS.put("minecraft:desert", 0xBFB755);
		BIOME_GRASS_COLORS.put("minecraft:savanna", 0xBFB755);
		BIOME_GRASS_COLORS.put("minecraft:savanna_plateau", 0xBFB755);
		BIOME_GRASS_COLORS.put("minecraft:windswept_savanna", 0xBFB755);
		BIOME_GRASS_COLORS.put("minecraft:badlands", 0x90814D);
		BIOME_GRASS_COLORS.put("minecraft:wooded_badlands", 0x90814D);
		BIOME_GRASS_COLORS.put("minecraft:eroded_badlands", 0x90814D);
		BIOME_GRASS_COLORS.put("minecraft:windswept_hills", 0x8AB689);
		BIOME_GRASS_COLORS.put("minecraft:windswept_forest", 0x8AB689);
		BIOME_GRASS_COLORS.put("minecraft:windswept_gravelly_hills", 0x8AB689);
		BIOME_GRASS_COLORS.put("minecraft:beach", 0x91BD59);
		BIOME_GRASS_COLORS.put("minecraft:snowy_beach", 0x80B497);
		BIOME_GRASS_COLORS.put("minecraft:stony_shore", 0x8AB689);
		BIOME_GRASS_COLORS.put("minecraft:river", 0x8AB689);
		BIOME_GRASS_COLORS.put("minecraft:frozen_river", 0x80B497);
		BIOME_GRASS_COLORS.put("minecraft:ocean", 0x8AB689);
		BIOME_GRASS_COLORS.put("minecraft:deep_ocean", 0x8AB689);
		BIOME_GRASS_COLORS.put("minecraft:warm_ocean", 0x8AB689);
		BIOME_GRASS_COLORS.put("minecraft:lukewarm_ocean", 0x8AB689);
		BIOME_GRASS_COLORS.put("minecraft:deep_lukewarm_ocean", 0x8AB689);
		BIOME_GRASS_COLORS.put("minecraft:cold_ocean", 0x8AB689);
		BIOME_GRASS_COLORS.put("minecraft:deep_cold_ocean", 0x8AB689);
		BIOME_GRASS_COLORS.put("minecraft:frozen_ocean", 0x80B497);
		BIOME_GRASS_COLORS.put("minecraft:deep_frozen_ocean", 0x80B497);

		// Terralith biomes
		BIOME_GRASS_COLORS.put("terralith:alpine_grove", 0x7AB870);
		BIOME_GRASS_COLORS.put("terralith:alpine_highland", 0x7AB870);
		BIOME_GRASS_COLORS.put("terralith:amethyst_canyon", 0x8AB689);
		BIOME_GRASS_COLORS.put("terralith:amethyst_rainforest", 0x59C93C);
		BIOME_GRASS_COLORS.put("terralith:arid_highlands", 0xBFB755);
		BIOME_GRASS_COLORS.put("terralith:birch_taiga", 0x88BB67);
		BIOME_GRASS_COLORS.put("terralith:brushland", 0xBFB755);
		BIOME_GRASS_COLORS.put("terralith:caldera", 0x758E5E);
		BIOME_GRASS_COLORS.put("terralith:canyon", 0x90814D);
		BIOME_GRASS_COLORS.put("terralith:cloud_forest", 0x60C850);
		BIOME_GRASS_COLORS.put("terralith:cold_shrubland", 0x86B783);
		BIOME_GRASS_COLORS.put("terralith:desert_canyon", 0xBFB755);
		BIOME_GRASS_COLORS.put("terralith:desert_oasis", 0x70C840);
		BIOME_GRASS_COLORS.put("terralith:desert_spires", 0xBFB755);
		BIOME_GRASS_COLORS.put("terralith:forested_highlands", 0x79C05A);
		BIOME_GRASS_COLORS.put("terralith:fractured_savanna", 0xBFB755);
		BIOME_GRASS_COLORS.put("terralith:frozen_cliffs", 0x70A8A0);
		BIOME_GRASS_COLORS.put("terralith:glacial_chasm", 0x70A8A0);
		BIOME_GRASS_COLORS.put("terralith:gravel_beach", 0x8AB689);
		BIOME_GRASS_COLORS.put("terralith:gravel_desert", 0xBFB755);
		BIOME_GRASS_COLORS.put("terralith:haze_mountain", 0x8AB689);
		BIOME_GRASS_COLORS.put("terralith:highland", 0x82BC68);
		BIOME_GRASS_COLORS.put("terralith:hot_shrubland", 0xBFB755);
		BIOME_GRASS_COLORS.put("terralith:ice_marsh", 0x6A7039);
		BIOME_GRASS_COLORS.put("terralith:jungle_mountains", 0x50D030);
		BIOME_GRASS_COLORS.put("terralith:lavender_forest", 0x8ABF6E);
		BIOME_GRASS_COLORS.put("terralith:lavender_valley", 0x8ABF6E);
		BIOME_GRASS_COLORS.put("terralith:lush_valley", 0x60C850);
		BIOME_GRASS_COLORS.put("terralith:mirage_isles", 0x40DC80);
		BIOME_GRASS_COLORS.put("terralith:moon_grove", 0x60A0A0);
		BIOME_GRASS_COLORS.put("terralith:orchid_swamp", 0x6A7039);
		BIOME_GRASS_COLORS.put("terralith:painted_mountains", 0x90814D);
		BIOME_GRASS_COLORS.put("terralith:red_oasis", 0x64C73F);
		BIOME_GRASS_COLORS.put("terralith:rocky_mountains", 0x92A670);
		BIOME_GRASS_COLORS.put("terralith:rocky_shrubland", 0x92A670);
		BIOME_GRASS_COLORS.put("terralith:sakura_grove", 0xB6DB67);
		BIOME_GRASS_COLORS.put("terralith:sakura_valley", 0xB6DB67);
		BIOME_GRASS_COLORS.put("terralith:sandstone_valley", 0xBFB755);
		BIOME_GRASS_COLORS.put("terralith:savanna_badlands", 0xBFB755);
		BIOME_GRASS_COLORS.put("terralith:savanna_slopes", 0xBFB755);
		BIOME_GRASS_COLORS.put("terralith:scarlet_mountains", 0x8E3B2E);
		BIOME_GRASS_COLORS.put("terralith:shield", 0x88BA65);
		BIOME_GRASS_COLORS.put("terralith:shrubland", 0x88BA65);
		BIOME_GRASS_COLORS.put("terralith:siberian_grove", 0x7AA880);
		BIOME_GRASS_COLORS.put("terralith:siberian_taiga", 0x7AA880);
		BIOME_GRASS_COLORS.put("terralith:snowy_badlands", 0x80B497);
		BIOME_GRASS_COLORS.put("terralith:snowy_cherry_grove", 0x80B497);
		BIOME_GRASS_COLORS.put("terralith:snowy_shield", 0x80B497);
		BIOME_GRASS_COLORS.put("terralith:steppe", 0xBFB755);
		BIOME_GRASS_COLORS.put("terralith:stony_shore", 0x8AB689);
		BIOME_GRASS_COLORS.put("terralith:temperate_highlands", 0x80BC60);
		BIOME_GRASS_COLORS.put("terralith:tropical_jungle", 0x4FD830);
		BIOME_GRASS_COLORS.put("terralith:valley_clearing", 0x83BB6D);
		BIOME_GRASS_COLORS.put("terralith:volcanic_crater", 0x555555);
		BIOME_GRASS_COLORS.put("terralith:volcanic_peaks", 0x555555);
		BIOME_GRASS_COLORS.put("terralith:warm_river", 0x8AB550);
		BIOME_GRASS_COLORS.put("terralith:white_cliffs", 0xD0D0C0);
		BIOME_GRASS_COLORS.put("terralith:white_mesa", 0xD0D0C0);
		BIOME_GRASS_COLORS.put("terralith:windswept_spires", 0x8AB689);
		BIOME_GRASS_COLORS.put("terralith:wintry_forest", 0x80B497);
		BIOME_GRASS_COLORS.put("terralith:wintry_lowlands", 0x80B497);
		BIOME_GRASS_COLORS.put("terralith:yellowstone", 0x9AA650);

		// Foliage tints
		for (Map.Entry<String, Integer> entry : BIOME_GRASS_COLORS.entrySet()) {
			BIOME_FOLIAGE_COLORS.put(entry.getKey(), entry.getValue());
		}
		BIOME_FOLIAGE_COLORS.put("minecraft:dark_forest", 0x507A32);
		BIOME_FOLIAGE_COLORS.put("minecraft:swamp", 0x6A7039);
		BIOME_FOLIAGE_COLORS.put("minecraft:mangrove_swamp", 0x8DB127);
		BIOME_FOLIAGE_COLORS.put("minecraft:cherry_grove", 0xFFBBD5);
		BIOME_FOLIAGE_COLORS.put("terralith:sakura_grove", 0xFFBBD5);
		BIOME_FOLIAGE_COLORS.put("terralith:sakura_valley", 0xFFBBD5);
		BIOME_FOLIAGE_COLORS.put("terralith:snowy_cherry_grove", 0xFFBBD5);

		// Water colors
		BIOME_WATER_COLORS.put("minecraft:swamp", 0x617B64);
		BIOME_WATER_COLORS.put("minecraft:mangrove_swamp", 0x3A7A6A);
		BIOME_WATER_COLORS.put("minecraft:frozen_ocean", 0x3938C9);
		BIOME_WATER_COLORS.put("minecraft:deep_frozen_ocean", 0x3938C9);
		BIOME_WATER_COLORS.put("minecraft:frozen_river", 0x3938C9);
		BIOME_WATER_COLORS.put("minecraft:cold_ocean", 0x3D57D6);
		BIOME_WATER_COLORS.put("minecraft:deep_cold_ocean", 0x3D57D6);
		BIOME_WATER_COLORS.put("minecraft:lukewarm_ocean", 0x45ADF2);
		BIOME_WATER_COLORS.put("minecraft:deep_lukewarm_ocean", 0x45ADF2);
		BIOME_WATER_COLORS.put("minecraft:warm_ocean", 0x43D5EE);
		BIOME_WATER_COLORS.put("terralith:warm_river", 0x43D5EE);
		BIOME_WATER_COLORS.put("terralith:deep_warm_ocean", 0x43D5EE);
		BIOME_WATER_COLORS.put("terralith:mirage_isles", 0x30E0D0);
		BIOME_WATER_COLORS.put("terralith:yellowstone", 0x40B0A0);
		BIOME_WATER_COLORS.put("terralith:volcanic_crater", 0x304050);
	}
}
