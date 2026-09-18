package com.siegedempires.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * "Are you sure you want to create a Land Revoking Banner?"
 */
public class ConfirmCreateLandRevokeBannerScreen extends ConfirmYesNoScreen {
	public ConfirmCreateLandRevokeBannerScreen(Screen parent) {
		super(parent,
				Component.translatable("gui.siegedempires.confirm_create_land_revoke_banner_title"),
				Component.translatable("gui.siegedempires.confirm_create_land_revoke_banner_message"));
	}

	@Override
	protected void onConfirm() {
		if (this.minecraft != null && this.minecraft.player != null) {
			this.minecraft.player.connection.sendCommand("town revokelandbanner");
		}
		this.minecraft.gui.setScreen(this.parent);
	}
}
