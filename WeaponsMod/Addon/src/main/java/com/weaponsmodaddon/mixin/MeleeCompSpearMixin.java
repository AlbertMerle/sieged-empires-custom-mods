package com.weaponsmodaddon.mixin;

import ckathode.weaponmod.item.MeleeCompSpear;
import com.weaponsmodaddon.spear.SpearChargeThrow;
import com.weaponsmodaddon.spear.SpearPendingThrows;
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
 * Replaces WeaponMod instant spear throw with bow-style charge-and-release.
 */
@Mixin(MeleeCompSpear.class)
public abstract class MeleeCompSpearMixin {
	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void weaponsmodaddon$startSpearCharge(
			ItemStack stack,
			Level level,
			Player player,
			InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir
	) {
		if (SpearPendingThrows.hasPending(player) || !SpearChargeThrow.canCharge(player)) {
			cir.setReturnValue(InteractionResult.FAIL);
			return;
		}
		player.startUsingItem(hand);
		cir.setReturnValue(InteractionResult.CONSUME);
	}

	/**
	 * Use {@link ItemUseAnimation#NONE} so vanilla BOW / SPEAR arm + first-person poses
	 * do not stack under the addon Blockbench clips ({@code spearanimation.GLTF}).
	 * Charge/release still work via {@code startUsingItem} + {@code releaseUsing}.
	 */
	@Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
	private void weaponsmodaddon$spearNoVanillaUsePose(ItemStack stack, CallbackInfoReturnable<ItemUseAnimation> cir) {
		cir.setReturnValue(ItemUseAnimation.NONE);
	}
}
