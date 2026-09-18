package com.siegedempires.client.gui;

import com.siegedempires.config.ModSettings;
import com.siegedempires.util.InventoryHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ConfirmCreateTownScreen extends ConfirmYesNoScreen {
	private final String townName;
	private final String description;
	private final List<String> bannerPatterns;
	private final String bannerBaseColor;
	private final String bannerPixels;

	public ConfirmCreateTownScreen(Screen parent, String townName, String description,
			List<String> bannerPatterns, String bannerBaseColor) {
		this(parent, townName, description, bannerPatterns, bannerBaseColor, null);
	}

	public ConfirmCreateTownScreen(Screen parent, String townName, String description,
			List<String> bannerPatterns, String bannerBaseColor, String bannerPixels) {
		super(parent,
				Component.translatable("gui.siegedempires.confirm_title"),
				Component.translatable("gui.siegedempires.confirm_message", ModSettings.get().createTownCost));
		this.townName = townName;
		this.description = description;
		this.bannerPatterns = bannerPatterns;
		this.bannerBaseColor = bannerBaseColor;
		this.bannerPixels = bannerPixels == null ? "" : bannerPixels;
	}

	@Override
	protected void onConfirm() {
		if (this.minecraft == null || this.minecraft.player == null) {
			return;
		}
		if (!InventoryHelper.hasEnoughGold(this.minecraft.player, ModSettings.get().createTownCost)) {
			GuiNotifications.showCantAfford(this.minecraft);
			this.minecraft.gui.setScreen(new CreateTownScreen(this.parent, this.townName, this.description,
					this.bannerPatterns, this.bannerBaseColor, this.bannerPixels, ""));
			return;
		}

		this.minecraft.gui.setScreen(new KingQueenScreen(this.parent, this.townName, this.description,
				this.bannerPatterns, this.bannerBaseColor, this.bannerPixels));
	}

	@Override
	protected void onDeny() {
		this.minecraft.gui.setScreen(new CreateTownScreen(this.parent, this.townName, this.description,
				this.bannerPatterns, this.bannerBaseColor, this.bannerPixels, ""));
	}
}
