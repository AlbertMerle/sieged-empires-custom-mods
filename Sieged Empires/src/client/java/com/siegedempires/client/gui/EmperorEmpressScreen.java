package com.siegedempires.client.gui;

import com.siegedempires.client.network.ClientNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class EmperorEmpressScreen extends Screen {
	private final Screen parent;
	private final String empireName;
	private final String description;
	private final List<String> bannerPatterns;
	private final String bannerBaseColor;
	private final String bannerPixels;
	private final boolean usedTownBanner;

	public EmperorEmpressScreen(Screen parent, String empireName, String description,
			List<String> bannerPatterns, String bannerBaseColor, boolean usedTownBanner) {
		this(parent, empireName, description, bannerPatterns, bannerBaseColor, null, usedTownBanner);
	}

	public EmperorEmpressScreen(Screen parent, String empireName, String description,
			List<String> bannerPatterns, String bannerBaseColor, String bannerPixels, boolean usedTownBanner) {
		super(Component.translatable("gui.siegedempires.emperor_empress_title"));
		this.parent = parent;
		this.empireName = empireName;
		this.description = description;
		this.bannerPatterns = bannerPatterns;
		this.bannerBaseColor = bannerBaseColor;
		this.bannerPixels = bannerPixels == null ? "" : bannerPixels;
		this.usedTownBanner = usedTownBanner;
	}

	@Override
	protected void init() {
		super.init();

		Component congratsMessage = Component.translatable("gui.siegedempires.emperor_empress_congrats");
		int congratsWidth = this.font.width(congratsMessage);
		this.addRenderableWidget(new StringWidget(
				this.width / 2 - congratsWidth / 2, this.height / 2 - 30,
				congratsWidth, this.font.lineHeight,
				congratsMessage, this.font));

		Component questionMessage = Component.translatable("gui.siegedempires.emperor_empress_question");
		int questionWidth = this.font.width(questionMessage);
		this.addRenderableWidget(new StringWidget(
				this.width / 2 - questionWidth / 2, this.height / 2 - 10,
				questionWidth, this.font.lineHeight,
				questionMessage, this.font));

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.emperor"),
				button -> onChooseTitle("Emperor")
		).bounds(this.width / 2 - 105, this.height / 2 + 20, 100, 20).build());

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.empress"),
				button -> onChooseTitle("Empress")
		).bounds(this.width / 2 + 5, this.height / 2 + 20, 100, 20).build());
	}

	private void onChooseTitle(String title) {
		if (minecraft != null && minecraft.player != null) {
			ClientNetworking.createEmpire(empireName, description, title, bannerBaseColor, bannerPatterns, bannerPixels);
		}
		minecraft.gui.setScreen(null);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
