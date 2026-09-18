package com.weaponsmodaddon.client.hud;

import com.weaponsmodaddon.gun.GunReload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

/**
 * Red secondary XP-style bar above the vanilla experience bar while reloading a gun.
 */
public final class ReloadHud {
	private static final int BAR_WIDTH = 182;
	private static final int BAR_HEIGHT = 5;
	/** Pixels above the vanilla XP bar ({@code ContextualBar.MARGIN_BOTTOM + HEIGHT}). */
	private static final int ABOVE_XP = 12;
	private static final int BG = ARGB.color(180, 0, 0, 0);
	private static final int FILL = ARGB.color(220, 200, 40, 40);
	private static final int BORDER = ARGB.color(200, 80, 20, 20);
	private static final int LABEL = ARGB.color(255, 255, 80, 80);
	private static final int LABEL_SHADOW = ARGB.color(255, 0, 0, 0);

	private ReloadHud() {
	}

	public static void render(GuiGraphicsExtractor graphics) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.gui.hud.isHidden()) {
			return;
		}
		if (!GunReload.shouldShowReloadHud(player)) {
			return;
		}

		int guiW = graphics.guiWidth();
		int guiH = graphics.guiHeight();
		int left = (guiW - BAR_WIDTH) / 2;
		// Vanilla XP bar top is guiH - 24 - 5; place ours just above that.
		int top = guiH - 24 - 5 - ABOVE_XP;

		float progress = GunReload.reloadProgress(player);
		int fillW = Math.round(BAR_WIDTH * progress);

		graphics.fill(left - 1, top - 1, left + BAR_WIDTH + 1, top + BAR_HEIGHT + 1, BORDER);
		graphics.fill(left, top, left + BAR_WIDTH, top + BAR_HEIGHT, BG);
		if (fillW > 0) {
			graphics.fill(left, top, left + fillW, top + BAR_HEIGHT, FILL);
		}

		Component label = Component.translatable("hud.weaponsmodaddon.reloading");
		int textX = (guiW - mc.font.width(label)) / 2;
		int textY = top - 10;
		graphics.text(mc.font, label, textX + 1, textY, LABEL_SHADOW, false);
		graphics.text(mc.font, label, textX - 1, textY, LABEL_SHADOW, false);
		graphics.text(mc.font, label, textX, textY + 1, LABEL_SHADOW, false);
		graphics.text(mc.font, label, textX, textY - 1, LABEL_SHADOW, false);
		graphics.text(mc.font, label, textX, textY, LABEL, false);
	}
}
