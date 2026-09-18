package com.weaponsmodaddon.client.mixin;

import com.weaponsmodaddon.ModItemTags;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Suppresses vanilla {@link HumanoidModel.ArmPose#BOW_AND_ARROW} / {@link HumanoidModel.ArmPose#SPEAR}
 * for WeaponMod spears and javelins so only addon Blockbench clips pose the arms.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(
			method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
			at = @At("HEAD"),
			cancellable = true
	)
	private static void weaponsmodaddon$addonSpearArmPose(
			Avatar avatar,
			ItemStack itemInHand,
			InteractionHand hand,
			CallbackInfoReturnable<HumanoidModel.ArmPose> cir
	) {
		if (itemInHand.isEmpty()) {
			return;
		}
		if (itemInHand.is(ModItemTags.SPEARS) || itemInHand.is(ModItemTags.JAVELINS)) {
			cir.setReturnValue(HumanoidModel.ArmPose.EMPTY);
		}
	}
}
