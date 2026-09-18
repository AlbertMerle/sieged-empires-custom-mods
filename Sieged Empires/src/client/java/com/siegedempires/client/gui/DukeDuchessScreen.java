package com.siegedempires.client.gui;

import com.siegedempires.client.network.ClientNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shown to the player being crowned — pick Duke or Duchess. */
public class DukeDuchessScreen extends Screen {
	private final Screen parent;
	private final String wartownId;
	private final String wartownName;
	private final String emperorName;

	public DukeDuchessScreen(Screen parent, String wartownId, String wartownName, String emperorName) {
		super(Component.translatable("gui.siegedempires.duke_duchess_title"));
		this.parent = parent;
		this.wartownId = wartownId;
		this.wartownName = wartownName;
		this.emperorName = emperorName;
	}

	@Override
	protected void init() {
		super.init();

		Component question = Component.translatable("gui.siegedempires.duke_duchess_question", wartownName, emperorName);
		int questionWidth = Math.min(this.width - 40, this.font.width(question));
		this.addRenderableWidget(new StringWidget(
				this.width / 2 - questionWidth / 2, this.height / 2 - 30,
				questionWidth, this.font.lineHeight * 3,
				question, this.font));

		Component pickMessage = Component.translatable("gui.siegedempires.duke_duchess_pick");
		int pickWidth = this.font.width(pickMessage);
		this.addRenderableWidget(new StringWidget(
				this.width / 2 - pickWidth / 2, this.height / 2 - 4,
				pickWidth, this.font.lineHeight,
				pickMessage, this.font));

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.duke"),
				button -> onChoose("King")
		).bounds(this.width / 2 - 105, this.height / 2 + 20, 100, 20).build());

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.duchess"),
				button -> onChoose("Queen")
		).bounds(this.width / 2 + 5, this.height / 2 + 20, 100, 20).build());
	}

	private void onChoose(String monarchTitle) {
		ClientNetworking.chooseDukeDuchess(wartownId, monarchTitle);
		if (minecraft != null) {
			minecraft.gui.setScreen(null);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
