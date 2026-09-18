package com.siegedempires.recipe;

import com.mojang.serialization.MapCodec;
import com.siegedempires.item.ModItems;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

public class GoldCoinBlastingRecipe extends BlastingRecipe {
	public static final GoldCoinBlastingRecipe INSTANCE = new GoldCoinBlastingRecipe();

	public static final MapCodec<GoldCoinBlastingRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
	public static final StreamCodec<RegistryFriendlyByteBuf, GoldCoinBlastingRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);
	public static final RecipeSerializer<GoldCoinBlastingRecipe> SERIALIZER =
			new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

	private GoldCoinBlastingRecipe() {
		super(
				new Recipe.CommonInfo(true),
				new AbstractCookingRecipe.CookingBookInfo(CookingBookCategory.MISC, "gold_coin"),
				Ingredient.of(ModItems.GOLD_COIN),
				new ItemStackTemplate(Items.GOLD_INGOT),
				1.0f,
				GoldCoinSmelting.BLASTING_TIME
		);
	}

	@Override
	public boolean matches(SingleRecipeInput input, Level level) {
		return GoldCoinSmelting.hasEnoughCoins(input.item());
	}

	@Override
	@SuppressWarnings("unchecked")
	public RecipeSerializer<BlastingRecipe> getSerializer() {
		return (RecipeSerializer<BlastingRecipe>) (RecipeSerializer<?>) SERIALIZER;
	}
}
