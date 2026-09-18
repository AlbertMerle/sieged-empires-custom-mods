package com.weaponsmodaddon.client.scope;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;

/**
 * Zoom label while aiming a scoped musket. Spyglass vignette from {@code Player.isScoping()};
 * aim reticle is {@link com.weaponsmodaddon.client.hud.AimCrosshair}.
 */
public final class ScopedMusketHud {

	private static final int ZOOM_COLOR = ARGB.color(220, 255, 255, 255);

	private ScopedMusketHud() {
	}

	public static void render(GuiGraphicsExtractor graphics) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !mc.options.getCameraType().isFirstPerson()) {
			return;
		}
		if (!ScopedMusketAimClient.isAimingScoped(mc.player)) {
			return;
		}

		int w = graphics.guiWidth();
		int h = graphics.guiHeight();
		String label = ScopedMusketAimClient.zoomLabel();
		graphics.centeredText(mc.font, label, w / 2, h - 28, ZOOM_COLOR);
	}
}
