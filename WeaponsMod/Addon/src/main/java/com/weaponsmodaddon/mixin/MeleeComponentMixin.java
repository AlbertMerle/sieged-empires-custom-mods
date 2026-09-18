package com.weaponsmodaddon.mixin;

import ckathode.weaponmod.item.MeleeCompSpear;
import ckathode.weaponmod.item.MeleeComponent;
import com.weaponsmodaddon.spear.SpearChargeThrow;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spear charge release lives on {@link MeleeComponent#releaseUsing}; only handle {@link MeleeCompSpear}.
 * Also makes spears stack to 8 (no durability — same constraint as vanilla stackable items / javelins).
 */
@Mixin(MeleeComponent.class)
public abstract class MeleeComponentMixin {
	private static final int SPEAR_MAX_STACK = 8;

	@Inject(method = "setProperties", at = @At("HEAD"), cancellable = true)
	private void weaponsmodaddon$spearStacksTo8(
			Item.Properties properties,
			CallbackInfoReturnable<Item.Properties> cir
	) {
		if (!((Object) this instanceof MeleeCompSpear)) {
			return;
		}
		MeleeComponent self = (MeleeComponent) (Object) this;
		cir.setReturnValue(properties
				.component(DataComponents.TOOL, self.getToolComponent())
				.stacksTo(SPEAR_MAX_STACK));
	}

	@Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
	private void weaponsmodaddon$spearChargeRelease(
			ItemStack stack,
			Level level,
			LivingEntity user,
			int remainingUseTicks,
			CallbackInfoReturnable<Boolean> cir
	) {
		if (!((Object) this instanceof MeleeCompSpear)) {
			return;
		}
		if (!(user instanceof Player player) || !SpearChargeThrow.canCharge(player)) {
			cir.setReturnValue(false);
			return;
		}
		MeleeComponent self = (MeleeComponent) (Object) this;
		int useDuration = self.getUseDuration(stack);
		cir.setReturnValue(SpearChargeThrow.release(stack, level, user, useDuration, remainingUseTicks));
	}
}
