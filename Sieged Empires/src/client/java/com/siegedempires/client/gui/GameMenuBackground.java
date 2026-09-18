package com.siegedempires.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Classic tiled block-texture menu backdrop (same idea as old dirt menus),
 * using cobblestone so the session menu reads like a stone wall.
 */
public final class GameMenuBackground {
	private static final Identifier COBBLESTONE =
			Identifier.withDefaultNamespace("textures/block/cobblestone.png");

	/** Screen pixels per texture repeat — matches vanilla {@code Screen.extractMenuBackgroundTexture}. */
	private static final int TILE_SIZE = 32;

	/** Multiply tint (~25% brightness), same feel as classic darkened dirt menus. */
	private static final int TINT = 0xFF404040;

	private GameMenuBackground() {
	}

	public static void draw(GuiGraphicsExtractor graphics, int width, int height) {
		graphics.blit(
				RenderPipelines.GUI_TEXTURED,
				COBBLESTONE,
				0, 0,
				0.0F, 0.0F,
				width, height,
				TILE_SIZE, TILE_SIZE,
				TINT
		);
	}
}
