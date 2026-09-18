package com.siegedempires.client.gui;

import com.siegedempires.network.DiplomacyData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class EnemyOptionsScreen extends Screen {
	private final Screen parent;
	private final DiplomacyData.FactionInfo enemy;

	public EnemyOptionsScreen(Screen parent, DiplomacyData.FactionInfo enemy) {
		super(Component.translatable("gui.siegedempires.enemy_options_title", enemy.name));
		this.parent = parent;
		this.enemy = enemy;
	}

	@Override
	protected void init() {
		super.init();
		int centerX = this.width / 2;
		int y = this.height / 2 - 20;

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.request_peace").withStyle(ChatFormatting.GREEN),
				button -> {
					DiplomacyGuiHelper.send("peacerequest", enemy.entityType, enemy.name);
					onClose();
				}
		).bounds(centerX - 100, y, 200, 20).build());

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
