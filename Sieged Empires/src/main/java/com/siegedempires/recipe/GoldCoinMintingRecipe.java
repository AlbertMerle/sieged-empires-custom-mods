package com.siegedempires.recipe;

import com.mojang.serialization.MapCodec;
import com.siegedempires.item.ModItems;
import net.minecraft.core.NonNullList;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.network.codec.StreamCodec;

/**
 * Mint gold coins with any pickaxe + gold ingot(s). The pickaxe is never consumed
 * or damaged. Each ingot yields 16 coins; shift-click crafts once per ingot.
 */
public class GoldCoinMintingRecipe extends CustomRecipe {
	public static final GoldCoinMintingRecipe INSTANCE = new GoldCoinMintingRecipe();

	public static final MapCodec<GoldCoinMintingRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
	public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, GoldCoinMintingRecipe> STREAM_CODEC =
			StreamCodec.unit(INSTANCE);
	public static final RecipeSerializer<GoldCoinMintingRecipe> SERIALIZER =
			new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

	private static final int COINS_PER_INGOT = GoldCoinSmelting.COINS_PER_INGOT;

	private record MintingInput(int pickaxeSlot) {}

	private GoldCoinMintingRecipe() {
	}

	private static MintingInput scan(CraftingInput input) {
		int pickaxeSlot = -1;
		int goldIngots = 0;
		int otherSlots = 0;

		for (int slot = 0; slot < input.size(); slot++) {
			ItemStack stack = input.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.is(ItemTags.PICKAXES)) {
				if (pickaxeSlot >= 0) {
					return null;
				}
				pickaxeSlot = slot;
				continue;
			}
			if (stack.is(Items.GOLD_INGOT)) {
				goldIngots += stack.getCount();
				continue;
			}
			otherSlots++;
		}

		if (pickaxeSlot < 0 || goldIngots < 1 || otherSlots > 0) {
			return null;
		}
		return new MintingInput(pickaxeSlot);
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		return scan(input) != null;
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		if (scan(input) == null) {
			return ItemStack.EMPTY;
		}
		return new ItemStack(ModItems.GOLD_COIN, COINS_PER_INGOT);
	}

	@Override
	public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
		NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
		MintingInput minting = scan(input);
		if (minting == null) {
			return remaining;
		}

		ItemStack pickaxe = input.getItem(minting.pickaxeSlot());
		if (!pickaxe.isEmpty()) {
			remaining.set(minting.pickaxeSlot(), pickaxe.copy());
		}
		return remaining;
	}

	@Override
	public RecipeSerializer<? extends CustomRecipe> getSerializer() {
		return SERIALIZER;
	}
}
