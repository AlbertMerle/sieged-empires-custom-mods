package com.moremeatmilk.item;

import com.moremeatmilk.Moremeatmilk;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public final class ModItems {

	private ModItems() {
	}

	public static final Item RAW_FOWL = registerFood("raw_fowl", ModFoodProperties.FISH_LIKE_RAW);
	public static final Item COOKED_FOWL = registerFood("cooked_fowl", ModFoodProperties.FISH_LIKE_COOKED);
	public static final Item RAW_GATOR = registerFood("raw_gator", ModFoodProperties.FISH_LIKE_RAW);
	public static final Item COOKED_GATOR = registerFood("cooked_gator", ModFoodProperties.FISH_LIKE_COOKED);
	public static final Item RAW_MONKEY = registerFood("raw_monkey", ModFoodProperties.FISH_LIKE_RAW);
	public static final Item COOKED_MONKEY = registerFood("cooked_monkey", ModFoodProperties.FISH_LIKE_COOKED);
	public static final Item RAW_SHARK = registerFood("raw_shark", ModFoodProperties.FISH_LIKE_RAW);
	public static final Item COOKED_SHARK = registerFood("cooked_shark", ModFoodProperties.FISH_LIKE_COOKED);

	public static final Item RAW_SNAKE = registerFood("raw_snake", ModFoodProperties.MEAT_RAW);
	public static final Item COOKED_SNAKE = registerFood("cooked_snake", ModFoodProperties.MEAT_COOKED);
	public static final Item RAW_ANTEATER = registerFood("raw_anteater", ModFoodProperties.MEAT_RAW);
	public static final Item COOKED_ANTEATER = registerFood("cooked_anteater", ModFoodProperties.MEAT_COOKED);
	public static final Item RAW_RACCOON = registerFood("raw_raccoon", ModFoodProperties.MEAT_RAW);
	public static final Item COOKED_RACCOON = registerFood("cooked_raccoon", ModFoodProperties.MEAT_COOKED);
	public static final Item RAW_WOLF_MEAT = registerFood("raw_wolf_meat", ModFoodProperties.MEAT_RAW);
	public static final Item COOKED_WOLF_MEAT = registerFood("cooked_wolf_meat", ModFoodProperties.MEAT_COOKED);
	public static final Item RAW_FELINE = registerFood("raw_feline", ModFoodProperties.MEAT_RAW);
	public static final Item COOKED_FELINE = registerFood("cooked_feline", ModFoodProperties.MEAT_COOKED);
	public static final Item RAW_BEAR_MEAT = registerFood("raw_bear_meat", ModFoodProperties.MEAT_RAW);
	public static final Item COOKED_BEAR_MEAT = registerFood("cooked_bear_meat", ModFoodProperties.MEAT_COOKED);
	public static final Item RAW_PANDA_MEAT = registerFood("raw_panda_meat", ModFoodProperties.MEAT_RAW);
	public static final Item COOKED_PANDA_MEAT = registerFood("cooked_panda_meat", ModFoodProperties.MEAT_COOKED);
	public static final Item RAW_FOX_MEAT = registerFood("raw_fox_meat", ModFoodProperties.MEAT_RAW);
	public static final Item COOKED_FOX_MEAT = registerFood("cooked_fox_meat", ModFoodProperties.MEAT_COOKED);

	public static final Item RAW_FROG_LEG = registerFood("raw_frog_leg", ModFoodProperties.POULTRY_RAW);
	public static final Item COOKED_FROG_LEG = registerFood("cooked_frog_leg", ModFoodProperties.POULTRY_COOKED);
	public static final Item COOKED_EGG = registerFood("cooked_egg", ModFoodProperties.POULTRY_COOKED);

	private static Item registerFood(String name, FoodProperties food) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Moremeatmilk.id(name));
		Item item = new Item(new Item.Properties().setId(key).food(food));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	/** Called from mod initializer to trigger static item registration. */
	public static void register() {
		// Static fields above perform Registry.register; this method forces class init.
	}

	public static void initialize() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			output.accept(RAW_FOWL);
			output.accept(COOKED_FOWL);
			output.accept(RAW_GATOR);
			output.accept(COOKED_GATOR);
			output.accept(RAW_MONKEY);
			output.accept(COOKED_MONKEY);
			output.accept(RAW_SHARK);
			output.accept(COOKED_SHARK);
			output.accept(RAW_SNAKE);
			output.accept(COOKED_SNAKE);
			output.accept(RAW_ANTEATER);
			output.accept(COOKED_ANTEATER);
			output.accept(RAW_RACCOON);
			output.accept(COOKED_RACCOON);
			output.accept(RAW_WOLF_MEAT);
			output.accept(COOKED_WOLF_MEAT);
			output.accept(RAW_FELINE);
			output.accept(COOKED_FELINE);
			output.accept(RAW_BEAR_MEAT);
			output.accept(COOKED_BEAR_MEAT);
			output.accept(RAW_PANDA_MEAT);
			output.accept(COOKED_PANDA_MEAT);
			output.accept(RAW_FOX_MEAT);
			output.accept(COOKED_FOX_MEAT);
			output.accept(RAW_FROG_LEG);
			output.accept(COOKED_FROG_LEG);
			output.accept(COOKED_EGG);
		});
		Moremeatmilk.LOGGER.info("Registered moremeatmilk food items");
	}

}
