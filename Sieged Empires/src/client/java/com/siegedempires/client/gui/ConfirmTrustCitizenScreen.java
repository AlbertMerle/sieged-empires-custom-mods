package com.siegedempires.client.gui;

import com.siegedempires.network.ManageTownData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConfirmTrustCitizenScreen extends ConfirmYesNoScreen {
	private final ManageTownData.MemberInfo member;

	public ConfirmTrustCitizenScreen(Screen parent, ManageTownData.MemberInfo member) {
		super(parent,
				Component.translatable("gui.siegedempires.trust_citizen_confirm_title"),
				Component.translatable("gui.siegedempires.trust_citizen_confirm_message", member.name),
				Component.empty()
						.append(Component.translatable("gui.siegedempires.trust_warning_before")
								.withStyle(ChatFormatting.BOLD))
						.append(Component.translatable("gui.siegedempires.trust_warning_except")
								.withStyle(ChatFormatting.BOLD, ChatFormatting.RED))
						.append(Component.translatable("gui.siegedempires.trust_warning_after")
								.withStyle(ChatFormatting.BOLD)));
		this.member = member;
	}

	@Override
	protected void onConfirm() {
		if (this.minecraft != null && this.minecraft.player != null) {
			String safeName = this.member.name.replace(" ", "_");
			this.minecraft.player.connection.sendCommand("town trust " + safeName);
		}
		this.minecraft.gui.setScreen(this.parent);
	}
}
