package com.siegedempires.client.gui;

import com.siegedempires.client.network.ClientGuiData;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConfirmDeleteEmpireScreen extends ConfirmNameDeleteScreen {
	public ConfirmDeleteEmpireScreen(Screen parent) {
		super(parent,
				Component.translatable("gui.siegedempires.delete_empire_confirm_title"),
				Component.translatable("gui.siegedempires.delete_empire_confirm_message"),
				Component.translatable("gui.siegedempires.delete_empire_confirm_write"),
				resolveEmpireName());
	}

	private static String resolveEmpireName() {
		var data = ClientGuiData.getManageEmpireData();
		if (data != null && data.empireName != null && !data.empireName.isEmpty()) {
			return data.empireName;
		}
		return "";
	}

	@Override
	protected void performDelete() {
		if (this.minecraft != null && this.minecraft.player != null) {
			this.minecraft.player.connection.sendCommand("empire delete");
		}
		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(null);
		}
	}
}
