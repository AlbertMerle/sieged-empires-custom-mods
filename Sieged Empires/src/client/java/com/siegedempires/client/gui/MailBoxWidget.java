package com.siegedempires.client.gui;

import com.siegedempires.Siegedempires;
import com.siegedempires.client.network.ClientGuiData;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Top-right mail box: always shows a "See Mail" button + mail icon.
 * When the player has pending mail, also shows "You have Mail!" above the button.
 */
public final class MailBoxWidget {
	public static final Identifier MAIL_TEXTURE =
			Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "textures/gui/mail.png");
	/** Texture is 32×32; draw 1:1 (wider blit wraps UV and duplicates the icon). */
	private static final int ICON_TEX_SIZE = 32;
	private static final int ICON_WIDTH = 32;
	private static final int ICON_HEIGHT = 32;
	private static final int PADDING = 8;
	private static final int GAP = 8;
	private static final int LABEL_GAP = 4;
	private static final int MARGIN = 12;
	private static final int BOX_BORDER = 0xFF888888;
	private static final int BOX_FILL = 0xCC1A1A1A;
	private static final int LABEL_COLOR = 0xFFFFAA00;

	private int boxX;
	private int boxY;
	private int boxWidth;
	private int boxHeight;
	private int buttonWidth;
	private int buttonX;
	private int buttonY;
	private int iconX;
	private int iconY;
	private int labelX;
	private int labelY;
	private boolean showLabel;
	private int labelWidth;
	private Screen screen;

	public void layout(Screen screen) {
		layout(screen, ClientGuiData.hasMail());
	}

	public void layout(Screen screen, boolean hasMail) {
		this.screen = screen;
		this.showLabel = hasMail;

		Component seeMail = Component.translatable("gui.siegedempires.mail.see");
		buttonWidth = Math.max(90, screen.getFont().width(seeMail) + 16);

		labelWidth = 0;
		int labelHeight = 0;
		if (showLabel) {
			Component label = Component.translatable("gui.siegedempires.mail.button");
			labelWidth = screen.getFont().width(label);
			labelHeight = screen.getFont().lineHeight;
		}

		int contentWidth = Math.max(buttonWidth + GAP + ICON_WIDTH, labelWidth);
		boxWidth = PADDING * 2 + contentWidth;
		boxHeight = PADDING * 2 + ICON_HEIGHT;
		if (showLabel) {
			boxHeight += labelHeight + LABEL_GAP;
		}

		boxX = screen.width - MARGIN - boxWidth;
		boxY = MARGIN;

		int contentLeft = boxX + PADDING;
		if (showLabel) {
			labelX = contentLeft + (contentWidth - labelWidth) / 2;
			labelY = boxY + PADDING;
			buttonY = labelY + labelHeight + LABEL_GAP;
		} else {
			buttonY = boxY + PADDING;
		}

		buttonX = contentLeft;
		iconX = buttonX + buttonWidth + GAP;
		iconY = buttonY;
	}

	public Button createButton(Runnable onOpenMail) {
		return Button.builder(
				Component.translatable("gui.siegedempires.mail.see"),
				button -> onOpenMail.run()
		).bounds(buttonX, buttonY, buttonWidth, ICON_HEIGHT).build();
	}

	public void applyButtonBounds(Button button) {
		if (button != null) {
			button.setX(buttonX);
			button.setY(buttonY);
			button.setWidth(buttonWidth);
			button.setHeight(ICON_HEIGHT);
			button.visible = true;
			button.setMessage(Component.translatable("gui.siegedempires.mail.see"));
		}
	}

	public void render(GuiGraphicsExtractor graphics, boolean hasMail) {
		if (screen != null && showLabel != hasMail) {
			layout(screen, hasMail);
		}

		graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, BOX_FILL);
		graphics.outline(boxX, boxY, boxWidth, boxHeight, BOX_BORDER);

		if (showLabel && screen != null) {
			Component label = Component.translatable("gui.siegedempires.mail.button");
			graphics.text(screen.getFont(), label, labelX, labelY, LABEL_COLOR);
		}

		graphics.blit(RenderPipelines.GUI_TEXTURED, MAIL_TEXTURE,
				iconX, iconY, 0, 0, ICON_WIDTH, ICON_HEIGHT, ICON_TEX_SIZE, ICON_TEX_SIZE);
	}

	public int getBoxLeft() {
		return boxX;
	}

	public int getBoxRight() {
		return boxX + boxWidth;
	}

	public int getBoxBottom() {
		return boxY + boxHeight;
	}

	public int getBoxTop() {
		return boxY;
	}
}
