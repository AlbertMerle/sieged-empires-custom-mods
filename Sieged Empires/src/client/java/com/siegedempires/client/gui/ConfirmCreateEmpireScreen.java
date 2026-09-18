package com.siegedempires.client.gui;

import com.siegedempires.util.InventoryHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ConfirmCreateEmpireScreen extends ConfirmYesNoScreen {
	private static final int EMPIRE_CREATE_COST = 8;

	private final String empireName;
	private final String description;
	private final List<String> bannerPatterns;
	private final String bannerBaseColor;
	private final String bannerPixels;
	private final boolean usedTownBanner;

	public ConfirmCreateEmpireScreen(Screen parent, String empireName, String description,
			List<String> bannerPatterns, String bannerBaseColor, boolean usedTownBanner) {
		this(parent, empireName, description, bannerPatterns, bannerBaseColor, null, usedTownBanner);
	}

	public ConfirmCreateEmpireScreen(Screen parent, String empireName, String description,
			List<String> bannerPatterns, String bannerBaseColor, String bannerPixels, boolean usedTownBanner) {
		super(parent,
				Component.translatable("gui.siegedempires.confirm_empire_title"),
				Component.translatable("gui.siegedempires.confirm_empire_message", EMPIRE_CREATE_COST));
		this.empireName = empireName;
		this.description = description;
		this.bannerPatterns = bannerPatterns;
		this.bannerBaseColor = bannerBaseColor;
		this.bannerPixels = bannerPixels == null ? "" : bannerPixels;
		this.usedTownBanner = usedTownBanner;
	}

	@Override
	protected void onConfirm() {
		if (this.minecraft == null || this.minecraft.player == null) {
			return;
		}
		if (!InventoryHelper.hasEnoughGold(this.minecraft.player, EMPIRE_CREATE_COST)) {
			GuiNotifications.showCantAfford(this.minecraft);
			this.minecraft.gui.setScreen(new CreateEmpireScreen(this.parent, this.empireName, this.description,
					this.bannerPatterns, this.bannerBaseColor, this.bannerPixels, ""));
			return;
		}

		this.minecraft.gui.setScreen(new EmperorEmpressScreen(this.parent, this.empireName, this.description,
				this.bannerPatterns, this.bannerBaseColor, this.bannerPixels, this.usedTownBanner));
	}

	@Override
	protected void onDeny() {
		this.minecraft.gui.setScreen(new CreateEmpireScreen(this.parent, this.empireName, this.description,
				this.bannerPatterns, this.bannerBaseColor, this.bannerPixels, ""));
	}
}
