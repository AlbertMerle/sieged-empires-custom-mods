package com.siegedempires.client.gui;

import com.siegedempires.network.ManageTownData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConfirmMakeLordScreen extends ConfirmYesNoScreen {
	private final ManageTownData.MemberInfo member;

	public ConfirmMakeLordScreen(Screen parent, ManageTownData.MemberInfo member) {
		super(parent,
				Component.translatable("gui.siegedempires.make_lord_confirm_title"),
				Component.translatable("gui.siegedempires.make_lord_confirm_message", member.name),
				Component.empty()
						.append(Component.translatable("gui.siegedempires.lord_warning_before")
								.withStyle(ChatFormatting.BOLD))
						.append(Component.translatable("gui.siegedempires.lord_warning_including")
								.withStyle(ChatFormatting.BOLD, ChatFormatting.RED))
						.append(Component.translatable("gui.siegedempires.lord_warning_after")
								.withStyle(ChatFormatting.BOLD)));
		this.member = member;
	}

	@Override
	protected void onConfirm() {
		if (this.minecraft != null && this.minecraft.player != null) {
			String safeName = this.member.name.replace(" ", "_");
			this.minecraft.player.connection.sendCommand("town makelord " + safeName);
		}
		this.minecraft.gui.setScreen(this.parent);
	}
}
