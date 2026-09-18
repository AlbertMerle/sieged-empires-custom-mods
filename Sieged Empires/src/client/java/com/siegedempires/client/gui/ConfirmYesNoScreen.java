package com.siegedempires.client.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Yes/No confirmation overlay with a visible task message.
 *
 * <p>Extends vanilla {@link ConfirmScreen} so title + message use the same
 * {@link MultiLineTextWidget} / {@link LinearLayout} pipeline as Minecraft itself.
 */
public abstract class ConfirmYesNoScreen extends ConfirmScreen {
	protected final Screen parent;
	private final Component[] extraMessages;

	protected ConfirmYesNoScreen(Screen parent, Component title, Component primaryMessage, Component... extraMessages) {
		super(
				confirmed -> { },
				title,
				primaryMessage,
				Component.translatable("gui.siegedempires.yes"),
				Component.translatable("gui.siegedempires.no")
		);
		this.parent = parent;
		this.extraMessages = extraMessages;
	}

	@Override
	protected void addAdditionalText() {
		for (Component extra : this.extraMessages) {
			if (extra != null && !extra.getString().isEmpty()) {
				this.layout.addChild(createMessageWidget(extra));
			}
		}
	}

	@Override
	protected void addButtons(LinearLayout buttonRow) {
		this.yesButton = buttonRow.addChild(Button.builder(
				Component.translatable("gui.siegedempires.yes"),
				button -> onConfirm()
		).build());
		this.noButton = buttonRow.addChild(Button.builder(
				Component.translatable("gui.siegedempires.no"),
				button -> onDeny()
		).build());
	}

	private MultiLineTextWidget createMessageWidget(Component message) {
		return new MultiLineTextWidget(message, this.font)
				.setMaxWidth(Math.max(200, this.width - 50))
				.setMaxRows(15)
				.setCentered(true);
	}

	protected void onDeny() {
		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(this.parent);
		}
	}

	protected abstract void onConfirm();

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
