package com.siegedempires.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConfirmAllyRequestScreen extends ConfirmYesNoScreen {
	private final String targetType;
	private final String targetName;

	public ConfirmAllyRequestScreen(Screen parent, String targetType, String targetName) {
		super(parent,
				Component.translatable("gui.siegedempires.ally_request_confirm_title"),
				Component.translatable("gui.siegedempires.ally_request_confirm", targetName));
		this.targetType = targetType;
		this.targetName = targetName;
	}

	@Override
	protected void onConfirm() {
		DiplomacyGuiHelper.send("allyrequest", this.targetType, this.targetName);
		this.minecraft.gui.setScreen(this.parent);
	}
}
