package com.siegedempires.client.gui;

import com.siegedempires.network.ManageTownData;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConfirmEvictPlayerScreen extends ConfirmYesNoScreen {
	private final ManageTownData.MemberInfo member;

	public ConfirmEvictPlayerScreen(Screen parent, ManageTownData.MemberInfo member) {
		super(parent,
				Component.translatable("gui.siegedempires.evict_player_confirm_title"),
				Component.translatable("gui.siegedempires.evict_player_confirm_message", member.name));
		this.member = member;
	}

	@Override
	protected void onConfirm() {
		if (this.minecraft != null && this.minecraft.player != null) {
			String safeName = this.member.name.replace(" ", "_");
			this.minecraft.player.connection.sendCommand("town evict " + safeName);
		}
		this.minecraft.gui.setScreen(this.parent);
	}
}
