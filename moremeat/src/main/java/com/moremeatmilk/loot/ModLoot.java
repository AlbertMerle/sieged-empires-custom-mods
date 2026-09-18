package com.moremeatmilk.loot;

import com.moremeatmilk.Moremeatmilk;
import com.moremeatmilk.item.ModItems;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableSource;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.EnchantedCountIncreaseFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ModLoot {

	private record DropSpec(Item item, float min, float max) {
	}

	private static final Map<ResourceKey<LootTable>, List<DropSpec>> DROPS = new HashMap<>();

	static {
		// Alex's Mobs
		alex("blue_jay", ModItems.RAW_FOWL, 0, 1);
		alex("crow", ModItems.RAW_FOWL, 0, 1);
		alex("crocodile", ModItems.RAW_GATOR, 1, 3);
		alex("caiman", ModItems.RAW_GATOR, 1, 3);
		alex("emu", ModItems.RAW_FOWL, 2, 3);
		alex("grizzly_bear", ModItems.RAW_BEAR_MEAT, 2, 4);
		alex("raccoon", ModItems.RAW_RACCOON, 1, 2);
		alex("rattlesnake", ModItems.RAW_SNAKE, 0, 1);
		alex("anaconda", ModItems.RAW_SNAKE, 1, 6);
		alex("anteater", ModItems.RAW_ANTEATER, 1, 3);
		alex("capuchin_monkey", ModItems.RAW_MONKEY, 0, 2);
		alex("frilled_shark", ModItems.RAW_SHARK, 1, 3);
		alex("gelada_monkey", ModItems.RAW_MONKEY, 1, 4);
		alex("gorilla", ModItems.RAW_MONKEY, 3, 6);
		alex("hammerhead_shark", ModItems.RAW_SHARK, 3, 6);
		alex("hummingbird", ModItems.RAW_FOWL, 0, 1);
		alex("komodo_dragon", ModItems.RAW_SNAKE, 2, 4);
		alex("maned_wolf", ModItems.RAW_WOLF_MEAT, 1, 3);
		alex("orca", Items.COD, 4, 6);
		alex("rain_frog", ModItems.RAW_FROG_LEG, 1, 2);
		alex("seal", Items.COD, 2, 4);
		alex("skunk", Items.ROTTEN_FLESH, 1, 2);
		alex("snow_leopard", ModItems.RAW_FELINE, 1, 3);
		alex("tiger", ModItems.RAW_FELINE, 2, 4);

		// Vanilla mobs
		vanilla(EntityTypes.DONKEY, Items.BEEF, 1, 3);
		vanilla(EntityTypes.HORSE, Items.BEEF, 1, 3);
		vanilla(EntityTypes.MULE, Items.BEEF, 1, 3);
		vanilla(EntityTypes.LLAMA, Items.MUTTON, 1, 3);
		vanilla(EntityTypes.LLAMA, Items.WOOL.white(), 0, 3);
		vanilla(EntityTypes.TRADER_LLAMA, Items.MUTTON, 1, 3);
		vanilla(EntityTypes.TRADER_LLAMA, Items.WOOL.white(), 0, 3);
		vanilla(EntityTypes.PANDA, ModItems.RAW_PANDA_MEAT, 1, 3);
		vanilla(EntityTypes.PARROT, ModItems.RAW_FOWL, 0, 1);
		vanilla(EntityTypes.BAT, ModItems.RAW_FOWL, 0, 1);
		vanilla(EntityTypes.CAMEL, Items.MUTTON, 2, 4);
		vanilla(EntityTypes.CAMEL, Items.WOOL.white(), 0, 3);
		vanilla(EntityTypes.FOX, ModItems.RAW_FOX_MEAT, 1, 2);
		vanilla(EntityTypes.GOAT, Items.MUTTON, 1, 3);
		vanilla(EntityTypes.GOAT, Items.WOOL.white(), 0, 2);
		vanilla(EntityTypes.WOLF, ModItems.RAW_WOLF_MEAT, 1, 2);
		vanilla(EntityTypes.FROG, ModItems.RAW_FROG_LEG, 1, 2);
		vanilla(EntityTypes.SQUID, Items.COD, 1, 3);
		vanilla(EntityTypes.GLOW_SQUID, Items.COD, 1, 3);
	}

	private static void alex(String entity, Item item, float min, float max) {
		addDrop(
			ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("alexsmobs", "entities/" + entity)),
			item,
			min,
			max
		);
	}

	private static void vanilla(EntityType<?> type, Item item, float min, float max) {
		type.getDefaultLootTable().ifPresent(key -> addDrop(key, item, min, max));
	}

	private static void addDrop(ResourceKey<LootTable> key, Item item, float min, float max) {
		DROPS.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new DropSpec(item, min, max));
	}

	public static void initialize() {
		LootTableEvents.MODIFY.register(ModLoot::modifyLootTable);
		Moremeatmilk.LOGGER.info("Registered meat loot table modifications for {} entities", DROPS.size());
	}

	private static void modifyLootTable(
		ResourceKey<LootTable> key,
		net.minecraft.world.level.storage.loot.LootTable.Builder tableBuilder,
		net.fabricmc.fabric.api.loot.v3.LootTableSource source,
		HolderLookup.Provider registries
	) {
		// Vanilla entity tables are VANILLA; Alex's Mobs (and other mod) tables are MOD.
		if (source != LootTableSource.VANILLA && source != LootTableSource.MOD) {
			return;
		}

		List<DropSpec> drops = DROPS.get(key);
		if (drops == null) {
			return;
		}

		for (DropSpec drop : drops) {
			tableBuilder.withPool(meatPool(registries, drop));
		}
	}

	private static LootPool.Builder meatPool(HolderLookup.Provider registries, DropSpec drop) {
		return LootPool.lootPool()
			.setRolls(ConstantValue.exactly(1))
			.add(LootItem.lootTableItem(drop.item())
				.apply(SetItemCountFunction.setCount(UniformGenerator.between(drop.min(), drop.max())))
				.apply(EnchantedCountIncreaseFunction.lootingMultiplier(registries, UniformGenerator.between(0, 1)))
			);
	}

}
