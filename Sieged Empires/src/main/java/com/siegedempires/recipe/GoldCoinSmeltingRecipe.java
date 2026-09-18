package com.siegedempires.recipe;

import com.mojang.serialization.MapCodec;
import com.siegedempires.item.ModItems;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;

public class GoldCoinSmeltingRecipe extends SmeltingRecipe {
	public static final GoldCoinSmeltingRecipe INSTANCE = new GoldCoinSmeltingRecipe();

	public static final MapCodec<GoldCoinSmeltingRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
	public static final StreamCodec<RegistryFriendlyByteBuf, GoldCoinSmeltingRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);
	public static final RecipeSerializer<GoldCoinSmeltingRecipe> SERIALIZER =
			new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

	private GoldCoinSmeltingRecipe() {
		super(
				new Recipe.CommonInfo(true),
				new AbstractCookingRecipe.CookingBookInfo(CookingBookCategory.MISC, "gold_coin"),
				Ingredient.of(ModItems.GOLD_COIN),
				new ItemStackTemplate(Items.GOLD_INGOT),
				0.1f,
				GoldCoinSmelting.SMELTING_TIME
		);
	}

	@Override
	public boolean matches(SingleRecipeInput input, Level level) {
		return GoldCoinSmelting.hasEnoughCoins(input.item());
	}

	@Override
	@SuppressWarnings("unchecked")
	public RecipeSerializer<SmeltingRecipe> getSerializer() {
		return (RecipeSerializer<SmeltingRecipe>) (RecipeSerializer<?>) SERIALIZER;
	}
}
