package com.siegedempires.client.gui;

import com.siegedempires.banner.CustomBannerDesign;
import com.siegedempires.config.ModSettings;
import com.siegedempires.util.NameValidator;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractTextAreaWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Create Town designer — Edit Banner opens the paint-style 20×40 editor.
 */
public class CreateTownScreen extends Screen {
	private static final int CONTENT_WIDTH = BannerEditorWidgets.CONTENT_WIDTH;
	private static final int SCROLL_AREA_TOP = 28;
	private static final int FOOTER_HEIGHT = 56;
	private static final int LABEL_GAP = 4;
	private static final int FIELD_HEIGHT = 20;
	private static final int SECTION_GAP = 14;
	/** Four lines of text plus MultiLineEditBox inner padding. */
	private static final int DESC_LINE_COUNT = 4;
	private static final int DESC_BOX_HEIGHT = DESC_LINE_COUNT * 9 + AbstractTextAreaWidget.DEFAULT_TOTAL_PADDING;
	private static final int DESC_CHAR_LIMIT = 526;
	/** MultiLineEditBox draws "0/N" at height+4; clear that before the invasion hint. */
	private static final int CHAR_COUNT_RESERVE = 4 + 9 + 6;

	private EditBox townNameBox;
	private MultiLineEditBox descriptionBox;
	private String cachedTownName = "";
	private String cachedDescription = "";
	private final List<String> bannerPatterns = new ArrayList<>();
	private String bannerBaseColor = "white";
	private String bannerPixels = "";

	private int scrollOffset = 0;
	private int maxScroll = 0;
	private String errorMessage = "";
	private int errorTicks = 0;

	private final Screen parent;
	private int previewX;
	private int previewY;

	public CreateTownScreen(Screen parent) {
		super(Component.translatable("gui.siegedempires.create_town_title"));
		this.parent = parent;
	}

	public CreateTownScreen(Screen parent, String cachedTownName, String cachedDescription,
			List<String> bannerPatterns, String bannerBaseColor, String errorMessage) {
		this(parent, cachedTownName, cachedDescription, bannerPatterns, bannerBaseColor, null, errorMessage);
	}

	public CreateTownScreen(Screen parent, String cachedTownName, String cachedDescription,
			List<String> bannerPatterns, String bannerBaseColor, String bannerPixels, String errorMessage) {
		this(parent);
		this.cachedTownName = cachedTownName;
		this.cachedDescription = cachedDescription;
		this.bannerPatterns.addAll(bannerPatterns);
		this.bannerBaseColor = bannerBaseColor == null || bannerBaseColor.isEmpty() ? "white" : bannerBaseColor;
		this.bannerPixels = bannerPixels == null ? "" : bannerPixels;
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
		y = addDescriptionSection(contentLeft, y);

		maxScroll = Math.max(0, y + scrollOffset - (this.height - FOOTER_HEIGHT - 20));

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.back"),
				button -> onClose()
		).bounds(centerX - 100, this.height - 52, 200, 20).build());

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.create_town"),
				button -> onCreateTown()
		).bounds(centerX - 100, this.height - 28, 200, 20).build());
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
		if (townNameBox != null) cachedTownName = townNameBox.getValue();
		if (descriptionBox != null) cachedDescription = descriptionBox.getValue();
		CustomBannerDesign initial = CustomBannerDesign.fromStoredOrSolid(bannerPixels, bannerBaseColor);
		if (minecraft != null) {
			minecraft.gui.setScreen(new BannerPaintScreen(this, initial, design -> {
				bannerPixels = design.encode();
				bannerBaseColor = design.dominantColorName();
			}));
		}
	}

	private int addDescriptionSection(int contentLeft, int y) {
		int labelH = this.font.lineHeight;
		int hintH = this.font.lineHeight;
		int hintGap = 6;
		int sectionHeight = labelH + LABEL_GAP + DESC_BOX_HEIGHT + CHAR_COUNT_RESERVE + hintGap + hintH;
		if (isVisible(y, sectionHeight)) {
			Component descLabel = Component.translatable("gui.siegedempires.town_description");
			this.addRenderableWidget(new StringWidget(
					contentLeft, y, this.font.width(descLabel), labelH,
					descLabel, this.font));

			int boxTop = y + labelH + LABEL_GAP;
			this.descriptionBox = MultiLineEditBox.builder()
					.setX(contentLeft)
					.setY(boxTop)
					.build(this.font, CONTENT_WIDTH, DESC_BOX_HEIGHT,
							Component.translatable("gui.siegedempires.town_description"));
			this.descriptionBox.setCharacterLimit(DESC_CHAR_LIMIT);
			this.descriptionBox.setLineLimit(DESC_LINE_COUNT);
			this.descriptionBox.setValue(cachedDescription);
			this.descriptionBox.setValueListener(text -> cachedDescription = text);
			this.addRenderableWidget(this.descriptionBox);

			int hintY = boxTop + DESC_BOX_HEIGHT + CHAR_COUNT_RESERVE + hintGap;
			int minOnline = ModSettings.get().invasionMinOnlinePlayers;
			Component hint = Component.translatable("gui.siegedempires.town_invasion_hint", minOnline)
					.withStyle(ChatFormatting.YELLOW);
			this.addRenderableWidget(new StringWidget(
					contentLeft, hintY, Math.min(CONTENT_WIDTH, this.font.width(hint)), hintH,
					hint, this.font));
		} else {
			this.descriptionBox = null;
		}
		return y + sectionHeight + SECTION_GAP;
	}

	private boolean isVisible(int y, int height) {
		return y + height >= SCROLL_AREA_TOP && y <= this.height - FOOTER_HEIGHT;
	}

	private void onCreateTown() {
		if (minecraft == null || minecraft.player == null) return;
		String townName = townNameBox != null ? townNameBox.getValue().trim() : "";
		String description = descriptionBox != null ? descriptionBox.getValue() : cachedDescription;
		if (townName.isEmpty()) {
			showInvalidNameError(Component.translatable("gui.siegedempires.town_name_required").getString());
			return;
		}
		String validationError = NameValidator.getValidationError(townName);
		if (validationError != null) {
			showInvalidNameError(NameValidator.hasDisallowedCharacters(townName)
					? Component.translatable("gui.siegedempires.town_name_invalid").getString()
					: validationError);
			return;
		}
		cachedTownName = townName;
		cachedDescription = description;
		minecraft.gui.setScreen(new ConfirmCreateTownScreen(parent, townName, description,
				new ArrayList<>(bannerPatterns), bannerBaseColor, bannerPixels));
	}

	private void showInvalidNameError(String message) {
		GuiNotifications.playErrorSound(minecraft);
		errorMessage = message;
		errorTicks = 100;
	}

	@Override
	public void tick() {
		super.tick();
		if (errorTicks > 0) {
			errorTicks--;
			if (errorTicks == 0) errorMessage = "";
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
			graphics.text(this.font, Component.literal(errorMessage), centerX - errorWidth / 2, this.height - 68, 0xFF5555);
		}
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
