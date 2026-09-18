package com.siegedempires.client.gui;

import com.siegedempires.model.DiplomacyRecord;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConfirmDeclareWarScreen extends ConfirmYesNoScreen {
	private final Screen returnScreen;
	private final String targetType;
	private final String targetName;

	public ConfirmDeclareWarScreen(Screen parent, Screen returnScreen, String targetType, String targetName) {
		super(parent,
				Component.translatable("gui.siegedempires.declare_war_confirm_title"),
				DiplomacyRecord.TYPE_EMPIRE.equals(targetType)
						? Component.translatable("gui.siegedempires.declare_war_confirm_empire")
						: Component.translatable("gui.siegedempires.declare_war_confirm_town"));
		this.returnScreen = returnScreen;
		this.targetType = targetType;
		this.targetName = targetName;
	}

	@Override
	protected void onConfirm() {
		DiplomacyGuiHelper.send("declarewar", this.targetType, this.targetName);
		this.minecraft.gui.setScreen(this.returnScreen);
	}
}
