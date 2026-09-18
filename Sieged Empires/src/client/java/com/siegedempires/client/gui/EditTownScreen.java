package com.siegedempires.client.gui;

import com.siegedempires.banner.CustomBannerDesign;
import com.siegedempires.client.network.ClientGuiData;
import com.siegedempires.client.network.ClientNetworking;
import com.siegedempires.network.payload.RenameTownResultPayload;
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
 * Edit town name + banner (Create Town layout without description).
 * Submits via Update Town and charges {@link FactionEditCost}.
 */
public class EditTownScreen extends Screen {
	private static final int CONTENT_WIDTH = BannerEditorWidgets.CONTENT_WIDTH;
	private static final int SCROLL_AREA_TOP = 28;
	private static final int FOOTER_HEIGHT = 56;
	private static final int LABEL_GAP = 4;
	private static final int FIELD_HEIGHT = 20;
	private static final int SECTION_GAP = 14;

	private EditBox townNameBox;
	private String cachedTownName = "";
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
	private Consumer<RenameTownResultPayload> resultListener;

	public EditTownScreen(Screen parent) {
		super(Component.translatable("gui.siegedempires.edit_town_title"));
		this.parent = parent;
		var data = ClientGuiData.getManageTownData();
		if (data != null) {
			if (data.townName != null) {
				cachedTownName = data.townName;
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

	public EditTownScreen(Screen parent, String cachedTownName,
			List<String> bannerPatterns, String bannerBaseColor, String bannerPixels,
			int editCost, String errorMessage) {
		this(parent);
		this.cachedTownName = cachedTownName == null ? "" : cachedTownName;
		this.bannerPatterns.clear();
		if (bannerPatterns != null) {
			this.bannerPatterns.addAll(bannerPatterns);
		}
		this.bannerBaseColor = bannerBaseColor == null || bannerBaseColor.isEmpty() ? "white" : bannerBaseColor;
		this.bannerPixels = bannerPixels == null ? "" : bannerPixels;
		this.editCost = Math.max(FactionEditCost.BASE_COST, editCost);
		this.errorMessage = errorMessage == null ? "" : errorMessage;
		this.errorTicks = this.errorMessage.isEmpty() ? 0 : 100;
	}

	@Override
	protected void init() {
		super.init();
		clearWidgets();

		int centerX = this.width / 2;
		int contentLeft = centerX - CONTENT_WIDTH / 2;
		int y = SCROLL_AREA_TOP - scrollOffset;

		y = addTownNameSection(contentLeft, y);
		y += SECTION_GAP;
		y = addBannerSection(contentLeft, centerX, y);

		maxScroll = Math.max(0, y + scrollOffset - (this.height - FOOTER_HEIGHT - 20));

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.back"),
				button -> onClose()
		).bounds(centerX - 100, this.height - 52, 200, 20).build());

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.update_town").withStyle(ChatFormatting.GREEN),
				button -> onUpdateTown()
		).bounds(centerX - 100, this.height - 28, 200, 20).build());

		resultListener = this::onUpdateResult;
		ClientNetworking.setRenameTownResultListener(resultListener);
	}

	private int addTownNameSection(int contentLeft, int y) {
		int labelH = this.font.lineHeight;
		int sectionHeight = labelH + LABEL_GAP + FIELD_HEIGHT;
		if (isVisible(y, sectionHeight)) {
			Component townNameLabel = Component.translatable("gui.siegedempires.town_name");
			this.addRenderableWidget(new StringWidget(
					contentLeft, y, this.font.width(townNameLabel), labelH,
					townNameLabel, this.font));
			this.townNameBox = new EditBox(this.font, contentLeft, y + labelH + LABEL_GAP, CONTENT_WIDTH, FIELD_HEIGHT,
					Component.translatable("gui.siegedempires.town_name"));
			this.townNameBox.setMaxLength(40);
			this.townNameBox.setValue(cachedTownName);
			this.townNameBox.setResponder(text -> cachedTownName = text);
			this.addRenderableWidget(this.townNameBox);
		} else {
			this.townNameBox = null;
		}
		return y + sectionHeight;
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
		if (townNameBox != null) {
			cachedTownName = townNameBox.getValue();
		}
		CustomBannerDesign initial = CustomBannerDesign.fromStoredOrSolid(bannerPixels, bannerBaseColor);
		if (minecraft != null) {
			minecraft.gui.setScreen(new BannerPaintScreen(this, initial, design -> {
				bannerPixels = design.encode();
				bannerBaseColor = design.dominantColorName();
			}));
		}
	}

	private boolean isVisible(int y, int height) {
		return y + height >= SCROLL_AREA_TOP && y <= this.height - FOOTER_HEIGHT;
	}

	private void onUpdateTown() {
		if (minecraft == null || minecraft.player == null) {
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
		if (!InventoryHelper.hasEnoughGold(minecraft.player, editCost)) {
			GuiNotifications.showCantAfford(minecraft);
			return;
		}
		cachedTownName = townName;
		ClientNetworking.updateTown(townName, bannerBaseColor, bannerPatterns, bannerPixels);
	}

	private void onUpdateResult(RenameTownResultPayload payload) {
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
			ClientNetworking.requestManageTownData();
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
		ClientNetworking.setRenameTownResultListener(null);
		if (minecraft != null) {
			minecraft.gui.setScreen(parent);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
