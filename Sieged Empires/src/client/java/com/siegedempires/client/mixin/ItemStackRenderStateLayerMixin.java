package com.siegedempires.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.siegedempires.banner.CustomBannerDesign;
import com.siegedempires.client.banner.CustomBannerClothTextures;
import com.siegedempires.client.banner.CustomBannerItemSubmit;
import com.siegedempires.client.banner.PendingBannerItemPixels;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.BannerSpecialRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holds freeform banner paint on the item layer and replaces vanilla pattern
 * submit when inventory/hand/frame special-models render a painted stack.
 */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public class ItemStackRenderStateLayerMixin {
	@Unique
	private @Nullable String siegedempires$bannerPixels;

	@Inject(method = "clear", at = @At("HEAD"))
	private void siegedempires$clearBannerPixels(CallbackInfo ci) {
		siegedempires$bannerPixels = null;
	}

	@Inject(method = "setupSpecialModel", at = @At("TAIL"))
	private <T> void siegedempires$captureBannerPixels(
			SpecialModelRenderer<T> renderer,
			@Nullable T argument,
			CallbackInfo ci) {
		String pending = PendingBannerItemPixels.take();
		if (renderer instanceof BannerSpecialRenderer
				&& CustomBannerDesign.isValidEncoded(pending)) {
			siegedempires$bannerPixels = pending;
		} else {
			siegedempires$bannerPixels = null;
		}
	}

	@Redirect(
			method = "submit",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/special/SpecialModelRenderer;submit(Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V"
			)
	)
	private void siegedempires$submitPixelBannerItem(
			SpecialModelRenderer<Object> renderer,
			@Nullable Object argument,
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			int lightCoords,
			int overlayCoords,
			boolean hasFoil,
			int outlineColor) {
		Object rawRenderer = renderer;
		if (siegedempires$bannerPixels != null
				&& rawRenderer instanceof BannerSpecialRenderer special) {
			Identifier texture = CustomBannerClothTextures.getOrCreate(siegedempires$bannerPixels);
			if (texture != null) {
				BannerSpecialRendererAccessor access = (BannerSpecialRendererAccessor) special;
				((CustomBannerItemSubmit) access.siegedempires$getBannerRenderer())
						.siegedempires$submitItemPixelCloth(
								access.siegedempires$getAttachment(),
								poseStack,
								submitNodeCollector,
								lightCoords,
								overlayCoords,
								outlineColor,
								texture);
				return;
			}
		}
		renderer.submit(
				argument,
				poseStack,
				submitNodeCollector,
				lightCoords,
				overlayCoords,
				hasFoil,
				outlineColor);
	}
}
