package com.siegedempires.client.gui;

import com.siegedempires.client.network.ClientNetworking;
import com.siegedempires.network.ManageTownData;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConfirmCrownDukeScreen extends ConfirmYesNoScreen {
	private final String wartownId;
	private final ManageTownData.MemberInfo member;

	public ConfirmCrownDukeScreen(Screen parent, String wartownId, ManageTownData.MemberInfo member) {
		super(parent,
				Component.translatable("gui.siegedempires.crown_duke_confirm_title"),
				buildMessage(member.name, parent),
				Component.empty());
		this.wartownId = wartownId;
		this.member = member;
	}

	private static Component buildMessage(String playerName, Screen parent) {
		String townName = resolveWartownName(parent);
		return Component.translatable("gui.siegedempires.crown_duke_confirm_message", playerName, townName);
	}

	private static String resolveWartownName(Screen parent) {
		var data = com.siegedempires.client.network.ClientGuiData.getManageTownData();
		if (data != null && data.townName != null && !data.townName.isEmpty()) {
			return data.townName;
		}
		return "???";
	}

	@Override
	protected void onConfirm() {
		if (this.minecraft != null && this.minecraft.player != null) {
			ClientNetworking.requestCrownWartownMonarch(wartownId, member.uuid);
		}
		this.minecraft.gui.setScreen(this.parent);
	}
}
