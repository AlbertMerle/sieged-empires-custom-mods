package com.weaponsmodaddon.mixin;

import ckathode.weaponmod.item.ItemJavelin;
import com.weaponsmodaddon.spear.SpearChargeThrow;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Javelin natively uses {@link ItemUseAnimation#BOW}; replace with NONE so addon
 * Blockbench spear clips are the only player pose while charging/throwing.
 * Charge only while standing or sneaking.
 */
@Mixin(ItemJavelin.class)
public abstract class ItemJavelinMixin {
	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void weaponsmodaddon$javelinChargeOnlyStandOrSneak(
			Level level,
			Player player,
			InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir
	) {
		if (!SpearChargeThrow.canCharge(player)) {
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}

	@Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
	private void weaponsmodaddon$javelinNoVanillaUsePose(ItemStack stack, CallbackInfoReturnable<ItemUseAnimation> cir) {
		cir.setReturnValue(ItemUseAnimation.NONE);
	}
}
