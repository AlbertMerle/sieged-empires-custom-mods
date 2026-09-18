package com.siegedempires.client.gui;

import com.siegedempires.network.ManageTownData;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * "Are you sure you want to revoke all land deeds from [player]?"
 */
public class ConfirmRevokeAllLandScreen extends ConfirmYesNoScreen {
	private final ManageTownData.MemberInfo member;

	public ConfirmRevokeAllLandScreen(Screen parent, ManageTownData.MemberInfo member) {
		super(parent,
				Component.translatable("gui.siegedempires.confirm_revoke_all_land_title"),
				Component.translatable("gui.siegedempires.confirm_revoke_all_land_message",
						member.name != null ? member.name : "?"));
		this.member = member;
	}

	@Override
	protected void onConfirm() {
		if (this.minecraft != null && this.minecraft.player != null
				&& this.member != null && this.member.name != null) {
			String safeName = this.member.name.replace(" ", "_");
			this.minecraft.player.connection.sendCommand("town revokelandall " + safeName);
		}
		this.minecraft.gui.setScreen(this.parent);
	}
}
