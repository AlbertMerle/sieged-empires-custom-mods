package com.siegedempires.client.banner;

import com.siegedempires.banner.CustomBannerDesign;
import org.jspecify.annotations.Nullable;

/**
 * Carries freeform {@code bannerPixels} on {@link net.minecraft.client.renderer.blockentity.state.BannerRenderState}.
 */
public interface BannerPixelsHolder {
	@Nullable String siegedempires$getBannerPixels();

	void siegedempires$setBannerPixels(@Nullable String bannerPixels);

	static boolean hasValidPixels(@Nullable String pixels) {
		return CustomBannerDesign.isValidEncoded(pixels);
	}
}
