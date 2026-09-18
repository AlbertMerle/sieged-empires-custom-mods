package com.siegedempires.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConfirmLeaveTownScreen extends ConfirmYesNoScreen {
	public ConfirmLeaveTownScreen(Screen parent) {
		super(parent,
				Component.translatable("gui.siegedempires.leave_town_confirm_title"),
				Component.translatable("gui.siegedempires.leave_town_confirm_message"));
	}

	@Override
	protected void onConfirm() {
		if (this.minecraft != null && this.minecraft.player != null) {
			this.minecraft.player.connection.sendCommand("town leave");
		}
		this.minecraft.gui.setScreen(null);
	}
}
