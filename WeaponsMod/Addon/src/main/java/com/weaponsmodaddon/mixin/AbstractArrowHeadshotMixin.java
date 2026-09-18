package com.weaponsmodaddon.mixin;

import com.weaponsmodaddon.headshot.HeadshotContext;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bow/crossbow arrow headshots (not tridents): 2× damage + blindness via {@link HeadshotContext}.
 */
@Mixin(AbstractArrow.class)
public abstract class AbstractArrowHeadshotMixin {

	@Inject(method = "onHitEntity", at = @At("HEAD"))
	private void weaponsmodaddon$prepareArrowHeadshot(EntityHitResult hitResult, CallbackInfo ci) {
		if ((Object) this instanceof ThrownTrident) {
			return;
		}
		HeadshotContext.prepare(hitResult, HeadshotContext.Kind.ARROW);
	}

	@Inject(method = "onHitEntity", at = @At("RETURN"))
	private void weaponsmodaddon$clearArrowHeadshot(EntityHitResult hitResult, CallbackInfo ci) {
		HeadshotContext.clear();
	}
}
