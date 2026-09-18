package com.croplite.worldgen;

import com.croplite.CropLite;
import com.croplite.config.CropLiteConfig;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.function.Predicate;

/**
 * Natural worldgen: cantelope legacy patch, sugar cane climate trim, fruit trees,
 * and rare wild crop groups.
 *
 * <p>Default wild-crop spawn: 20% chance of one group per chunk, 5% chance of a second group.
 * Cold (temp &lt; −0.5) and savanna: 30% of those rates. Desert/badlands: 10% of those rates.
 *
 * <p>Wild crop patches run in {@link GenerationStep.Decoration#TOP_LAYER_MODIFICATION}
 * so they can replace grass / fern / tall grass / flowers after vegetal decoration.
 *
 * <p>Fruit trees: rare (default 1-in-30 chunks via config), climate-gated, never plains.
 * Registration always strips first ({@link ModificationPhase#REMOVALS}) then re-adds only
 * when {@code natural_fruit_tree_generation} is true — prevents leftover double-adds.
 */
public final class ModWorldgen {
	private ModWorldgen() {
	}

	public static final ResourceKey<PlacedFeature> PATCH_CANTELOPE = placed("patch_cantelope");

	private static final ResourceKey<PlacedFeature> PEACH_TREE = placed("peach_tree");
	private static final ResourceKey<PlacedFeature> LEMON_TREE = placed("lemon_tree");
	private static final ResourceKey<PlacedFeature> BANANA_TREE = placed("banana_tree");

	private static final ResourceKey<PlacedFeature> PATCH_SUGAR_CANE = vanillaPlaced("patch_sugar_cane");
	private static final ResourceKey<PlacedFeature> PATCH_SUGAR_CANE_SWAMP = vanillaPlaced("patch_sugar_cane_swamp");
	private static final ResourceKey<PlacedFeature> PATCH_SUGAR_CANE_DESERT = vanillaPlaced("patch_sugar_cane_desert");
	private static final ResourceKey<PlacedFeature> PATCH_SUGAR_CANE_BADLANDS = vanillaPlaced("patch_sugar_cane_badlands");

	public static void initialize() {
		ModPlacementModifiers.initialize();
		ModFeatures.initialize();

		BiomeModifications.addFeature(
				ctx -> BiomeSpawnCategory.isSavanna(ctx.getBiomeHolder()),
				GenerationStep.Decoration.VEGETAL_DECORATION,
				PATCH_CANTELOPE);

		configureSugarCane();

		// Always strip first so old jars / double-adds cannot leave dense trees behind.
		stripFruitTreesFromAllBiomes();
		if (CropLiteConfig.get().naturalFruitTreeGeneration) {
			registerFruitTrees();
		} else {
			CropLite.LOGGER.info("CropLite fruit trees: natural spawn disabled (peach/lemon/banana)");
		}

		if (!CropLiteConfig.get().naturalCropGeneration) {
			CropLite.LOGGER.info("CropLite natural crop generation disabled by config");
			return;
		}

		registerWildCrops();
	}

	/**
	 * Peach / lemon / banana: one selector each (never double-add). Density comes only from
	 * {@code croplite:config_chunk_rarity} reading {@code *_tree_chunks_per_spawn}.
	 */
	private static void registerFruitTrees() {
		GenerationStep.Decoration treeStep = GenerationStep.Decoration.VEGETAL_DECORATION;
		CropLiteConfig config = CropLiteConfig.get();

		Predicate<BiomeSelectionContext> peach = ctx ->
				BiomeSpawnCategory.allowsPeachTree(ctx.getBiomeHolder(), config);
		Predicate<BiomeSelectionContext> lemon = ctx ->
				BiomeSpawnCategory.allowsLemonTree(ctx.getBiomeHolder(), config);
		Predicate<BiomeSelectionContext> banana = ctx ->
				BiomeSpawnCategory.allowsBananaTree(ctx.getBiomeHolder(), config);

		add(peach, treeStep, "peach_tree");
		add(lemon, treeStep, "lemon_tree");
		add(banana, treeStep, "banana_tree");

		CropLite.LOGGER.info(
				"CropLite fruit trees: enabled — peach/lemon/banana 1-in-{} / 1-in-{} / 1-in-{} chunks "
						+ "(peach/lemon: temp < {}, no plains; banana: jungle + temp > {})",
				config.peachTreeChunksPerSpawn,
				config.lemonTreeChunksPerSpawn,
				config.bananaTreeChunksPerSpawn,
				config.temperateCropMaxTemperature,
				config.temperateCropMaxTemperature);
	}

	private static void registerWildCrops() {
		Predicate<BiomeSelectionContext> defaultRate = ctx -> {
			if (BiomeSpawnCategory.isDesert(ctx.getBiomeHolder())
					|| BiomeSpawnCategory.isSavanna(ctx.getBiomeHolder())) {
				return false;
			}
			float temp = ctx.getBiome().getBaseTemperature();
			if (temp < -0.5F) {
				return false;
			}
			return BiomeSpawnCategory.isTropicalWet(ctx.getBiomeHolder())
					|| BiomeSpawnCategory.isTemperateSpawn(ctx.getBiomeHolder())
					|| BiomeSpawnCategory.isColdSpawn(ctx.getBiomeHolder());
		};

		Predicate<BiomeSelectionContext> reducedRate = ctx -> {
			if (BiomeSpawnCategory.isDesert(ctx.getBiomeHolder())) {
				return false;
			}
			if (BiomeSpawnCategory.isSavanna(ctx.getBiomeHolder())) {
				return true;
			}
			float temp = ctx.getBiome().getBaseTemperature();
			return temp < -0.5F && BiomeSpawnCategory.isColdSpawn(ctx.getBiomeHolder());
		};

		Predicate<BiomeSelectionContext> aridRate = ctx -> BiomeSpawnCategory.isDesert(ctx.getBiomeHolder());

		// After grass/flowers so patch rarity is not eaten by dense flora.
		GenerationStep.Decoration cropStep = GenerationStep.Decoration.TOP_LAYER_MODIFICATION;

		// Default: 20% one group, 5% second group (rarity chance = 1/N).
		add(defaultRate, cropStep, "patch_wild_crops");
		add(defaultRate, cropStep, "patch_wild_crops_second");

		// Savanna + very cold: 30% of default → ~6% and ~1.5%.
		add(reducedRate, cropStep, "patch_wild_crops_reduced");
		add(reducedRate, cropStep, "patch_wild_crops_reduced_second");

		// Desert / badlands: 10% of default → 2% and 0.5%.
		add(aridRate, cropStep, "patch_wild_crops_arid");
		add(aridRate, cropStep, "patch_wild_crops_arid_second");

		if (FabricLoader.getInstance().isModLoaded("terralith")) {
			CropLite.LOGGER.info(
					"CropLite wild crops: Terralith detected — modded biomes use temp+humidity pools "
							+ "(desert ≤{} humidity @ ≥{}°; jungle/wet ≥{} humidity)",
					BiomeSpawnCategory.ARID_MAX_HUMIDITY,
					BiomeSpawnCategory.DESERT_CLIMATE_MIN_TEMPERATURE,
					BiomeSpawnCategory.WET_MIN_HUMIDITY);
		}
		CropLite.LOGGER.info("CropLite wild crop spawn categories registered");
	}

	/**
	 * Strip peach/lemon/banana placed features from every biome before (optional) re-add.
	 * Sapling growth still uses configured features directly (not these placed features).
	 */
	private static void stripFruitTreesFromAllBiomes() {
		BiomeModifications.create(CropLite.id("disable_fruit_trees"))
				.add(ModificationPhase.REMOVALS, context -> true, context -> {
					var generation = context.getGenerationSettings();
					generation.removeFeature(PEACH_TREE);
					generation.removeFeature(LEMON_TREE);
					generation.removeFeature(BANANA_TREE);
				});
	}

	/** Sugar cane: keep vanilla features only in tropical wet / savanna / desert. */
	private static void configureSugarCane() {
		BiomeModifications.create(CropLite.id("restrict_sugar_cane"))
				.add(ModificationPhase.REMOVALS, context -> {
					if (!CropLiteConfig.get().temperatureChecks) {
						return false;
					}
					return !BiomeSpawnCategory.allowsSugarCane(context.getBiomeHolder());
				}, context -> {
					var generation = context.getGenerationSettings();
					generation.removeFeature(PATCH_SUGAR_CANE);
					generation.removeFeature(PATCH_SUGAR_CANE_SWAMP);
					generation.removeFeature(PATCH_SUGAR_CANE_DESERT);
					generation.removeFeature(PATCH_SUGAR_CANE_BADLANDS);
				});
	}

	private static void add(
			Predicate<BiomeSelectionContext> selector,
			GenerationStep.Decoration step,
			String placedPath) {
		BiomeModifications.addFeature(selector, step, placed(placedPath));
	}

	private static ResourceKey<PlacedFeature> placed(String path) {
		return ResourceKey.create(Registries.PLACED_FEATURE, CropLite.id(path));
	}

	private static ResourceKey<PlacedFeature> vanillaPlaced(String path) {
		return ResourceKey.create(Registries.PLACED_FEATURE, Identifier.withDefaultNamespace(path));
	}
}
