package com.siegedempires.client.gui;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Yes/No delete confirmation that requires typing the exact town/empire name.
 * Yes stays inactive (darkened) until the textbox matches; No always returns to parent.
 */
public abstract class ConfirmNameDeleteScreen extends ConfirmYesNoScreen {
	private static final int NAME_FIELD_WIDTH = 280;
	private static final int NAME_FIELD_HEIGHT = 20;

	private final String requiredName;
	private EditBox nameBox;

	protected ConfirmNameDeleteScreen(Screen parent, Component title, Component areYouSureMessage,
	                                  Component writePrompt, String requiredName) {
		super(parent, title, areYouSureMessage, writePrompt);
		this.requiredName = requiredName != null ? requiredName : "";
	}

	@Override
	protected void addAdditionalText() {
		super.addAdditionalText();

		this.nameBox = new EditBox(this.font, NAME_FIELD_WIDTH, NAME_FIELD_HEIGHT,
				Component.translatable("gui.siegedempires.delete_confirm_name_field"));
		this.nameBox.setMaxLength(Math.max(64, this.requiredName.length()));
		if (!this.requiredName.isEmpty()) {
			// Unfocused empty field still shows the name; focused empty uses suggestion below.
			this.nameBox.setHint(Component.literal(this.requiredName));
		}
		this.nameBox.setResponder(this::onNameTyped);
		updateGhostSuggestion("");
		this.layout.addChild(this.nameBox);
	}

	@Override
	protected void addButtons(LinearLayout buttonRow) {
		super.addButtons(buttonRow);
		if (this.yesButton != null) {
			this.yesButton.active = isNameMatched("");
		}
		if (this.nameBox != null) {
			this.setInitialFocus(this.nameBox);
		}
	}

	private void onNameTyped(String text) {
		updateGhostSuggestion(text);
		if (this.yesButton != null) {
			this.yesButton.active = isNameMatched(text);
		}
	}

	/** Dark-grey ghost suffix of the required name while the typed prefix matches. */
	private void updateGhostSuggestion(String text) {
		if (this.nameBox == null || this.requiredName.isEmpty()) {
			if (this.nameBox != null) {
				this.nameBox.setSuggestion(null);
			}
			return;
		}
		if (text.isEmpty()) {
			this.nameBox.setSuggestion(this.requiredName);
		} else if (this.requiredName.startsWith(text) && text.length() < this.requiredName.length()) {
			this.nameBox.setSuggestion(this.requiredName.substring(text.length()));
		} else {
			this.nameBox.setSuggestion(null);
		}
	}

	private boolean isNameMatched(String text) {
		return !this.requiredName.isEmpty() && this.requiredName.equals(text);
	}

	@Override
	protected final void onConfirm() {
		if (this.nameBox == null || !isNameMatched(this.nameBox.getValue())) {
			return;
		}
		performDelete();
	}

	protected abstract void performDelete();
}
