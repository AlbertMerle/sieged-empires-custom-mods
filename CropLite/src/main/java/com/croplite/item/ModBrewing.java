package com.croplite.item;

import net.fabricmc.fabric.api.registry.FabricPotionBrewingBuilder;

import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Coffee beans → Speed I (3 min) potions; garlic → Strength potions.
 * Matches vanilla sugar / blaze powder start mixes.
 */
public final class ModBrewing {
	private ModBrewing() {
	}

	public static void initialize() {
		FabricPotionBrewingBuilder.BUILD.register(builder -> {
			builder.registerRecipes(Ingredient.of(ModItems.COFFEE_BEAN), Potions.SWIFTNESS);
			builder.registerRecipes(Ingredient.of(ModItems.GARLIC), Potions.STRENGTH);
		});
	}
}
