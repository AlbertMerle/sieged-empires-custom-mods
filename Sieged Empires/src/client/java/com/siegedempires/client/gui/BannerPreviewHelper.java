package com.siegedempires.client.gui;

import com.siegedempires.banner.BannerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class BannerPreviewHelper {
	private BannerPreviewHelper() {
	}

	public static ItemStack createPreviewStack(String baseColor, List<String> patterns) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return ItemStack.EMPTY;
		}

		return BannerHelper.createBannerStack(
				baseColor,
				patterns,
				minecraft.level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)
		);
	}
}
