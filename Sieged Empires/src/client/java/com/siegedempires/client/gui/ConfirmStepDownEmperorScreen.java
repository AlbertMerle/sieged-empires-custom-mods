package com.siegedempires.client.gui;

import com.siegedempires.network.ManageEmpireData;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConfirmStepDownEmperorScreen extends ConfirmYesNoScreen {
	private final ManageEmpireData.TownInfo town;

	public ConfirmStepDownEmperorScreen(Screen parent, ManageEmpireData.TownInfo town) {
		super(parent,
				Component.translatable("gui.siegedempires.step_down_emperor_confirm_title"),
				Component.translatable("gui.siegedempires.step_down_emperor_confirm_message", town.monarchName));
		this.town = town;
	}

	@Override
	protected void onConfirm() {
		if (this.minecraft != null && this.minecraft.player != null) {
			String safeName = this.town.monarchName.replace(" ", "_");
			this.minecraft.player.connection.sendCommand("empire stepdown " + safeName);
		}
		this.minecraft.gui.setScreen(null);
	}
}
