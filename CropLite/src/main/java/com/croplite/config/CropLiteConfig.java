package com.croplite.config;

import com.croplite.CropLite;
import com.croplite.crop.TemperatureCategory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * CropLite config. Soil percentages scale crop growth relative to vanilla farmland.
 * {@link #cropGrowthRate} is a global multiplier (100 = 100% of the soil rate).
 * Temperature bands gate grass drops, crop growth (rot), sugar cane gen, and wild crops.
 */
public final class CropLiteConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("croplite.json");

	private static CropLiteConfig instance = defaults();

	/** Growth rate when planted on farmland (% of vanilla). Default 100. */
	public int farmlandGrowthPercent = 100;
	/** Growth rate when planted on dirt (% of vanilla). Default 20. */
	public int dirtGrowthPercent = 20;
	/** Growth rate when planted on grass blocks (% of vanilla). Default 15. */
	public int grassBlockGrowthPercent = 15;
	/** Growth rate when planted on coarse dirt (% of vanilla). Default 10. */
	public int coarseDirtGrowthPercent = 10;
	/** Growth rate when planted on sand (% of vanilla). Default 5. */
	public int sandGrowthPercent = 5;
	/**
	 * Extra growth percent when water is within 4 blocks horizontally (vanilla farmland
	 * water range: ±4 xz, y 0–1 relative to the soil). Default +5.
	 */
	public int waterBonusPercent = 5;
	/**
	 * Global crop growth multiplier. 100 = 100% (no change beyond soil rates).
	 * Multiplies with soil % for {@code CropBlock} plants (e.g. sand 5% × this 50% → 2.5% of vanilla).
	 * Also scales stems, sugar cane, cocoa, sweet berries, and saplings (natural ticks).
	 * Bonemeal: success chance = this rate (vanilla still consumes on failure), so 50% ≈ 2× bonemeal.
	 */
	public int cropGrowthRate = 100;
	/**
	 * When true, farmable crops (excluding saplings) may naturally generate on valid soils
	 * at a random growth stage.
	 */
	public boolean naturalCropGeneration = true;

	/**
	 * When true, peach/lemon/banana may generate in terrain (still rarity + climate gated).
	 * Saplings always grow when planted. When false, placed features are stripped from all biomes.
	 */
	public boolean naturalFruitTreeGeneration = true;

	/**
	 * Average chunks between peach tree attempts (cold/temperate, temp &lt; temperate max, not plains).
	 * Deterministic 1-in-N via {@code croplite:config_chunk_rarity}.
	 * {@code 0} disables peach; {@code 1} every eligible chunk; default {@code 30}.
	 */
	public int peachTreeChunksPerSpawn = 30;
	/**
	 * Average chunks between lemon tree attempts (temperate, temp &lt; temperate max, not plains).
	 * Default 30.
	 */
	public int lemonTreeChunksPerSpawn = 30;
	/**
	 * Average chunks between banana tree attempts (jungle, temp &gt; temperate max). Default 30.
	 */
	public int bananaTreeChunksPerSpawn = 30;

	/** Master switch for biome temperature checks (growth, sugar cane gen, loot). */
	public boolean temperatureChecks = true;

	/** Cold crops grow at biome base temperature ≤ this. Default 0.5. */
	public float coldCropMaxTemperature = 0.5F;
	/** Temperate crops grow at ≥ this. Default 0.4. */
	public float temperateCropMinTemperature = 0.4F;
	/** Temperate crops grow at ≤ this. Default 0.8. */
	public float temperateCropMaxTemperature = 0.8F;
	/** Tropical crops grow at ≥ this. Default 0.75. */
	public float tropicalCropMinTemperature = 0.75F;
	/** Tropical crops grow at ≤ this. Default 1.9 (excludes savanna/desert at temp 2.0). */
	public float tropicalCropMaxTemperature = 1.9F;

	/** Serene Seasons: growth % during winter. Default 30. */
	public int winterGrowthPercent = 30;
	/** Serene Seasons: growth % during tropical dry season (100−30 = slowed by 30%). Default 70. */
	public int drySeasonGrowthPercent = 70;
	/**
	 * Desert/badlands: non-hardy tropical crops with water within 2 blocks grow at this % rate.
	 * Without water they rot. Default 30.
	 */
	public int desertIrrigatedGrowthPercent = 30;

	/**
	 * Optional overrides: block id → allowed temperature categories.
	 * Empty / missing entries use built-in defaults in {@link com.croplite.crop.CropClimate}.
	 */
	public Map<String, Set<TemperatureCategory>> cropClimates = new LinkedHashMap<>();

	private CropLiteConfig() {
	}

	public static CropLiteConfig get() {
		return instance;
	}

	public static CropLiteConfig defaults() {
		CropLiteConfig config = new CropLiteConfig();
		config.cropClimates = defaultCropClimates();
		return config;
	}

	public static void load() {
		instance = defaults();

		if (Files.exists(CONFIG_PATH)) {
			try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				instance.farmlandGrowthPercent = getInt(json, "farmland_growth_percent", instance.farmlandGrowthPercent);
				instance.dirtGrowthPercent = getInt(json, "dirt_growth_percent", instance.dirtGrowthPercent);
				instance.grassBlockGrowthPercent = getInt(json, "grass_block_growth_percent", instance.grassBlockGrowthPercent);
				instance.coarseDirtGrowthPercent = getInt(json, "coarse_dirt_growth_percent", instance.coarseDirtGrowthPercent);
				instance.sandGrowthPercent = getInt(json, "sand_growth_percent", instance.sandGrowthPercent);
				instance.waterBonusPercent = getInt(json, "water_bonus_percent", instance.waterBonusPercent);
				instance.cropGrowthRate = getInt(json, "crop_growth_rate", instance.cropGrowthRate);
				if (json.has("natural_crop_generation")) {
					instance.naturalCropGeneration = json.get("natural_crop_generation").getAsBoolean();
				}
				if (json.has("natural_fruit_tree_generation")) {
					instance.naturalFruitTreeGeneration = json.get("natural_fruit_tree_generation").getAsBoolean();
				}
				instance.peachTreeChunksPerSpawn = readChunksPerSpawn(json, "peach_tree_chunks_per_spawn",
						"peach_tree_spawn_percent", instance.peachTreeChunksPerSpawn);
				instance.lemonTreeChunksPerSpawn = readChunksPerSpawn(json, "lemon_tree_chunks_per_spawn",
						"lemon_tree_spawn_percent", instance.lemonTreeChunksPerSpawn);
				instance.bananaTreeChunksPerSpawn = readChunksPerSpawn(json, "banana_tree_chunks_per_spawn",
						"banana_tree_spawn_percent", instance.bananaTreeChunksPerSpawn);
				if (json.has("temperature_checks")) {
					instance.temperatureChecks = json.get("temperature_checks").getAsBoolean();
				}
				// New overlapping growth ranges (preferred keys).
				instance.coldCropMaxTemperature = getFloat(json, "cold_crop_max_temperature",
						getFloat(json, "cold_max_temperature", instance.coldCropMaxTemperature));
				instance.temperateCropMinTemperature = getFloat(json, "temperate_crop_min_temperature", instance.temperateCropMinTemperature);
				instance.temperateCropMaxTemperature = getFloat(json, "temperate_crop_max_temperature", instance.temperateCropMaxTemperature);
				instance.tropicalCropMinTemperature = getFloat(json, "tropical_crop_min_temperature",
						getFloat(json, "tropical_min_temperature", instance.tropicalCropMinTemperature));
				instance.tropicalCropMaxTemperature = getFloat(json, "tropical_crop_max_temperature", instance.tropicalCropMaxTemperature);
				instance.winterGrowthPercent = getInt(json, "winter_growth_percent", instance.winterGrowthPercent);
				instance.drySeasonGrowthPercent = getInt(json, "dry_season_growth_percent", instance.drySeasonGrowthPercent);
				instance.desertIrrigatedGrowthPercent = getInt(json, "desert_irrigated_growth_percent", instance.desertIrrigatedGrowthPercent);
				if (json.has("crop_climates") && json.get("crop_climates").isJsonObject()) {
					instance.cropClimates = readCropClimates(json.getAsJsonObject("crop_climates"));
				}
			} catch (Exception e) {
				CropLite.LOGGER.error("Failed to load CropLite config; using defaults", e);
				instance = defaults();
			}
		}

		save();
		CropLite.LOGGER.info(
				"CropLite config: crop_growth_rate={}%, temp_checks={}, cold≤{}, temperate={}–{}, tropical={}–{}, winter={}%, dry={}%, desert_irrig={}%, natural_gen={}, fruit_trees={}, peach/lemon/banana tree 1-in-N chunks={}/{}/{}",
				instance.cropGrowthRate,
				instance.temperatureChecks,
				instance.coldCropMaxTemperature,
				instance.temperateCropMinTemperature,
				instance.temperateCropMaxTemperature,
				instance.tropicalCropMinTemperature,
				instance.tropicalCropMaxTemperature,
				instance.winterGrowthPercent,
				instance.drySeasonGrowthPercent,
				instance.desertIrrigatedGrowthPercent,
				instance.naturalCropGeneration,
				instance.naturalFruitTreeGeneration,
				instance.peachTreeChunksPerSpawn,
				instance.lemonTreeChunksPerSpawn,
				instance.bananaTreeChunksPerSpawn);
	}

	public static void save() {
		JsonObject json = new JsonObject();
		json.addProperty("_readme",
				"Soil growth percents are relative to vanilla farmland (100 = full vanilla on that soil). "
						+ "crop_growth_rate is a global multiplier stacked with soil % for CropBlock crops "
						+ "(e.g. sand_growth_percent 5 × crop_growth_rate 50 → 2.5% of vanilla). "
						+ "crop_growth_rate also scales pumpkin/melon/cantelope stems, sugar cane, cocoa, "
						+ "sweet berries, and saplings. Bonemeal success chance matches crop_growth_rate "
						+ "(item still consumed on failure → 50% needs ~2× bonemeal). "
						+ "Growth bands (overlapping): cold ≤ cold_crop_max_temperature; "
						+ "temperate between temperate_crop_min/max; tropical between tropical_crop_min/max. "
						+ "Serene Seasons: winter_growth_percent (default 30), dry_season_growth_percent (default 70). "
						+ "Desert/badlands: cactus/coffee/pepper/cantelope grow freely; other tropical crops need water "
						+ "within 2 blocks and grow at desert_irrigated_growth_percent (default 30); cold/temperate rot. "
						+ "crop_climates maps block ids to allowed climate bands. "
						+ "natural_crop_generation enables wild crop patches by biome category. "
						+ "natural_fruit_tree_generation enables peach/lemon/banana in terrain (default true). "
						+ "peach/lemon/banana_tree_chunks_per_spawn = deterministic 1-in-N eligible chunks "
						+ "(default 30; 0 = off that tree). Peach/lemon: not plains, temp < temperate max; "
						+ "banana: jungle only, temp > temperate max. Needs new chunks after changes. Saplings still grow.");
		json.addProperty("farmland_growth_percent", instance.farmlandGrowthPercent);
		json.addProperty("dirt_growth_percent", instance.dirtGrowthPercent);
		json.addProperty("grass_block_growth_percent", instance.grassBlockGrowthPercent);
		json.addProperty("coarse_dirt_growth_percent", instance.coarseDirtGrowthPercent);
		json.addProperty("sand_growth_percent", instance.sandGrowthPercent);
		json.addProperty("water_bonus_percent", instance.waterBonusPercent);
		json.addProperty("crop_growth_rate", instance.cropGrowthRate);
		json.addProperty("natural_crop_generation", instance.naturalCropGeneration);
		json.addProperty("natural_fruit_tree_generation", instance.naturalFruitTreeGeneration);
		json.addProperty("peach_tree_chunks_per_spawn", instance.peachTreeChunksPerSpawn);
		json.addProperty("lemon_tree_chunks_per_spawn", instance.lemonTreeChunksPerSpawn);
		json.addProperty("banana_tree_chunks_per_spawn", instance.bananaTreeChunksPerSpawn);
		json.addProperty("temperature_checks", instance.temperatureChecks);
		json.addProperty("cold_crop_max_temperature", instance.coldCropMaxTemperature);
		json.addProperty("temperate_crop_min_temperature", instance.temperateCropMinTemperature);
		json.addProperty("temperate_crop_max_temperature", instance.temperateCropMaxTemperature);
		json.addProperty("tropical_crop_min_temperature", instance.tropicalCropMinTemperature);
		json.addProperty("tropical_crop_max_temperature", instance.tropicalCropMaxTemperature);
		json.addProperty("winter_growth_percent", instance.winterGrowthPercent);
		json.addProperty("dry_season_growth_percent", instance.drySeasonGrowthPercent);
		json.addProperty("desert_irrigated_growth_percent", instance.desertIrrigatedGrowthPercent);
		json.add("crop_climates", writeCropClimates(instance.cropClimates));

		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
				GSON.toJson(json, writer);
			}
		} catch (IOException e) {
			CropLite.LOGGER.error("Failed to save CropLite config", e);
		}
	}

	public int soilGrowthPercent(BlockState soil) {
		Block block = soil.getBlock();
		if (block == Blocks.FARMLAND) {
			return farmlandGrowthPercent;
		}
		if (block == Blocks.DIRT) {
			return dirtGrowthPercent;
		}
		if (block == Blocks.GRASS_BLOCK) {
			return grassBlockGrowthPercent;
		}
		if (block == Blocks.COARSE_DIRT) {
			return coarseDirtGrowthPercent;
		}
		if (block == Blocks.SAND) {
			return sandGrowthPercent;
		}
		return farmlandGrowthPercent;
	}

	private static Map<String, Set<TemperatureCategory>> defaultCropClimates() {
		Map<String, Set<TemperatureCategory>> map = new LinkedHashMap<>();
		map.put("minecraft:wheat", EnumSet.of(TemperatureCategory.COLD, TemperatureCategory.TEMPERATE));
		map.put("minecraft:carrots", EnumSet.of(TemperatureCategory.COLD, TemperatureCategory.TEMPERATE));
		map.put("minecraft:potatoes", EnumSet.of(TemperatureCategory.TEMPERATE));
		map.put("minecraft:beetroots", EnumSet.of(TemperatureCategory.COLD, TemperatureCategory.TEMPERATE));
		map.put("minecraft:sweet_berry_bush", EnumSet.of(TemperatureCategory.COLD));
		map.put("minecraft:melon_stem", EnumSet.of(TemperatureCategory.TROPICAL));
		map.put("minecraft:pumpkin_stem", EnumSet.of(TemperatureCategory.TEMPERATE, TemperatureCategory.TROPICAL));
		map.put("minecraft:sugar_cane", EnumSet.of(TemperatureCategory.TROPICAL));
		map.put("minecraft:cocoa", EnumSet.of(TemperatureCategory.TROPICAL));
		map.put("minecraft:cactus", EnumSet.of(TemperatureCategory.TROPICAL));
		map.put("croplite:oats_crop", EnumSet.of(TemperatureCategory.TEMPERATE));
		map.put("croplite:beans_crop", EnumSet.of(TemperatureCategory.TEMPERATE, TemperatureCategory.TROPICAL));
		map.put("croplite:rice_crop", EnumSet.of(TemperatureCategory.TROPICAL));
		map.put("croplite:tomato_crop", EnumSet.of(TemperatureCategory.TROPICAL));
		map.put("croplite:pepper_crop", EnumSet.of(TemperatureCategory.TROPICAL));
		map.put("croplite:eggplant_crop", EnumSet.of(TemperatureCategory.TROPICAL));
		map.put("croplite:cucumber_crop", EnumSet.of(TemperatureCategory.TROPICAL));
		map.put("croplite:sweet_potato_crop", EnumSet.of(TemperatureCategory.TROPICAL));
		map.put("croplite:cantelope_stem", EnumSet.of(TemperatureCategory.TEMPERATE, TemperatureCategory.TROPICAL));
		map.put("croplite:attached_cantelope_stem", EnumSet.of(TemperatureCategory.TEMPERATE, TemperatureCategory.TROPICAL));
		map.put("croplite:coffee_crop", EnumSet.of(TemperatureCategory.TROPICAL));
		map.put("croplite:garlic_crop", EnumSet.of(TemperatureCategory.COLD, TemperatureCategory.TEMPERATE));
		map.put("croplite:peach_sapling", EnumSet.of(TemperatureCategory.COLD, TemperatureCategory.TEMPERATE));
		map.put("croplite:lemon_sapling", EnumSet.of(TemperatureCategory.TEMPERATE, TemperatureCategory.TROPICAL));
		map.put("croplite:banana_sapling", EnumSet.of(TemperatureCategory.TROPICAL));
		map.put("croplite:basil_crop", EnumSet.of(TemperatureCategory.TEMPERATE, TemperatureCategory.TROPICAL));
		return map;
	}

	private static Map<String, Set<TemperatureCategory>> readCropClimates(JsonObject object) {
		Map<String, Set<TemperatureCategory>> map = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			EnumSet<TemperatureCategory> set = EnumSet.noneOf(TemperatureCategory.class);
			if (entry.getValue().isJsonArray()) {
				for (JsonElement element : entry.getValue().getAsJsonArray()) {
					TemperatureCategory category = TemperatureCategory.CODEC.byName(element.getAsString());
					if (category != null) {
						set.add(category);
					}
				}
			}
			if (!set.isEmpty()) {
				map.put(entry.getKey(), set);
			}
		}
		return map.isEmpty() ? defaultCropClimates() : map;
	}

	private static JsonObject writeCropClimates(Map<String, Set<TemperatureCategory>> climates) {
		JsonObject object = new JsonObject();
		for (Map.Entry<String, Set<TemperatureCategory>> entry : climates.entrySet()) {
			JsonArray array = new JsonArray();
			for (TemperatureCategory category : entry.getValue()) {
				array.add(category.getSerializedName());
			}
			object.add(entry.getKey(), array);
		}
		return object;
	}

	private static int getInt(JsonObject json, String key, int fallback) {
		return json.has(key) ? json.get(key).getAsInt() : fallback;
	}

	private static float getFloat(JsonObject json, String key, float fallback) {
		return json.has(key) ? json.get(key).getAsFloat() : fallback;
	}

	/**
	 * Prefer {@code chunksKey} (1-in-N). If missing, migrate legacy percent keys
	 * (only when they look user-tuned — not the old ultra-rare defaults).
	 */
	private static int readChunksPerSpawn(JsonObject json, String chunksKey, String legacyPercentKey, int fallback) {
		if (json.has(chunksKey)) {
			return Math.max(0, json.get(chunksKey).getAsInt());
		}
		if (json.has(legacyPercentKey)) {
			double percent = json.get(legacyPercentKey).getAsDouble();
			if (percent <= 0.0) {
				return 0;
			}
			// Old defaults (0.033 / 0.037 / 0.58) were never the intended play feel — use fallback.
			if (percent < 1.0) {
				return fallback;
			}
			return Math.max(1, (int) Math.round(100.0 / percent));
		}
		return fallback;
	}
}
