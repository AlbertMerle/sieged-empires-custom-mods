package com.siegedempires.client.gui;

import com.siegedempires.network.DiplomacyData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AllyOptionsScreen extends Screen {
	private final Screen parent;
	private final DiplomacyData.FactionInfo ally;

	public AllyOptionsScreen(Screen parent, DiplomacyData.FactionInfo ally) {
		super(Component.translatable("gui.siegedempires.ally_options_title", ally.name));
		this.parent = parent;
		this.ally = ally;
	}

	@Override
	protected void init() {
		super.init();
		int centerX = this.width / 2;
		int y = this.height / 2 - 40;

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.request_trade_agreement"),
				button -> {
					DiplomacyGuiHelper.send("traderequest", ally.entityType, ally.name);
					onClose();
				}
		).bounds(centerX - 100, y, 200, 20).build());

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.request_open_borders"),
				button -> {
					DiplomacyGuiHelper.send("bordersrequest", ally.entityType, ally.name);
					onClose();
				}
		).bounds(centerX - 100, y + 28, 200, 20).build());

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.remove_ally").withStyle(ChatFormatting.RED),
				button -> minecraft.gui.setScreen(new ConfirmRemoveAllyScreen(parent, ally))
		).bounds(centerX - 100, y + 56, 200, 20).build());

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.back"),
				button -> onClose()
		).bounds(centerX - 100, this.height - 28, 200, 20).build());
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
