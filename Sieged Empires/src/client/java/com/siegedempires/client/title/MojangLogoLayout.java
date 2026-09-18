package com.siegedempires.client.title;

import com.siegedempires.Siegedempires;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

/** White Mojang icon for the startup loading overlay (from {@code mojang.png}). */
public final class MojangLogoLayout {
	public static final Identifier TEXTURE =
			Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "textures/gui/mojang_icon.png");

	public static final int TEX_WIDTH = 825;
	public static final int TEX_HEIGHT = 825;

	private MojangLogoLayout() {
	}

	public static void registerStartupTextures(TextureManager textureManager) {
		textureManager.registerAndLoad(TEXTURE, new MojangLogoTexture());
	}

	public static int widthForHeight(int height) {
		return Math.max(1, height * TEX_WIDTH / TEX_HEIGHT);
	}
}
