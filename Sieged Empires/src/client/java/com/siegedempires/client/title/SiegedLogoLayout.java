package com.siegedempires.client.title;

import com.siegedempires.Siegedempires;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

/**
 * Shared Sieged Empires logo metrics: preferred on-screen size matches GUI scale 2,
 * then shrinks to fit the available GUI width/height so it never collides.
 */
public final class SiegedLogoLayout {
	public static final Identifier TEXTURE =
			Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "textures/gui/logo.png");

	public static final int TEX_WIDTH = 1024;
	public static final int TEX_HEIGHT = 506;

	/** Title-screen look the user likes at GUI scale 2. */
	public static final int TITLE_PREFERRED_WIDTH = 280;
	/** In-game Sieged Empires menu look at GUI scale 2. */
	public static final int MENU_PREFERRED_WIDTH = 240;

	private SiegedLogoLayout() {
	}

	/** Eager-load like {@code LoadingOverlay}'s Mojang logo — required during the startup reload overlay. */
	public static void registerStartupTextures(TextureManager textureManager) {
		textureManager.registerAndLoad(TEXTURE, new SiegedLogoTexture());
	}

	public static int heightForWidth(int width) {
		return Math.max(1, width * TEX_HEIGHT / TEX_WIDTH);
	}

	public static int widthForHeight(int height) {
		return Math.max(1, height * TEX_WIDTH / TEX_HEIGHT);
	}

	/**
	 * Fits the logo inside {@code maxWidth}×{@code maxHeight}, never larger than
	 * {@code preferredWidth}, preserving aspect ratio.
	 */
	public static Size fit(int preferredWidth, int maxWidth, int maxHeight) {
		int widthCap = Math.max(1, Math.min(preferredWidth, maxWidth));
		int heightCap = Math.max(1, maxHeight);

		int width = widthCap;
		int height = heightForWidth(width);
		if (height > heightCap) {
			height = heightCap;
			width = widthForHeight(height);
			if (width > widthCap) {
				width = widthCap;
				height = heightForWidth(width);
			}
		}
		return new Size(width, height);
	}

	public static TitlePlacement titlePlacement(int screenWidth, int heightOffset, int menuTop) {
		int gap = 10;
		int maxWidth = Math.max(64, screenWidth - 32);
		int maxHeight = Math.max(24, menuTop - heightOffset - gap);
		Size size = fit(TITLE_PREFERRED_WIDTH, maxWidth, maxHeight);

		int logoY = heightOffset;
		// If still tight, slide the logo up within the top margin instead of overlapping.
		int bottom = logoY + size.height();
		if (bottom + gap > menuTop) {
			logoY = Math.max(4, menuTop - gap - size.height());
		}
		int logoX = screenWidth / 2 - size.width() / 2;
		return new TitlePlacement(logoX, logoY, size.width(), size.height());
	}

	/** Same formula TitleScreen uses for the first menu row. */
	public static int titleMenuTop(int screenHeight) {
		return screenHeight / 4 + 48;
	}

	public record Size(int width, int height) {
	}

	public record TitlePlacement(int x, int y, int width, int height) {
	}
}
