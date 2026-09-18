package com.siegedempires.client.gui;

import com.siegedempires.network.DiplomacyData;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConfirmRemoveAllyScreen extends ConfirmYesNoScreen {
	private final DiplomacyData.FactionInfo ally;

	public ConfirmRemoveAllyScreen(Screen parent, DiplomacyData.FactionInfo ally) {
		super(parent,
				Component.translatable("gui.siegedempires.remove_ally_confirm_title"),
				Component.translatable("gui.siegedempires.remove_ally_confirm", ally.name));
		this.ally = ally;
	}

	@Override
	protected void onConfirm() {
		DiplomacyGuiHelper.send("removeally", this.ally.entityType, this.ally.name);
		this.minecraft.gui.setScreen(this.parent);
	}
}
