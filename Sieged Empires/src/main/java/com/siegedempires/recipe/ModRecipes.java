package com.siegedempires.recipe;

import com.siegedempires.Siegedempires;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeSerializer;

public final class ModRecipes {
	private ModRecipes() {
	}

	public static void initialize() {
		Registry.register(
				BuiltInRegistries.RECIPE_SERIALIZER,
				Siegedempires.id("gold_coin_minting"),
				GoldCoinMintingRecipe.SERIALIZER
		);
		Registry.register(
				BuiltInRegistries.RECIPE_SERIALIZER,
				Siegedempires.id("gold_coin_smelting"),
				GoldCoinSmeltingRecipe.SERIALIZER
		);
		Registry.register(
				BuiltInRegistries.RECIPE_SERIALIZER,
				Siegedempires.id("gold_coin_blasting"),
				GoldCoinBlastingRecipe.SERIALIZER
		);
		Siegedempires.LOGGER.info("Registered custom recipes");
	}
}
