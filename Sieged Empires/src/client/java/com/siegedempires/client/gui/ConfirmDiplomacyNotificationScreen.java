package com.siegedempires.client.gui;

import com.siegedempires.network.DiplomacyData;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConfirmDiplomacyNotificationScreen extends ConfirmYesNoScreen {
	private final DiplomacyData.PendingNotification notification;

	public ConfirmDiplomacyNotificationScreen(Screen parent, DiplomacyData.PendingNotification notification) {
		super(parent,
				Component.translatable("gui.siegedempires.diplomacy_confirm_title"),
				confirmMessage(notification));
		this.notification = notification;
	}

	private static Component confirmMessage(DiplomacyData.PendingNotification notification) {
		return switch (notification.inviteType) {
			case "trade" -> Component.translatable("gui.siegedempires.trade_accept_confirm", notification.name);
			case "open_borders" ->
					Component.translatable("gui.siegedempires.open_borders_accept_confirm", notification.name);
			case "peace" -> Component.translatable("gui.siegedempires.peace_accept_confirm", notification.name);
			default -> Component.translatable("gui.siegedempires.ally_request_confirm", notification.name);
		};
	}

	@Override
	protected void onConfirm() {
		DiplomacyGuiHelper.acceptNotification(
				this.notification.inviteType, this.notification.entityType, this.notification.name);
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	protected void onDeny() {
		DiplomacyGuiHelper.declineNotification(
				this.notification.inviteType, this.notification.entityType, this.notification.name);
		this.minecraft.gui.setScreen(this.parent);
	}
}
