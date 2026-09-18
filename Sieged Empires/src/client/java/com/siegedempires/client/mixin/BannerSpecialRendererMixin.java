package com.siegedempires.client.mixin;

import com.siegedempires.banner.BannerHelper;
import com.siegedempires.banner.CustomBannerDesign;
import com.siegedempires.client.banner.PendingBannerItemPixels;
import net.minecraft.client.renderer.special.BannerSpecialRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stashes freeform {@code banner_pixels} from the item stack so the following
 * {@code setupSpecialModel} call can attach them to the layer render state.
 */
@Mixin(BannerSpecialRenderer.class)
public class BannerSpecialRendererMixin {
	@Inject(method = "extractArgument", at = @At("HEAD"))
	private void siegedempires$captureBannerPixels(
			ItemStack stack,
			CallbackInfoReturnable<BannerPatternLayers> cir) {
		String pixels = BannerHelper.getBannerPixelsFromStack(stack);
		PendingBannerItemPixels.set(
				CustomBannerDesign.isValidEncoded(pixels) ? pixels : null);
	}
}
