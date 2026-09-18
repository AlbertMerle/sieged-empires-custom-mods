package com.siegedempires.client.gui;

import com.siegedempires.client.network.ClientGuiData;
import com.siegedempires.client.network.ClientNetworking;
import com.siegedempires.network.payload.RenameTownResultPayload;
import com.siegedempires.util.NameValidator;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class RenameTownScreen extends Screen {
	public static final int COST = com.siegedempires.command.TownCommand.RENAME_COST;

	private static final int FIELD_WIDTH = 280;
	private static final int FIELD_HEIGHT = 20;
	private static final int LABEL_ABOVE_FIELD = 24;
	private static final int FOOTER_BUTTON_WIDTH = 100;
	private static final int FOOTER_BUTTON_HEIGHT = 20;
	private static final int FOOTER_BUTTON_GAP = 20;

	private static String cachedTownName = "";

	private final Screen parent;
	private EditBox townNameBox;
	private String errorMessage = "";
	private int errorTicks = 0;
	private Consumer<RenameTownResultPayload> resultListener;

	public RenameTownScreen(Screen parent) {
		super(Component.translatable("gui.siegedempires.rename_town_title"));
		this.parent = parent;
	}

	public static void clearCache() {
		cachedTownName = "";
	}

	@Override
	protected void init() {
		super.init();
		clearWidgets();

		if (cachedTownName.isEmpty()) {
			var data = ClientGuiData.getManageTownData();
			if (data != null && data.townName != null && !data.townName.isEmpty()) {
				cachedTownName = data.townName;
			}
		}

		int centerX = this.width / 2;
		int centerY = this.height / 2;
		int fieldY = centerY - FIELD_HEIGHT / 2;

		townNameBox = new EditBox(this.font, centerX - FIELD_WIDTH / 2, fieldY, FIELD_WIDTH, FIELD_HEIGHT,
				Component.translatable("gui.siegedempires.town_name"));
		townNameBox.setMaxLength(40);
		townNameBox.setValue(cachedTownName);
		townNameBox.setResponder(text -> cachedTownName = text);
		this.addRenderableWidget(townNameBox);
		this.setInitialFocus(townNameBox);

		int footerY = this.height - 28;
		int totalFooterWidth = FOOTER_BUTTON_WIDTH * 2 + FOOTER_BUTTON_GAP;
		int footerLeft = centerX - totalFooterWidth / 2;

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.back").withStyle(ChatFormatting.RED),
				button -> onBack()
		).bounds(footerLeft, footerY, FOOTER_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT).build());

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.rename_action").withStyle(ChatFormatting.GREEN),
				button -> onRename()
		).bounds(footerLeft + FOOTER_BUTTON_WIDTH + FOOTER_BUTTON_GAP, footerY,
				FOOTER_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT).build());

		resultListener = this::onRenameResult;
		ClientNetworking.setRenameTownResultListener(resultListener);
	}

	private void onBack() {
		clearCache();
		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(parent);
		}
	}

	private void onRename() {
		if (this.minecraft == null || this.minecraft.player == null) {
			return;
		}

		String townName = townNameBox != null ? townNameBox.getValue().trim() : cachedTownName.trim();
		if (townName.isEmpty()) {
			showError(Component.translatable("gui.siegedempires.town_name_required").getString());
			return;
		}

		String validationError = NameValidator.getValidationError(townName);
		if (validationError != null) {
			showError(NameValidator.hasDisallowedCharacters(townName)
					? Component.translatable("gui.siegedempires.town_name_invalid").getString()
					: validationError);
			return;
		}

		cachedTownName = townName;
		ClientNetworking.renameTown(townName);
	}

	private void onRenameResult(RenameTownResultPayload payload) {
		if (payload == null) {
			return;
		}
		if (!payload.success()) {
			showError(payload.errorMessage());
			return;
		}

		GuiNotifications.playXpSound(this.minecraft);
		clearCache();
		if (this.minecraft != null) {
			ClientNetworking.requestManageTownData();
			this.minecraft.gui.setScreen(parent);
		}
	}

	private void showError(String message) {
		GuiNotifications.playErrorSound(this.minecraft);
		errorMessage = message;
		errorTicks = 100;
	}

	@Override
	public void tick() {
		super.tick();
		if (errorTicks > 0) {
			errorTicks--;
			if (errorTicks == 0) {
				errorMessage = "";
			}
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		int centerX = this.width / 2;
		int centerY = this.height / 2;
		int labelY = centerY - LABEL_ABOVE_FIELD - FIELD_HEIGHT;

		Component label = Component.translatable("gui.siegedempires.rename_town_prompt");
		graphics.text(this.font, label, centerX - this.font.width(label) / 2, labelY, 0xFFFFFF);

		if (!errorMessage.isEmpty()) {
			int errorWidth = this.font.width(errorMessage);
			graphics.text(this.font, Component.literal(errorMessage),
					centerX - errorWidth / 2, centerY + FIELD_HEIGHT + 20, 0xFF5555);
		}
	}

	@Override
	public void onClose() {
		ClientNetworking.setRenameTownResultListener(null);
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
