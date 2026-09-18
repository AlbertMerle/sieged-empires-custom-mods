package com.siegedempires.client.title;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.util.function.Consumer;

/**
 * Small sponsor credit on the vanilla title screen. Placement adapts to GUI scale:
 * bottom-center when there is room, otherwise top-left (always kept on-screen).
 */
public final class TitleScreenSponsor {
	public static final URI SPONSOR_URI = URI.create("https://briskdata.com/");
	public static final Component SPONSOR_TEXT = Component.translatable("menu.siegedempires.sponsor")
			.withStyle(ChatFormatting.GRAY);
	private static final Component VISIT_BUTTON = Component.translatable("menu.siegedempires.visit_sponsor");
	private static final int BUTTON_WIDTH = 118;
	private static final int BUTTON_HEIGHT = 18;

	private TitleScreenSponsor() {
	}

	public static void addWidgets(
			Screen parent,
			Consumer<AbstractWidget> addForegroundWidget,
			Consumer<StringWidget> addLabelOnly,
			Font font,
			int screenWidth,
			int screenHeight,
			int menuTop
	) {
		TitleScreenLayout.SponsorPlacement placement = TitleScreenLayout.computeSponsorPlacement(font, screenWidth, screenHeight, menuTop);
		int textWidth = font.width(SPONSOR_TEXT);

		addForegroundWidget.accept(Button.builder(VISIT_BUTTON, ConfirmLinkScreen.confirmLink(parent, SPONSOR_URI, true))
				.bounds(placement.buttonX(), placement.buttonY(), BUTTON_WIDTH, BUTTON_HEIGHT)
				.build());

		addLabelOnly.accept(new StringWidget(
				placement.textX(),
				placement.textY(),
				textWidth,
				font.lineHeight,
				SPONSOR_TEXT,
				font));
	}
}
