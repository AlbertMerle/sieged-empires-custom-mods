package com.croplite.loot;

import java.util.Set;

import com.croplite.CropLite;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * Injects biome-temperature grain drops into grass/fern loot, and CropLite seeds into structure chests.
 * Vanilla wheat seeds are stripped from grass/fern tables via datapack overrides.
 */
public final class ModLoot {
	private ModLoot() {
	}

	public static final ResourceKey<LootTable> GRASS_SEEDS = key("inject/grass_seeds");
	public static final ResourceKey<LootTable> STRUCTURE_SEEDS = key("inject/structure_seeds");

	private static final Set<ResourceKey<LootTable>> STRUCTURE_CHESTS = Set.of(
			BuiltInLootTables.SIMPLE_DUNGEON,
			BuiltInLootTables.ABANDONED_MINESHAFT,
			BuiltInLootTables.WOODLAND_MANSION,
			BuiltInLootTables.SHIPWRECK_SUPPLY,
			BuiltInLootTables.SHIPWRECK_TREASURE,
			BuiltInLootTables.BURIED_TREASURE,
			BuiltInLootTables.UNDERWATER_RUIN_SMALL,
			BuiltInLootTables.UNDERWATER_RUIN_BIG,
			BuiltInLootTables.JUNGLE_TEMPLE,
			BuiltInLootTables.DESERT_PYRAMID,
			BuiltInLootTables.STRONGHOLD_CORRIDOR,
			BuiltInLootTables.STRONGHOLD_CROSSING,
			BuiltInLootTables.PILLAGER_OUTPOST,
			BuiltInLootTables.IGLOO_CHEST,
			BuiltInLootTables.ANCIENT_CITY,
			BuiltInLootTables.TRIAL_CHAMBERS_SUPPLY,
			BuiltInLootTables.TRIAL_CHAMBERS_CORRIDOR,
			BuiltInLootTables.END_CITY_TREASURE,
			BuiltInLootTables.SPAWN_BONUS_CHEST,
			BuiltInLootTables.VILLAGE_PLAINS_HOUSE,
			BuiltInLootTables.VILLAGE_SAVANNA_HOUSE,
			BuiltInLootTables.VILLAGE_SNOWY_HOUSE,
			BuiltInLootTables.VILLAGE_TAIGA_HOUSE,
			BuiltInLootTables.VILLAGE_DESERT_HOUSE,
			BuiltInLootTables.VILLAGE_FISHER
	);

	public static void initialize() {
		TemperatureCategoryCondition.register();

		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			if (!source.isBuiltin()) {
				return;
			}

			if (isBlockLoot(key, Blocks.SHORT_GRASS)
					|| isBlockLoot(key, Blocks.TALL_GRASS)
					|| isBlockLoot(key, Blocks.FERN)
					|| isBlockLoot(key, Blocks.LARGE_FERN)) {
				tableBuilder.pool(injectPool(GRASS_SEEDS));
				return;
			}

			if (STRUCTURE_CHESTS.contains(key)) {
				tableBuilder.pool(injectPool(STRUCTURE_SEEDS));
			}
		});
	}

	private static LootPool injectPool(ResourceKey<LootTable> injectTable) {
		return LootPool.lootPool()
				.setRolls(ConstantValue.exactly(1.0F))
				.add(NestedLootTable.lootTableReference(injectTable))
				.build();
	}

	private static boolean isBlockLoot(ResourceKey<LootTable> key, net.minecraft.world.level.block.Block block) {
		return block.getLootTable().filter(key::equals).isPresent();
	}

	private static ResourceKey<LootTable> key(String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, CropLite.id(path));
	}
}
