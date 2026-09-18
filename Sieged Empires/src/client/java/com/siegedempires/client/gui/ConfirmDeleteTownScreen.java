package com.siegedempires.client.gui;

import com.siegedempires.client.network.ClientGuiData;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConfirmDeleteTownScreen extends ConfirmNameDeleteScreen {
	public ConfirmDeleteTownScreen(Screen parent) {
		super(parent,
				Component.translatable("gui.siegedempires.delete_town_confirm_title"),
				Component.translatable("gui.siegedempires.delete_town_confirm_message"),
				Component.translatable("gui.siegedempires.delete_town_confirm_write"),
				resolveTownName());
	}

	private static String resolveTownName() {
		var data = ClientGuiData.getManageTownData();
		if (data != null && data.townName != null && !data.townName.isEmpty()) {
			return data.townName;
		}
		return "";
	}

	@Override
	protected void performDelete() {
		if (this.minecraft != null && this.minecraft.player != null) {
			this.minecraft.player.connection.sendCommand("town delete");
		}
		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(null);
		}
	}
}
