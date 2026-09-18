package com.siegedempires.client.gui;

import com.siegedempires.util.InventoryHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConfirmMakeRestrictedLandScreen extends ConfirmYesNoScreen {
	public static final int COST = 2;

	public ConfirmMakeRestrictedLandScreen(Screen parent) {
		super(parent,
				Component.translatable("gui.siegedempires.make_restricted_land_confirm_title"),
				Component.translatable("gui.siegedempires.make_restricted_land_confirm", COST));
	}

	@Override
	protected void onConfirm() {
		if (this.minecraft == null || this.minecraft.player == null) {
			return;
		}
		if (!InventoryHelper.hasEnoughGold(this.minecraft.player, COST)) {
			GuiNotifications.showCantAfford(this.minecraft);
			this.minecraft.gui.setScreen(this.parent);
			return;
		}
		this.minecraft.player.connection.sendCommand("town makerestrictedland");
		this.minecraft.gui.setScreen(this.parent);
	}
}
