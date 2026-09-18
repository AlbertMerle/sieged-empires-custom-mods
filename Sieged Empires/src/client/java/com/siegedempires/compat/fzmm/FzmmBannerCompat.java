package com.siegedempires.compat.fzmm;

import com.siegedempires.banner.BannerHelper;
import fzmm.zailer.me.client.gui.banner_editor.BannerEditorScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPattern;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Soft-compat entry for opening FZMM's banner editor from SE create menus.
 * Only call after {@link #isAvailable()} is true (loads FZMM classes).
 */
public final class FzmmBannerCompat {
	private FzmmBannerCompat() {}

	public static boolean isAvailable() {
		return FabricLoader.getInstance().isModLoaded("fzmm");
	}

	/**
	 * Open FZMM banner editor with {@code parent} kept as the return screen
	 * (same instance — name/description/cache fields stay intact).
	 *
	 * @param onSaved receives base color name + pattern list ({@code color:pattern})
	 */
	public static void openEditor(Screen parent,
								  String bannerBaseColor,
								  List<String> bannerPatterns,
								  BiConsumer<String, List<String>> onSaved) {
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return;
		}
		HolderGetter<BannerPattern> patterns = resolvePatternLookup(client);
		ItemStack initial = BannerHelper.createBannerStack(
				bannerBaseColor == null || bannerBaseColor.isEmpty() ? "white" : bannerBaseColor,
				bannerPatterns == null ? List.of() : bannerPatterns,
				patterns);

		FzmmBannerSession.begin(initial, stack -> {
			BannerHelper.ParsedBanner parsed = BannerHelper.parseBannerStack(stack);
			onSaved.accept(parsed.baseColor(), new ArrayList<>(parsed.patterns()));
		});
		client.gui.setScreen(new BannerEditorScreen(parent));
	}

	private static HolderGetter<BannerPattern> resolvePatternLookup(Minecraft client) {
		if (client.getConnection() != null) {
			return client.getConnection().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		}
		if (client.level != null) {
			return client.level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		}
		throw new IllegalStateException("Cannot open banner editor without registry access");
	}
}
