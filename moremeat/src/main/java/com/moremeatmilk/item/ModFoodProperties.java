package com.moremeatmilk.item;

import net.minecraft.world.food.FoodProperties;

public final class ModFoodProperties {

	// Matches raw/cooked cod (fish)
	public static final FoodProperties FISH_LIKE_RAW = new FoodProperties.Builder()
		.nutrition(2)
		.saturationModifier(0.2F)
		.build();
	public static final FoodProperties FISH_LIKE_COOKED = new FoodProperties.Builder()
		.nutrition(5)
		.saturationModifier(0.6F)
		.build();

	// Matches beef-style meats
	public static final FoodProperties MEAT_RAW = new FoodProperties.Builder()
		.nutrition(3)
		.saturationModifier(0.3F)
		.build();
	public static final FoodProperties MEAT_COOKED = new FoodProperties.Builder()
		.nutrition(8)
		.saturationModifier(0.8F)
		.build();

	// Matches chicken / frog legs
	public static final FoodProperties POULTRY_RAW = new FoodProperties.Builder()
		.nutrition(2)
		.saturationModifier(0.3F)
		.build();
	public static final FoodProperties POULTRY_COOKED = new FoodProperties.Builder()
		.nutrition(6)
		.saturationModifier(0.6F)
		.build();

	private ModFoodProperties() {
	}

}
