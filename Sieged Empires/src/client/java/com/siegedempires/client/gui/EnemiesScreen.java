package com.siegedempires.client.gui;

import com.siegedempires.client.network.ClientGuiData;
import com.siegedempires.client.network.ClientNetworking;
import com.siegedempires.network.DiplomacyData;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class EnemiesScreen extends Screen {
	private static final int BUTTON_WIDTH = 200;
	private static final int BUTTON_HEIGHT = 20;
	private static final int PANEL_WIDTH = 220;
	private static final int FOOTER_BUTTON_GAP = 8;
	private static final int BOTTOM_MARGIN = 8;
	private static final int PANEL_ABOVE_ACTION_GAP = 12;
	private static final int PANEL_TOP = 36;
	private static final int MIN_PANEL_HEIGHT = 80;

	private final Screen parent;
	private int scroll;
	private int panelX;
	private int panelY;
	private int panelHeight;

	public EnemiesScreen(Screen parent) {
		super(Component.translatable("gui.siegedempires.enemies_title"));
		this.parent = parent;
	}

	private int backButtonY() {
		return this.height - BOTTOM_MARGIN - BUTTON_HEIGHT;
	}

	private int declareWarButtonY() {
		return backButtonY() - FOOTER_BUTTON_GAP - BUTTON_HEIGHT;
	}

	private void layoutPanels() {
		int centerX = this.width / 2;
		panelX = centerX - PANEL_WIDTH / 2;
		panelY = PANEL_TOP;
		int panelBottom = declareWarButtonY() - PANEL_ABOVE_ACTION_GAP;
		panelHeight = Math.max(MIN_PANEL_HEIGHT, panelBottom - panelY);
	}

	@Override
	protected void init() {
		super.init();
		clearWidgets();

		ClientGuiData.setDiplomacyListener(this::onDiplomacyUpdated);
		ClientNetworking.requestDiplomacyData();

		layoutPanels();

		int centerX = this.width / 2;
		DiplomacyData data = ClientGuiData.getDiplomacyData();
		if (data != null && data.canManage) {
			this.addRenderableWidget(Button.builder(
					Component.translatable("gui.siegedempires.declare_war")
							.withStyle(net.minecraft.ChatFormatting.DARK_PURPLE, net.minecraft.ChatFormatting.BOLD),
					button -> minecraft.gui.setScreen(EmpireSelectionScreen.forDeclareWar(this))
			).bounds(centerX - BUTTON_WIDTH / 2, declareWarButtonY(), BUTTON_WIDTH, BUTTON_HEIGHT).build());
		}

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.back"),
				button -> onClose()
		).bounds(centerX - BUTTON_WIDTH / 2, backButtonY(), BUTTON_WIDTH, BUTTON_HEIGHT).build());
	}

	private void onDiplomacyUpdated() {
		scroll = 0;
		layoutPanels();
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		DiplomacyData data = ClientGuiData.getDiplomacyData();
		if (data == null || !data.canManage || data.enemies == null) {
			return super.mouseClicked(event, doubleClick);
		}

		int listTop = panelY + DiplomacyPanelRenderer.PANEL_PADDING + this.font.lineHeight + 4;
		int listLeft = panelX + DiplomacyPanelRenderer.PANEL_PADDING;
		int listRight = panelX + PANEL_WIDTH - DiplomacyPanelRenderer.PANEL_PADDING
				- DiplomacyPanelRenderer.SCROLLBAR_WIDTH - 2;
		int maxScroll = DiplomacyPanelRenderer.maxScroll(panelHeight, data.enemies);
		int clampedScroll = Math.max(0, Math.min(maxScroll, scroll));
		int rowY = listTop - clampedScroll;

		for (DiplomacyData.FactionInfo enemy : data.enemies) {
			if (event.y() >= rowY && event.y() < rowY + DiplomacyPanelRenderer.ROW_HEIGHT
					&& event.x() >= listLeft && event.x() <= listRight) {
				minecraft.gui.setScreen(new EnemyOptionsScreen(this, enemy));
				return true;
			}
			rowY += DiplomacyPanelRenderer.ROW_HEIGHT + DiplomacyPanelRenderer.ROW_GAP;
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		DiplomacyData data = ClientGuiData.getDiplomacyData();
		if (data != null && DiplomacyPanelRenderer.isMouseOverPanel(
				mouseX, mouseY, panelX, panelY, PANEL_WIDTH, panelHeight)) {
			int maxScroll = DiplomacyPanelRenderer.maxScroll(panelHeight, data.enemies);
			scroll = DiplomacyPanelRenderer.scrollBy(scroll, maxScroll, scrollY);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		layoutPanels();

		int centerX = this.width / 2;
		graphics.text(this.font, this.title, centerX - this.font.width(this.title) / 2, 8, 0xFFFFFFFF);

		DiplomacyData data = ClientGuiData.getDiplomacyData();
		if (data == null) {
			Component loading = Component.translatable("gui.siegedempires.loading");
			graphics.text(this.font, loading, centerX - this.font.width(loading) / 2, this.height / 2, 0xFFAAAAAA);
			return;
		}

		DiplomacyPanelRenderer.drawPanel(graphics, this.font,
				panelX, panelY, PANEL_WIDTH, panelHeight,
				Component.translatable("gui.siegedempires.enemies"),
				0xFFFF5555, data.enemies, 0xFFFF5555, scroll);
	}

	@Override
	public void onClose() {
		ClientGuiData.setDiplomacyListener(null);
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
