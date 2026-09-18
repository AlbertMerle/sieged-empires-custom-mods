package com.siegedempires.client.gui;

import com.siegedempires.network.EmpireListData;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConfirmJoinEmpireScreen extends ConfirmYesNoScreen {
	private final EmpireListData.EmpireInfo empire;

	public ConfirmJoinEmpireScreen(Screen parent, EmpireListData.EmpireInfo empire) {
		super(parent,
				Component.translatable("gui.siegedempires.join_empire_confirm_title"),
				Component.translatable("gui.siegedempires.join_empire_confirm_message", empire.name),
				Component.translatable("gui.siegedempires.join_empire_confirm_warning"));
		this.empire = empire;
	}

	@Override
	protected void onConfirm() {
		if (this.minecraft != null && this.minecraft.player != null) {
			String safeName = this.empire.name.replace(" ", "_");
			this.minecraft.player.connection.sendCommand("empire join " + safeName);
		}
		this.minecraft.gui.setScreen(null);
	}

	@Override
	protected void onDeny() {
		this.minecraft.gui.setScreen(EmpireSelectionScreen.forJoin(this.parent));
	}
}
