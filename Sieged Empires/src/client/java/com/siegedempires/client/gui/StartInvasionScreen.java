package com.siegedempires.client.gui;

import com.siegedempires.client.network.ClientGuiData;
import com.siegedempires.client.network.ClientNetworking;
import com.siegedempires.network.DiplomacyData;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class StartInvasionScreen extends Screen {
	private static final int BUTTON_WIDTH = 200;
	private static final int BUTTON_HEIGHT = 20;
	private static final int PANEL_WIDTH = 280;
	private static final int PANEL_HEIGHT = 180;
	private static final int INVADE_BUTTON_WIDTH = 52;
	/** Gap between the enemies list panel and the Invade buttons to its right. */
	private static final int INVADE_BUTTON_GAP = 6;

	private final Screen parent;
	private int scroll;
	private int panelX;
	private int panelY;
	private final List<Button> invadeButtons = new ArrayList<>();

	public StartInvasionScreen(Screen parent) {
		super(Component.translatable("gui.siegedempires.start_invasion_title"));
		this.parent = parent;
	}

	private int invadeButtonX() {
		return panelX + PANEL_WIDTH + INVADE_BUTTON_GAP;
	}

	private int contentWidth() {
		return PANEL_WIDTH + INVADE_BUTTON_GAP + INVADE_BUTTON_WIDTH;
	}

	@Override
	protected void init() {
		super.init();
		clearWidgets();
		invadeButtons.clear();

		ClientGuiData.setDiplomacyListener(this::onDiplomacyUpdated);
		ClientNetworking.requestDiplomacyData();

		int centerX = this.width / 2;
		panelX = centerX - contentWidth() / 2;
		panelY = 48;

		rebuildInvadeButtons();

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.back"),
				button -> onClose()
		).bounds(centerX - BUTTON_WIDTH / 2, this.height - 28, BUTTON_WIDTH, BUTTON_HEIGHT).build());
	}

	private void onDiplomacyUpdated() {
		scroll = 0;
		rebuildInvadeButtons();
	}

	private void rebuildInvadeButtons() {
		for (Button button : invadeButtons) {
			removeWidget(button);
		}
		invadeButtons.clear();

		DiplomacyData data = ClientGuiData.getDiplomacyData();
		if (data == null || data.enemies == null || data.enemies.isEmpty()) {
			return;
		}

		int listTop = panelY + DiplomacyPanelRenderer.PANEL_PADDING + this.font.lineHeight + 4;
		int listBottom = panelY + PANEL_HEIGHT - DiplomacyPanelRenderer.PANEL_PADDING;
		int maxScroll = DiplomacyPanelRenderer.maxScroll(PANEL_HEIGHT, data.enemies);
		int clampedScroll = Math.max(0, Math.min(maxScroll, scroll));
		int minOnline = data.invasionMinOnlinePlayers > 0 ? data.invasionMinOnlinePlayers : 3;
		int buttonX = invadeButtonX();

		int rowY = listTop - clampedScroll;
		for (DiplomacyData.FactionInfo enemy : data.enemies) {
			int rowBottom = rowY + DiplomacyPanelRenderer.ROW_HEIGHT;
			if (rowBottom >= listTop && rowY <= listBottom) {
				boolean canInvade = enemy.onlineCount >= minOnline;
				Button invadeButton = Button.builder(
						Component.translatable("gui.siegedempires.invade"),
						btn -> invade(enemy)
				).bounds(buttonX, rowY, INVADE_BUTTON_WIDTH, DiplomacyPanelRenderer.ROW_HEIGHT)
						.build();
				invadeButton.active = canInvade;
				this.addRenderableWidget(invadeButton);
				invadeButtons.add(invadeButton);
			}
			rowY += DiplomacyPanelRenderer.ROW_HEIGHT + DiplomacyPanelRenderer.ROW_GAP;
		}
	}

	private void invade(DiplomacyData.FactionInfo enemy) {
		DiplomacyGuiHelper.send("invasionstart", enemy.entityType, enemy.name);
		onClose();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		DiplomacyData data = ClientGuiData.getDiplomacyData();
		// Include the Invade button column so scrolling still works while hovering them.
		if (data != null && DiplomacyPanelRenderer.isMouseOverPanel(
				mouseX, mouseY, panelX, panelY, contentWidth(), PANEL_HEIGHT)) {
			int maxScroll = DiplomacyPanelRenderer.maxScroll(PANEL_HEIGHT, data.enemies);
			scroll = DiplomacyPanelRenderer.scrollBy(scroll, maxScroll, scrollY);
			rebuildInvadeButtons();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		int centerX = this.width / 2;
		graphics.text(this.font, this.title, centerX - this.font.width(this.title) / 2, 8, 0xFFFFFFFF);

		DiplomacyData data = ClientGuiData.getDiplomacyData();
		if (data == null) {
			Component loading = Component.translatable("gui.siegedempires.loading");
			graphics.text(this.font, loading, centerX - this.font.width(loading) / 2, this.height / 2, 0xFFAAAAAA);
			return;
		}

		DiplomacyPanelRenderer.drawPanel(graphics, this.font,
				panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT,
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
