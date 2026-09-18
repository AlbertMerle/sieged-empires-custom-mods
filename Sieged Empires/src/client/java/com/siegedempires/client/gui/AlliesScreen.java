package com.siegedempires.client.gui;

import com.siegedempires.client.network.ClientGuiData;
import com.siegedempires.client.network.ClientNetworking;
import com.siegedempires.network.DiplomacyData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AlliesScreen extends Screen {
	private static final int BUTTON_WIDTH = 200;
	private static final int BUTTON_HEIGHT = 20;
	private static final int PANEL_WIDTH = 220;
	private static final int NOTIFICATION_HEIGHT = 56;
	private static final int FOOTER_BUTTON_GAP = 8;
	private static final int BOTTOM_MARGIN = 8;
	private static final int PANEL_ABOVE_ACTION_GAP = 12;
	private static final int NOTIFICATION_TOP = 36;
	private static final int MIN_PANEL_HEIGHT = 80;

	private final Screen parent;
	private int scroll;
	private int notificationScroll;
	private int panelX;
	private int panelY;
	private int panelHeight;
	private int notificationY;

	public AlliesScreen(Screen parent) {
		super(Component.translatable("gui.siegedempires.allies_title"));
		this.parent = parent;
	}

	private int backButtonY() {
		return this.height - BOTTOM_MARGIN - BUTTON_HEIGHT;
	}

	private int requestAllianceButtonY() {
		return backButtonY() - FOOTER_BUTTON_GAP - BUTTON_HEIGHT;
	}

	private void layoutPanels() {
		int centerX = this.width / 2;
		panelX = centerX - PANEL_WIDTH / 2;

		DiplomacyData data = ClientGuiData.getDiplomacyData();
		boolean hasNotifications = data != null && data.notifications != null && !data.notifications.isEmpty();
		notificationY = NOTIFICATION_TOP;
		int panelTop = hasNotifications
				? notificationY + NOTIFICATION_HEIGHT + 8
				: NOTIFICATION_TOP;
		int panelBottom = requestAllianceButtonY() - PANEL_ABOVE_ACTION_GAP;
		panelHeight = Math.max(MIN_PANEL_HEIGHT, panelBottom - panelTop);
		panelY = panelTop;
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
				Component.translatable("gui.siegedempires.request_alliance").withStyle(ChatFormatting.GREEN),
				button -> minecraft.gui.setScreen(EmpireSelectionScreen.forAllianceRequest(this))
			).bounds(centerX - BUTTON_WIDTH / 2, requestAllianceButtonY(), BUTTON_WIDTH, BUTTON_HEIGHT).build());
		}

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.back"),
				button -> onClose()
		).bounds(centerX - BUTTON_WIDTH / 2, backButtonY(), BUTTON_WIDTH, BUTTON_HEIGHT).build());
	}

	private void onDiplomacyUpdated() {
		scroll = 0;
		notificationScroll = 0;
		layoutPanels();
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		DiplomacyData data = ClientGuiData.getDiplomacyData();
		if (data != null && data.canManage && data.notifications != null && !data.notifications.isEmpty()) {
			int notificationX = panelX;
			int index = DiplomacyPanelRenderer.notificationIndexAt(
					event.x(), event.y(), notificationX, notificationY, PANEL_WIDTH, NOTIFICATION_HEIGHT,
					data.notifications, notificationScroll);
			if (index >= 0) {
				minecraft.gui.setScreen(new ConfirmDiplomacyNotificationScreen(this, data.notifications.get(index)));
				return true;
			}
		}

		if (data == null || !data.canManage || data.allies == null) {
			return super.mouseClicked(event, doubleClick);
		}

		int listTop = panelY + DiplomacyPanelRenderer.PANEL_PADDING + this.font.lineHeight + 4;
		int listLeft = panelX + DiplomacyPanelRenderer.PANEL_PADDING;
		int listRight = panelX + PANEL_WIDTH - DiplomacyPanelRenderer.PANEL_PADDING - DiplomacyPanelRenderer.SCROLLBAR_WIDTH - 2;
		int maxScroll = DiplomacyPanelRenderer.maxScroll(panelHeight, data.allies);
		int clampedScroll = Math.max(0, Math.min(maxScroll, scroll));
		int rowY = listTop - clampedScroll;

		for (DiplomacyData.FactionInfo ally : data.allies) {
			if (event.y() >= rowY && event.y() < rowY + DiplomacyPanelRenderer.ROW_HEIGHT
					&& event.x() >= listLeft && event.x() <= listRight) {
				minecraft.gui.setScreen(new AllyOptionsScreen(this, ally));
				return true;
			}
			rowY += DiplomacyPanelRenderer.ROW_HEIGHT + DiplomacyPanelRenderer.ROW_GAP;
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		DiplomacyData data = ClientGuiData.getDiplomacyData();
		if (data != null && data.notifications != null && !data.notifications.isEmpty()
				&& DiplomacyPanelRenderer.isMouseOverPanel(
				mouseX, mouseY, panelX, notificationY, PANEL_WIDTH, NOTIFICATION_HEIGHT)) {
			int maxScroll = DiplomacyPanelRenderer.maxNotificationScroll(
					NOTIFICATION_HEIGHT - DiplomacyPanelRenderer.PANEL_PADDING * 2, data.notifications);
			notificationScroll = DiplomacyPanelRenderer.scrollBy(notificationScroll, maxScroll, scrollY);
			return true;
		}

		if (data != null && DiplomacyPanelRenderer.isMouseOverPanel(
				mouseX, mouseY, panelX, panelY, PANEL_WIDTH, panelHeight)) {
			int maxScroll = DiplomacyPanelRenderer.maxScroll(panelHeight, data.allies);
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

		if (data.notifications != null && !data.notifications.isEmpty()) {
			DiplomacyPanelRenderer.drawNotifications(graphics, this.font,
					panelX, notificationY, PANEL_WIDTH, NOTIFICATION_HEIGHT,
					data.notifications, notificationScroll);
		}

		DiplomacyPanelRenderer.drawPanel(graphics, this.font,
				panelX, panelY, PANEL_WIDTH, panelHeight,
				Component.translatable("gui.siegedempires.allies"),
				0xFF55FF55, data.allies, 0xFF55FF55, scroll, false);
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
