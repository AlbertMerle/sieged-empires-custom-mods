package com.siegedempires.client.banner;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.jspecify.annotations.Nullable;

/**
 * Captured town/empire banner design for a Shippy Ships mast flag.
 */
public record ShipBannerRenderData(DyeColor baseColor, BannerPatternLayers patterns) {
	public interface Holder {
		@Nullable ShipBannerRenderData siegedempires$getShipBanner();

		void siegedempires$setShipBanner(@Nullable ShipBannerRenderData data);
	}

	public static @Nullable ShipBannerRenderData fromHeadStack(ItemStack head) {
		if (head.isEmpty() || !(head.getItem() instanceof BannerItem bannerItem)) {
			return null;
		}
		BannerPatternLayers patterns = head.get(DataComponents.BANNER_PATTERNS);
		return new ShipBannerRenderData(
				bannerItem.getColor(),
				patterns != null ? patterns : BannerPatternLayers.EMPTY);
	}
}
