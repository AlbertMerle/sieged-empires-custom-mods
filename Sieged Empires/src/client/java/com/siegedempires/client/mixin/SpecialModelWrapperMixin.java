package com.siegedempires.client.mixin;

import com.siegedempires.banner.BannerHelper;
import com.siegedempires.banner.CustomBannerDesign;
import com.siegedempires.client.banner.PendingBannerItemPixels;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.SpecialModelWrapper;
import net.minecraft.client.renderer.special.BannerSpecialRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * GUI item atlas keys off {@link ItemStackRenderState#appendModelIdentityElement}.
 * Pixel banners share empty pattern layers, so identity must include the paint
 * string or every custom flag of the same dye collapses to one cached icon.
 */
@Mixin(SpecialModelWrapper.class)
public class SpecialModelWrapperMixin<T> {
	@Shadow
	@Final
	private SpecialModelRenderer<T> specialRenderer;

	@Inject(method = "update", at = @At("HEAD"))
	private void siegedempires$clearPendingPixels(
			ItemStackRenderState output,
			ItemStack item,
			ItemModelResolver resolver,
			ItemDisplayContext displayContext,
			@Nullable ClientLevel level,
			@Nullable ItemOwner owner,
			int seed,
			CallbackInfo ci) {
		PendingBannerItemPixels.clear();
	}

	@Inject(method = "update", at = @At("TAIL"))
	private void siegedempires$appendBannerPixelIdentity(
			ItemStackRenderState output,
			ItemStack item,
			ItemModelResolver resolver,
			ItemDisplayContext displayContext,
			@Nullable ClientLevel level,
			@Nullable ItemOwner owner,
			int seed,
			CallbackInfo ci) {
		if (!(this.specialRenderer instanceof BannerSpecialRenderer)) {
			return;
		}
		String pixels = BannerHelper.getBannerPixelsFromStack(item);
		if (CustomBannerDesign.isValidEncoded(pixels)) {
			output.appendModelIdentityElement(pixels);
		}
	}
}
