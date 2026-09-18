package com.croplite.item;

import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.Items;

/**
 * Tweaks default item components after vanilla registration.
 * Melon slices stay edible when the hunger bar is full so Tough as Nails can restore thirst/hydration.
 */
public final class ModItemComponents {
	private ModItemComponents() {
	}

	public static void initialize() {
		DefaultItemComponentEvents.MODIFY.register(context -> context.modify(Items.MELON_SLICE, builder -> {
			FoodProperties melon = Foods.MELON_SLICE;
			builder.set(DataComponents.FOOD, new FoodProperties(melon.nutrition(), melon.saturation(), true));
		}));
	}
}
