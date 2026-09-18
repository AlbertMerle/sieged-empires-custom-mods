package com.siegedempires.client.gui;

import com.siegedempires.network.ManageEmpireData;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConfirmDisannexTownScreen extends ConfirmYesNoScreen {
	private final ManageEmpireData.TownInfo town;

	public ConfirmDisannexTownScreen(Screen parent, ManageEmpireData.TownInfo town) {
		super(parent,
				Component.translatable("gui.siegedempires.disannex_town_confirm_title"),
				Component.translatable("gui.siegedempires.disannex_town_confirm_message", town.name));
		this.town = town;
	}

	@Override
	protected void onConfirm() {
		if (this.minecraft != null && this.minecraft.player != null) {
			String safeName = this.town.name.replace(" ", "_");
			this.minecraft.player.connection.sendCommand("empire disannex " + safeName);
		}
		this.minecraft.gui.setScreen(this.parent);
	}
}
