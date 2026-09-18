package com.siegedempires.mixin;

import com.siegedempires.recipe.GoldCoinSmelting;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Furnace smelting always consumes one input item; gold coins melt in batches of sixteen.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin {

	@Inject(method = "canBurn", at = @At("HEAD"), cancellable = true)
	private static void siegedempires$requireEnoughGoldCoins(
			NonNullList<ItemStack> items,
			int maxStackSize,
			ItemStack result,
			CallbackInfoReturnable<Boolean> cir) {
		ItemStack input = items.get(0);
		if (GoldCoinSmelting.isGoldCoin(input) && !GoldCoinSmelting.hasEnoughCoins(input)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "burn", at = @At("HEAD"), cancellable = true)
	private static void siegedempires$abortInsufficientGoldCoins(
			NonNullList<ItemStack> items,
			ItemStack inputItemStack,
			ItemStack result,
			CallbackInfo ci) {
		ItemStack input = items.get(0);
		if (GoldCoinSmelting.isGoldCoin(input) && !GoldCoinSmelting.hasEnoughCoins(input)) {
			ci.cancel();
		}
	}

	@Redirect(
			method = "burn",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V")
	)
	private static void siegedempires$shrinkGoldCoinInput(ItemStack stack, int amount) {
		if (GoldCoinSmelting.hasEnoughCoins(stack)) {
			stack.shrink(GoldCoinSmelting.COINS_PER_INGOT);
			return;
		}
		stack.shrink(amount);
	}
}
