package com.siegedempires.client.gui;

import com.siegedempires.banner.CustomBannerDesign;
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
 * Create Empire designer — Edit Banner opens the paint-style 20×40 editor.
 */
public class CreateEmpireScreen extends Screen {
	private static final int CONTENT_WIDTH = BannerEditorWidgets.CONTENT_WIDTH;
	private static final int SCROLL_AREA_TOP = 28;
	private static final int FOOTER_HEIGHT = 56;
	private static final int FIELD_HEIGHT = 20;
	private static final int LABEL_GAP = 4;
	private static final int SECTION_GAP = 14;
	private static final int DESC_LINE_COUNT = 4;
	private static final int DESC_BOX_HEIGHT = DESC_LINE_COUNT * 9 + AbstractTextAreaWidget.DEFAULT_TOTAL_PADDING;
	private static final int DESC_CHAR_LIMIT = 526;

	private EditBox empireNameBox;
	private MultiLineEditBox descriptionBox;
	private String cachedEmpireName = "";
	private String cachedDescription = "";
	private final List<String> bannerPatterns = new ArrayList<>();
	private String bannerBaseColor = "white";
	private String bannerPixels = "";

	private int scrollOffset = 0;
	private int maxScroll = 0;
	private String errorMessage = "";
	private int errorTicks = 0;

	private final Screen parent;
	private boolean usedTownBanner = false;
	private int previewX;
	private int previewY;

	public CreateEmpireScreen(Screen parent) {
		super(Component.translatable("gui.siegedempires.create_empire_title"));
		this.parent = parent;
	}

	public CreateEmpireScreen(Screen parent, String cachedEmpireName, String cachedDescription,
			List<String> bannerPatterns, String bannerBaseColor, String errorMessage) {
		this(parent, cachedEmpireName, cachedDescription, bannerPatterns, bannerBaseColor, null, errorMessage);
	}

	public CreateEmpireScreen(Screen parent, String cachedEmpireName, String cachedDescription,
			List<String> bannerPatterns, String bannerBaseColor, String bannerPixels, String errorMessage) {
		this(parent);
		this.cachedEmpireName = cachedEmpireName;
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

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.use_town_banner"),
				btn -> onUseTownBanner()
		).bounds(centerX - 100, y, 200, 20).build());
		y += FIELD_HEIGHT + SECTION_GAP;

		y = addBannerSection(contentLeft, centerX, y);

		Component descLabel = Component.translatable("gui.siegedempires.empire_description");
		int dlw = font.width(descLabel);
		this.addRenderableWidget(new StringWidget(contentLeft, y, dlw, font.lineHeight, descLabel, font));
		y += font.lineHeight + LABEL_GAP;

		descriptionBox = MultiLineEditBox.builder()
				.setX(contentLeft)
				.setY(y)
				.build(font, CONTENT_WIDTH, DESC_BOX_HEIGHT,
						Component.translatable("gui.siegedempires.empire_description"));
		descriptionBox.setCharacterLimit(DESC_CHAR_LIMIT);
		descriptionBox.setLineLimit(DESC_LINE_COUNT);
		descriptionBox.setValue(cachedDescription);
		descriptionBox.setValueListener(text -> cachedDescription = text);
		this.addRenderableWidget(descriptionBox);
		y += DESC_BOX_HEIGHT + 4 + font.lineHeight + SECTION_GAP;

		maxScroll = Math.max(0, y + scrollOffset - (this.height - FOOTER_HEIGHT - 20));

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.back"),
				btn -> onClose()
		).bounds(centerX - 100, this.height - 52, 200, 20).build());

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.create_empire"),
				btn -> onCreateEmpire()
		).bounds(centerX - 100, this.height - 28, 200, 20).build());
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
		if (empireNameBox != null) cachedEmpireName = empireNameBox.getValue();
		if (descriptionBox != null) cachedDescription = descriptionBox.getValue();
		CustomBannerDesign initial = CustomBannerDesign.fromStoredOrSolid(bannerPixels, bannerBaseColor);
		if (minecraft != null) {
			minecraft.gui.setScreen(new BannerPaintScreen(this, initial, design -> {
				bannerPixels = design.encode();
				bannerBaseColor = design.dominantColorName();
				usedTownBanner = false;
			}));
		}
	}

	private void onUseTownBanner() {
		usedTownBanner = true;
		init();
	}

	private void onCreateEmpire() {
		if (minecraft == null || minecraft.player == null) return;
		String empireName = empireNameBox.getValue().trim();
		if (empireName.isEmpty()) {
			showError(Component.translatable("gui.siegedempires.empire_name_required").getString());
			return;
		}
		String description = descriptionBox != null ? descriptionBox.getValue() : cachedDescription;
		minecraft.gui.setScreen(new ConfirmCreateEmpireScreen(parent, empireName,
				description, bannerPatterns, bannerBaseColor, bannerPixels, usedTownBanner));
	}

	private void showError(String message) {
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
			graphics.text(this.font, Component.literal(errorMessage),
					centerX - errorWidth / 2, this.height - 68, 0xFF5555);
		}
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
