package com.siegedempires.client.gui;

import com.siegedempires.client.network.ClientNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class KingQueenScreen extends Screen {
	private final Screen parent;
	private final String townName;
	private final String description;
	private final List<String> bannerPatterns;
	private final String bannerBaseColor;
	private final String bannerPixels;

	public KingQueenScreen(Screen parent, String townName, String description,
			List<String> bannerPatterns, String bannerBaseColor) {
		this(parent, townName, description, bannerPatterns, bannerBaseColor, null);
	}

	public KingQueenScreen(Screen parent, String townName, String description,
			List<String> bannerPatterns, String bannerBaseColor, String bannerPixels) {
		super(Component.translatable("gui.siegedempires.king_queen_title"));
		this.parent = parent;
		this.townName = townName;
		this.description = description;
		this.bannerPatterns = bannerPatterns;
		this.bannerBaseColor = bannerBaseColor;
		this.bannerPixels = bannerPixels == null ? "" : bannerPixels;
	}

	@Override
	protected void init() {
		super.init();

		Component questionMessage = Component.translatable("gui.siegedempires.king_queen_question");
		int questionWidth = this.font.width(questionMessage);
		this.addRenderableWidget(new StringWidget(
				this.width / 2 - questionWidth / 2, this.height / 2 - 10,
				questionWidth, this.font.lineHeight,
				questionMessage, this.font));

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.king"),
				button -> onChooseTitle("King")
		).bounds(this.width / 2 - 105, this.height / 2 + 20, 100, 20).build());

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.queen"),
				button -> onChooseTitle("Queen")
		).bounds(this.width / 2 + 5, this.height / 2 + 20, 100, 20).build());
	}

	private void onChooseTitle(String title) {
		if (minecraft != null && minecraft.player != null) {
			ClientNetworking.createTown(townName, description, title, bannerBaseColor, bannerPatterns, bannerPixels);
		}
		minecraft.gui.setScreen(null);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
