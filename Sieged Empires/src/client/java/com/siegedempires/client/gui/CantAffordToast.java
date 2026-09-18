package com.siegedempires.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Tutorial-style toast (top-right popup) shown when the player cannot afford an action.
 * Uses the vanilla tutorial toast background and a bold "!" icon in the icon slot.
 */
public class CantAffordToast implements Toast {
	private static final Identifier BACKGROUND_SPRITE = Identifier.withDefaultNamespace("toast/tutorial");
	private static final int TEXT_LEFT = 30;
	private static final int TEXT_WIDTH = 126;
	private static final int LINE_SPACING = 11;
	private static final int PADDING_TOP = 7;
	private static final int PADDING_BOTTOM = 3;
	private static final int DISPLAY_MS = 5000;

	private final List<FormattedCharSequence> lines;
	private Toast.Visibility visibility = Toast.Visibility.SHOW;

	public CantAffordToast(Font font, Component message) {
		this.lines = font.split(message.copy().withColor(-11534256), TEXT_WIDTH);
	}

	@Override
	public Toast.Visibility getWantedVisibility() {
		return visibility;
	}

	@Override
	public void update(ToastManager manager, long fullyVisibleForMs) {
		if (fullyVisibleForMs > DISPLAY_MS) {
			visibility = Toast.Visibility.HIDE;
		}
	}

	@Override
	public int height() {
		return PADDING_TOP + Math.max(lines.size(), 2) * LINE_SPACING + PADDING_BOTTOM;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs) {
		int height = height();
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND_SPRITE, 0, 0, width(), height);

		Component icon = Component.literal("!").withStyle(ChatFormatting.BOLD);
		int iconWidth = font.width(icon);
		graphics.text(font, icon, 16 - iconWidth / 2, 9, 0xFFFF55, false);

		int textHeight = lines.size() * LINE_SPACING;
		int contentHeight = Math.max(lines.size(), 2) * LINE_SPACING;
		int textTop = PADDING_TOP + (contentHeight - textHeight) / 2;

		for (int i = 0; i < lines.size(); i++) {
			graphics.text(font, lines.get(i), TEXT_LEFT, textTop + i * LINE_SPACING, -16777216, false);
		}
	}
}
