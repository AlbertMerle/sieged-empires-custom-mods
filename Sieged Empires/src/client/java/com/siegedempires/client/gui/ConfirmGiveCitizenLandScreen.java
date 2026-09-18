package com.siegedempires.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Confirmation dialog for the Give Citizen Land flow.
 */
public class ConfirmGiveCitizenLandScreen extends ConfirmYesNoScreen {
	private final String playerName;

	public ConfirmGiveCitizenLandScreen(Screen parent, String playerName) {
		super(parent,
				Component.translatable("gui.siegedempires.confirm_give_citizen_land_title"),
				Component.translatable("gui.siegedempires.confirm_give_citizen_land_message",
						playerName == null ? "" : playerName));
		this.playerName = playerName == null ? "" : playerName;
	}

	@Override
	protected void onConfirm() {
		if (this.minecraft != null && this.minecraft.player != null) {
			String safeName = this.playerName.replace(" ", "_");
			this.minecraft.player.connection.sendCommand("town givecitizenland " + safeName);
		}
		this.minecraft.gui.setScreen(this.parent);
	}
}
