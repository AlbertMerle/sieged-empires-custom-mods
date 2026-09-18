package com.siegedempires.client.gui;

import com.siegedempires.banner.CustomBannerDesign;
import com.siegedempires.client.network.ClientGuiData;
import com.siegedempires.client.network.ClientNetworking;
import com.siegedempires.network.payload.UpdateEmpireResultPayload;
import com.siegedempires.util.FactionEditCost;
import com.siegedempires.util.InventoryHelper;
import com.siegedempires.util.NameValidator;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Edit empire name + banner (Create Empire layout without description / use-town-banner).
 */
public class EditEmpireScreen extends Screen {
	private static final int CONTENT_WIDTH = BannerEditorWidgets.CONTENT_WIDTH;
	private static final int SCROLL_AREA_TOP = 28;
	private static final int FOOTER_HEIGHT = 56;
	private static final int LABEL_GAP = 4;
	private static final int FIELD_HEIGHT = 20;
	private static final int SECTION_GAP = 14;

	private EditBox empireNameBox;
	private String cachedEmpireName = "";
	private final List<String> bannerPatterns = new ArrayList<>();
	private String bannerBaseColor = "white";
	private String bannerPixels = "";
	private int editCost = FactionEditCost.BASE_COST;

	private int scrollOffset = 0;
	private int maxScroll = 0;
	private String errorMessage = "";
	private int errorTicks = 0;

	private final Screen parent;
	private int previewX;
	private int previewY;
	private Consumer<UpdateEmpireResultPayload> resultListener;

	public EditEmpireScreen(Screen parent) {
		super(Component.translatable("gui.siegedempires.edit_empire_title"));
		this.parent = parent;
		var data = ClientGuiData.getManageEmpireData();
		if (data != null) {
			if (data.empireName != null) {
				cachedEmpireName = data.empireName;
			}
			editCost = FactionEditCost.costForChunks(data.claimedChunks);
			if (data.bannerPatterns != null) {
				bannerPatterns.addAll(data.bannerPatterns);
			}
			bannerBaseColor = data.bannerBaseColor == null || data.bannerBaseColor.isEmpty()
					? "white" : data.bannerBaseColor;
			bannerPixels = data.bannerPixels == null ? "" : data.bannerPixels;
		}
	}

	@Override
	protected void init() {
		super.init();
		clearWidgets();
		int centerX = this.width / 2;
		int contentLeft = centerX - CONTENT_WIDTH / 2;
		int y = SCROLL_AREA_TOP - scrollOffset;

		Component nameLabel = Component.translatable("gui.siegedempires.empire_name");
		int nlw = font.width(nameLabel);
		this.addRenderableWidget(new StringWidget(contentLeft, y, nlw, font.lineHeight, nameLabel, font));
		y += font.lineHeight + LABEL_GAP;

		empireNameBox = new EditBox(font, contentLeft, y, CONTENT_WIDTH, FIELD_HEIGHT, Component.empty());
		empireNameBox.setMaxLength(64);
		empireNameBox.setValue(cachedEmpireName);
		empireNameBox.setResponder(text -> cachedEmpireName = text);
		this.addRenderableWidget(empireNameBox);
		y += FIELD_HEIGHT + SECTION_GAP;

		y = addBannerSection(contentLeft, centerX, y);
		maxScroll = Math.max(0, y + scrollOffset - (this.height - FOOTER_HEIGHT - 20));

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.back"),
				btn -> onClose()
		).bounds(centerX - 100, this.height - 52, 200, 20).build());

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.update_empire").withStyle(ChatFormatting.GREEN),
				btn -> onUpdateEmpire()
		).bounds(centerX - 100, this.height - 28, 200, 20).build());

		resultListener = this::onUpdateResult;
		ClientNetworking.setUpdateEmpireResultListener(resultListener);
	}

	private int addBannerSection(int contentLeft, int centerX, int y) {
		previewX = BannerEditorWidgets.computePreviewX(contentLeft);
		previewY = y + 20;
		return BannerEditorWidgets.addEditBannerButton(
				this::addRenderableWidget,
				this::openBannerPaintEditor,
				contentLeft, centerX, y);
	}

	private void openBannerPaintEditor() {
		if (empireNameBox != null) {
			cachedEmpireName = empireNameBox.getValue();
		}
		CustomBannerDesign initial = CustomBannerDesign.fromStoredOrSolid(bannerPixels, bannerBaseColor);
		if (minecraft != null) {
			minecraft.gui.setScreen(new BannerPaintScreen(this, initial, design -> {
				bannerPixels = design.encode();
				bannerBaseColor = design.dominantColorName();
			}));
		}
	}

	private void onUpdateEmpire() {
		if (minecraft == null || minecraft.player == null) {
			return;
		}
		String empireName = empireNameBox != null ? empireNameBox.getValue().trim() : cachedEmpireName.trim();
		if (empireName.isEmpty()) {
			showError(Component.translatable("gui.siegedempires.empire_name_required").getString());
			return;
		}
		String validationError = NameValidator.getValidationError(empireName);
		if (validationError != null) {
			showError(validationError);
			return;
		}
		if (!InventoryHelper.hasEnoughGold(minecraft.player, editCost)) {
			GuiNotifications.showCantAfford(minecraft);
			return;
		}
		cachedEmpireName = empireName;
		ClientNetworking.updateEmpire(empireName, bannerBaseColor, bannerPatterns, bannerPixels);
	}

	private void onUpdateResult(UpdateEmpireResultPayload payload) {
		if (payload == null) {
			return;
		}
		if (!payload.success()) {
			if ("Can't Afford This!".equals(payload.errorMessage())) {
				GuiNotifications.showCantAfford(minecraft);
			} else {
				showError(payload.errorMessage());
			}
			return;
		}
		GuiNotifications.playXpSound(minecraft);
		if (minecraft != null) {
			ClientNetworking.requestManageEmpireData();
			minecraft.gui.setScreen(parent);
		}
	}

	private void showError(String message) {
		GuiNotifications.playErrorSound(minecraft);
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
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (maxScroll > 0) {
			scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) scrollY * 10));
			init();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int centerX = this.width / 2;
		int contentLeft = centerX - CONTENT_WIDTH / 2;
		int footerTop = this.height - FOOTER_HEIGHT;
		graphics.fill(contentLeft - 4, SCROLL_AREA_TOP - 2, contentLeft + CONTENT_WIDTH + 4, footerTop, 0x88000000);

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		graphics.text(this.font, this.title, centerX - this.font.width(this.title) / 2, 8, 0xFFFFFF);

		BannerEditorWidgets.drawBannerPreview(graphics, bannerPatterns, bannerBaseColor, bannerPixels,
				previewX, previewY + 15);

		if (!errorMessage.isEmpty()) {
			int errorWidth = this.font.width(errorMessage);
			graphics.text(this.font, Component.literal(errorMessage),
					centerX - errorWidth / 2, this.height - 68, 0xFF5555);
		}
	}

	@Override
	public void onClose() {
		ClientNetworking.setUpdateEmpireResultListener(null);
		if (minecraft != null) {
			minecraft.gui.setScreen(parent);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
