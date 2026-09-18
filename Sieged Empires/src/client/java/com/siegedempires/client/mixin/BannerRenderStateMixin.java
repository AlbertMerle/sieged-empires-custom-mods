package com.siegedempires.client.mixin;

import com.siegedempires.banner.CustomBannerDesign;
import com.siegedempires.client.banner.BannerPixelsHolder;
import net.minecraft.client.renderer.blockentity.state.BannerRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BannerRenderState.class)
public class BannerRenderStateMixin implements BannerPixelsHolder {
	@Unique
	private @Nullable String siegedempires$bannerPixels;

	@Override
	public @Nullable String siegedempires$getBannerPixels() {
		return siegedempires$bannerPixels;
	}

	@Override
	public void siegedempires$setBannerPixels(@Nullable String bannerPixels) {
		this.siegedempires$bannerPixels =
				CustomBannerDesign.isValidEncoded(bannerPixels) ? bannerPixels : null;
	}
}
