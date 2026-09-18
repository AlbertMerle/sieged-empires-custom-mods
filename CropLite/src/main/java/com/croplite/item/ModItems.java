package com.croplite.item;

import com.croplite.CropLite;
import com.croplite.block.ModBlocks;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.level.block.Block;

import java.util.function.Function;

public final class ModItems {
	private ModItems() {
	}

	public static final Item HAY = register("hay", new Item.Properties());

	public static final Item TOMATO = register("tomato", new Item.Properties().food(food(4, 0.4F)));
	public static final Item PEPPER = register("pepper", new Item.Properties().food(food(4, 0.4F)));
	public static final Item EGGPLANT = register("eggplant", new Item.Properties().food(food(4, 0.4F)));
	public static final Item CUCUMBER = register("cucumber", new Item.Properties().food(alwaysEdibleFood(2, 0.3F)));

	public static final Item TOMATO_SEEDS = registerSeeds("tomato_seeds", ModBlocks.TOMATO_CROP);
	public static final Item PEPPER_SEEDS = registerSeeds("pepper_seeds", ModBlocks.PEPPER_CROP);
	public static final Item EGGPLANT_SEEDS = registerSeeds("eggplant_seeds", ModBlocks.EGGPLANT_CROP);
	public static final Item CUCUMBER_SEEDS = registerSeeds("cucumber_seeds", ModBlocks.CUCUMBER_CROP);
	public static final Item CANTELOPE_SEEDS = registerSeeds("cantelope_seeds", ModBlocks.CANTELOPE_STEM);
	public static final Item BASIL_SEEDS = registerSeeds("basil_seeds", ModBlocks.BASIL_CROP);

	/** Plantable foods: the food item itself is the seed, like vanilla potatoes. */
	public static final Item SWEET_POTATO = registerPlantableFood("sweet_potato", ModBlocks.SWEET_POTATO_CROP, food(1, 0.3F));
	public static final Item OATS = registerPlantableFood("oats", ModBlocks.OATS_CROP, food(2, 0.2F));
	public static final Item BEANS = registerPlantableFood("beans", ModBlocks.BEANS_CROP, food(2, 0.2F));
	public static final Item RICE = registerPlantableFood("rice", ModBlocks.RICE_CROP, food(2, 0.2F));

	/**
	 * Coffee bean: plantable on farmland. Eat: Speed I 1 min, Poison I 3 sec,
	 * TAN / Homeostatic dehydration I for 30 sec when those mods are present.
	 */
	public static final Item COFFEE_BEAN = registerPlantableFood("coffee_bean", ModBlocks.COFFEE_CROP,
			food(2, 0.1F),
			Consumables.defaultFood()
					.onConsume(new ApplyStatusEffectsConsumeEffect(java.util.List.of(
							new MobEffectInstance(MobEffects.SPEED, 1200, 0),
							new MobEffectInstance(MobEffects.POISON, 60, 0))))
					.onConsume(OptionalModEffectConsumeEffect.tanDehydration(600))
					.onConsume(OptionalModEffectConsumeEffect.homeostaticThirst(600))
					.build());

	/** Roasted coffee: Speed I 3 min, TAN/Homeostatic dehydration I 30 sec, no poison. */
	public static final Item ROASTED_COFFEE_BEAN = register("roasted_coffee_bean",
			new Item.Properties().food(
					food(2, 0.2F),
					Consumables.defaultFood()
							.onConsume(new ApplyStatusEffectsConsumeEffect(
									new MobEffectInstance(MobEffects.SPEED, 3600, 0)))
							.onConsume(OptionalModEffectConsumeEffect.tanDehydration(600))
							.onConsume(OptionalModEffectConsumeEffect.homeostaticThirst(600))
							.build()));

	/** Water bottle + 8 coffee beans. Speed II for 3 minutes. */
	public static final Item BOTTLE_OF_COFFEE = register("bottle_of_coffee", new Item.Properties()
			.food(alwaysEdibleFood(2, 0.2F), Consumables.defaultDrink()
					.onConsume(new ApplyStatusEffectsConsumeEffect(
							new MobEffectInstance(MobEffects.SPEED, 3600, 1)))
					.build())
			.usingConvertsTo(Items.GLASS_BOTTLE)
			.stacksTo(16));

	/** Dropped 2–4 from cactus; craft 4 back into a cactus; 1 hunger + 2 TAN thirst; edible while full. */
	public static final Item CACTUS_FLESH = register("cactus_flesh",
			new Item.Properties().food(alwaysEdibleFood(1, 0.1F)));

	/**
	 * Plantable garlic bulb. Eat while full: 1 HP damage, Strength I 10s, Regen I 2s.
	 * Grows at 30% speed; mature break yields 2–3 bulbs.
	 */
	public static final Item GARLIC = registerPlantableFood("garlic", ModBlocks.GARLIC_CROP,
			alwaysEdibleFood(1, 0.1F),
			Consumables.defaultFood()
					.onConsume(GarlicEatConsumeEffect.INSTANCE)
					.build());

	public static final Item BASIL = register("basil",
			new Item.Properties()
					.food(food(1, 0.1F), Consumables.defaultFood()
							.onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0)))
							.build()));

	public static final Item BAKED_SWEET_POTATO = register("baked_sweet_potato", new Item.Properties().food(food(5, 0.6F)));

	public static final Item CANTELOPE_SLICE = register("cantelope_slice", new Item.Properties().food(alwaysEdibleFood(2, 0.3F)));

	public static final Item PEACH = register("peach", new Item.Properties().food(food(4, 0.3F)));
	public static final Item LEMON = register("lemon", new Item.Properties().food(food(2, 0.2F)));
	public static final Item BANANA = register("banana", new Item.Properties().food(alwaysEdibleFood(4, 0.3F)));

	public static final Item LEMONADE = register("lemonade", new Item.Properties()
			.food(food(2, 0.3F), Consumables.DEFAULT_DRINK)
			.usingConvertsTo(Items.GLASS_BOTTLE)
			.stacksTo(16));

	public static final Item OATMEAL = register("oatmeal", new Item.Properties()
			.food(food(6, 0.6F))
			.usingConvertsTo(Items.BOWL)
			.stacksTo(16));

	public static final Item RICE_AND_BEANS = register("rice_and_beans", new Item.Properties()
			.food(food(8, 0.8F))
			.usingConvertsTo(Items.BOWL)
			.stacksTo(16));

	public static final Item CANTELOPE = registerBlockItem("cantelope", ModBlocks.CANTELOPE);
	public static final Item PEACH_LEAVES = registerBlockItem("peach_leaves", ModBlocks.PEACH_LEAVES);
	public static final Item LEMON_LEAVES = registerBlockItem("lemon_leaves", ModBlocks.LEMON_LEAVES);
	public static final Item BANANA_LEAVES = registerBlockItem("banana_leaves", ModBlocks.BANANA_LEAVES);
	public static final Item BANANA_STALK = registerBlockItem("banana_stalk", ModBlocks.BANANA_STALK);
	public static final Item PEACH_SAPLING = registerBlockItem("peach_sapling", ModBlocks.PEACH_SAPLING);
	public static final Item LEMON_SAPLING = registerBlockItem("lemon_sapling", ModBlocks.LEMON_SAPLING);
	public static final Item BANANA_SAPLING = registerBlockItem("banana_sapling", ModBlocks.BANANA_SAPLING);

	private static FoodProperties food(int nutrition, float saturationModifier) {
		return new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturationModifier).build();
	}

	/** Juicy produce that can be eaten while full (for Tough as Nails thirst/hydration). */
	private static FoodProperties alwaysEdibleFood(int nutrition, float saturationModifier) {
		return new FoodProperties.Builder()
				.nutrition(nutrition)
				.saturationModifier(saturationModifier)
				.alwaysEdible()
				.build();
	}

	private static Item registerSeeds(String name, Block crop) {
		return register(name, props -> new BlockItem(crop, props), new Item.Properties().useItemDescriptionPrefix());
	}

	private static Item registerPlantableFood(String name, Block crop, FoodProperties food) {
		return register(name, props -> new BlockItem(crop, props),
				new Item.Properties().useItemDescriptionPrefix().food(food));
	}

	private static Item registerPlantableFood(
			String name, Block crop, FoodProperties food, net.minecraft.world.item.component.Consumable consumable) {
		return register(name, props -> new BlockItem(crop, props),
				new Item.Properties().useItemDescriptionPrefix().food(food, consumable));
	}

	private static Item registerBlockItem(String name, Block block) {
		return register(name, props -> new BlockItem(block, props), new Item.Properties().useBlockDescriptionPrefix());
	}

	public static Item register(String name, Item.Properties properties) {
		return register(name, Item::new, properties);
	}

	public static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, CropLite.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	public static void initialize() {
	}
}
