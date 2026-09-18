package com.siegedempires.client.mixin;

import com.siegedempires.client.banner.ShipBannerRenderData;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Carries ship-flag render data from {@code extractRenderState} to
 * {@code CustomHeadLayer} submit (cannot use ThreadLocal — extract/submit
 * interleave across entities).
 */
@Mixin(ArmorStandRenderState.class)
public class ArmorStandRenderStateMixin implements ShipBannerRenderData.Holder {
	@Unique
	private @Nullable ShipBannerRenderData siegedempires$shipBanner;

	@Override
	public @Nullable ShipBannerRenderData siegedempires$getShipBanner() {
		return this.siegedempires$shipBanner;
	}

	@Override
	public void siegedempires$setShipBanner(@Nullable ShipBannerRenderData data) {
		this.siegedempires$shipBanner = data;
	}
}
