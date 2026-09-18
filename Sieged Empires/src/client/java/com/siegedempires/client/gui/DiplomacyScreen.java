package com.siegedempires.client.gui;

import com.siegedempires.client.network.ClientGuiData;
import com.siegedempires.client.network.ClientNetworking;
import com.siegedempires.network.DiplomacyData;
import com.siegedempires.util.InventoryHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class DiplomacyScreen extends Screen {
	private static final int EMPIRE_CREATE_COST = 8;
	private static final int BUTTON_WIDTH = 90;
	private static final int BUTTON_HEIGHT = 20;
	private static final int WIDE_BUTTON_WIDTH = 180;
	private static final int PANEL_WIDTH = 150;
	private static final int MIN_PANEL_HEIGHT = 48;
	private static final int NOTIFICATION_HEIGHT = 52;
	private static final int MIN_NOTIFICATION_HEIGHT = 28;
	private static final int FOOTER_GAP = 8;
	private static final int BOTTOM_MARGIN = 8;
	private static final int FLAG_WIDTH = 16;
	private static final int FLAG_HEIGHT = 24;
	/** Gap between self-header and the content below (notifications or panels). */
	private static final int HEADER_CONTENT_GAP = 6;
	/** Gap between notification strip and ally/enemy panels. */
	private static final int NOTIFICATION_PANEL_GAP = 8;
	/** Extra gap between ally/enemy panels and the Allies/Enemies buttons. */
	private static final int PANEL_FOOTER_GAP = 8;

	private final Screen parent;
	private int alliesScroll;
	private int enemiesScroll;
	private int notificationScroll;
	private int panelLeftX;
	private int panelRightX;
	private int panelY;
	private int panelHeight;
	private int notificationY;
	private int notificationHeight;
	private Button alliesButton;
	private Button enemiesButton;
	private Button startInvasionButton;
	private Button createEmpireButton;
	private Button joinEmpireButton;
	private Button backButton;

	public DiplomacyScreen(Screen parent) {
		super(Component.translatable("gui.siegedempires.diplomacy_title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		super.init();
		clearWidgets();

		ClientGuiData.setDiplomacyListener(this::onDiplomacyUpdated);
		ClientGuiData.setManageTownListener(this::refreshEmpireButtons);
		ClientNetworking.requestDiplomacyData();
		ClientNetworking.requestManageTownData();

		int centerX = this.width / 2;
		panelLeftX = centerX - PANEL_WIDTH - 12;
		panelRightX = centerX + 12;

		// Placeholder Y — layoutContent() sets final positions.
		alliesButton = this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.allies").withStyle(ChatFormatting.GREEN),
				button -> minecraft.gui.setScreen(new AlliesScreen(this))
		).bounds(centerX - BUTTON_WIDTH - 6, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());

		enemiesButton = this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.enemies").withStyle(ChatFormatting.RED),
				button -> minecraft.gui.setScreen(new EnemiesScreen(this))
		).bounds(centerX + 6, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());

		startInvasionButton = Button.builder(
				Component.translatable("gui.siegedempires.start_invasion"),
				button -> minecraft.gui.setScreen(new StartInvasionScreen(this))
		).bounds(centerX - WIDE_BUTTON_WIDTH / 2, 0, WIDE_BUTTON_WIDTH, BUTTON_HEIGHT).build();
		this.addRenderableWidget(startInvasionButton);

		createEmpireButton = this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.create_empire"),
				button -> onOpenCreateEmpire()
		).bounds(centerX - WIDE_BUTTON_WIDTH / 2, 0, WIDE_BUTTON_WIDTH, BUTTON_HEIGHT).build());

		joinEmpireButton = this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.join_empire"),
				button -> onOpenJoinEmpire()
		).bounds(centerX - WIDE_BUTTON_WIDTH / 2, 0, WIDE_BUTTON_WIDTH, BUTTON_HEIGHT).build());

		backButton = this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.back"),
				button -> onClose()
		).bounds(centerX - WIDE_BUTTON_WIDTH / 2, 0, WIDE_BUTTON_WIDTH, BUTTON_HEIGHT).build());

		updateEmpireButtons();
		layoutContent();
		updateInvasionButton();
	}

	/**
	 * Stacks footer buttons from the bottom, then stretches ally/enemy panels
	 * into all remaining space under the header (and notifications if present).
	 * Height grows with GUI coordinate space (lower GUI scale / taller window).
	 */
	private void layoutContent() {
		int centerX = this.width / 2;
		panelLeftX = centerX - PANEL_WIDTH - 12;
		panelRightX = centerX + 12;

		boolean showEmpireButtons = createEmpireButton != null && createEmpireButton.visible;

		int backY = this.height - BOTTOM_MARGIN - BUTTON_HEIGHT;
		int cursorY = backY;
		if (backButton != null) {
			backButton.setPosition(centerX - WIDE_BUTTON_WIDTH / 2, backY);
		}

		int joinY = cursorY;
		int createY = cursorY;
		if (showEmpireButtons) {
			joinY = cursorY - FOOTER_GAP - BUTTON_HEIGHT;
			createY = joinY - FOOTER_GAP - BUTTON_HEIGHT;
			cursorY = createY;
			if (joinEmpireButton != null) {
				joinEmpireButton.setPosition(centerX - WIDE_BUTTON_WIDTH / 2, joinY);
			}
			if (createEmpireButton != null) {
				createEmpireButton.setPosition(centerX - WIDE_BUTTON_WIDTH / 2, createY);
			}
		}

		int invasionY = cursorY - FOOTER_GAP - BUTTON_HEIGHT;
		int alliesEnemiesY = invasionY - FOOTER_GAP * 2 - BUTTON_HEIGHT;
		if (startInvasionButton != null) {
			startInvasionButton.setPosition(centerX - WIDE_BUTTON_WIDTH / 2, invasionY);
		}
		if (alliesButton != null) {
			alliesButton.setPosition(centerX - BUTTON_WIDTH - 6, alliesEnemiesY);
		}
		if (enemiesButton != null) {
			enemiesButton.setPosition(centerX + 6, alliesEnemiesY);
		}

		int headerBottom = 26 + FLAG_HEIGHT;
		notificationY = headerBottom + HEADER_CONTENT_GAP;

		DiplomacyData data = ClientGuiData.getDiplomacyData();
		boolean hasNotifications = data != null && data.notifications != null && !data.notifications.isEmpty();
		notificationHeight = hasNotifications ? NOTIFICATION_HEIGHT : 0;

		int contentTop = hasNotifications
				? notificationY + notificationHeight + NOTIFICATION_PANEL_GAP
				: notificationY;
		int panelsBottom = alliesEnemiesY - PANEL_FOOTER_GAP;
		int availableForPanels = panelsBottom - contentTop;

		if (hasNotifications && availableForPanels < MIN_PANEL_HEIGHT) {
			notificationHeight = MIN_NOTIFICATION_HEIGHT;
			contentTop = notificationY + notificationHeight + NOTIFICATION_PANEL_GAP;
			availableForPanels = panelsBottom - contentTop;
		}

		panelHeight = Math.max(MIN_PANEL_HEIGHT, availableForPanels);
		panelY = contentTop;
		if (panelY + panelHeight > panelsBottom) {
			panelHeight = Math.max(MIN_PANEL_HEIGHT, panelsBottom - panelY);
		}
	}

	private void onDiplomacyUpdated() {
		alliesScroll = 0;
		enemiesScroll = 0;
		notificationScroll = 0;
		updateInvasionButton();
		layoutContent();
	}

	private void refreshEmpireButtons() {
		updateEmpireButtons();
		layoutContent();
	}

	private void updateInvasionButton() {
		if (startInvasionButton == null) {
			return;
		}
		DiplomacyData data = ClientGuiData.getDiplomacyData();
		startInvasionButton.active = data != null
				&& ((data.enemies != null && !data.enemies.isEmpty()) || data.hasActiveInvasion);
	}

	private void updateEmpireButtons() {
		if (createEmpireButton == null || joinEmpireButton == null) {
			return;
		}
		var data = ClientGuiData.getManageTownData();
		boolean inEmpire = data != null && data.empireId != null && !data.empireId.isEmpty();
		createEmpireButton.visible = !inEmpire;
		joinEmpireButton.visible = !inEmpire;
	}

	private void onOpenCreateEmpire() {
		if (minecraft == null || minecraft.player == null) {
			return;
		}

		if (!InventoryHelper.hasEnoughGold(minecraft.player, EMPIRE_CREATE_COST)) {
			GuiNotifications.showCantAfford(minecraft);
			return;
		}

		minecraft.gui.setScreen(new CreateEmpireScreen(this));
	}

	private void onOpenJoinEmpire() {
		minecraft.gui.setScreen(EmpireSelectionScreen.forJoin(this));
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		DiplomacyData data = ClientGuiData.getDiplomacyData();
		if (data != null && data.canManage && data.notifications != null && !data.notifications.isEmpty()
				&& notificationHeight > 0) {
			int centerX = this.width / 2;
			int notificationWidth = PANEL_WIDTH * 2 + 24;
			int notificationX = centerX - notificationWidth / 2;
			int index = DiplomacyPanelRenderer.notificationIndexAt(
					event.x(), event.y(), notificationX, notificationY, notificationWidth, notificationHeight,
					data.notifications, notificationScroll);
			if (index >= 0) {
				DiplomacyData.PendingNotification notification = data.notifications.get(index);
				minecraft.gui.setScreen(new ConfirmDiplomacyNotificationScreen(this, notification));
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		DiplomacyData data = ClientGuiData.getDiplomacyData();
		if (data == null) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}

		int centerX = this.width / 2;
		int notificationWidth = PANEL_WIDTH * 2 + 24;
		int notificationX = centerX - notificationWidth / 2;
		if (notificationHeight > 0 && data.notifications != null && !data.notifications.isEmpty()
				&& DiplomacyPanelRenderer.isMouseOverPanel(mouseX, mouseY, notificationX, notificationY,
				notificationWidth, notificationHeight)) {
			int maxScroll = DiplomacyPanelRenderer.maxNotificationScroll(
					notificationHeight - DiplomacyPanelRenderer.PANEL_PADDING * 2, data.notifications);
			notificationScroll = DiplomacyPanelRenderer.scrollBy(notificationScroll, maxScroll, scrollY);
			return true;
		}

		if (DiplomacyPanelRenderer.isMouseOverPanel(mouseX, mouseY, panelLeftX, panelY, PANEL_WIDTH, panelHeight)) {
			int maxScroll = DiplomacyPanelRenderer.maxScroll(panelHeight, data.allies);
			alliesScroll = DiplomacyPanelRenderer.scrollBy(alliesScroll, maxScroll, scrollY);
			return true;
		}

		if (DiplomacyPanelRenderer.isMouseOverPanel(mouseX, mouseY, panelRightX, panelY, PANEL_WIDTH, panelHeight)) {
			int maxScroll = DiplomacyPanelRenderer.maxScroll(panelHeight, data.enemies);
			enemiesScroll = DiplomacyPanelRenderer.scrollBy(enemiesScroll, maxScroll, scrollY);
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
		if (data == null || data.entityName == null || data.entityName.isEmpty()) {
			Component loading = Component.translatable("gui.siegedempires.loading");
			graphics.text(this.font, loading, centerX - this.font.width(loading) / 2, this.height / 2, 0xFFAAAAAA);
			return;
		}

		drawSelfHeader(graphics, centerX, data);

		if (notificationHeight > 0 && data.notifications != null && !data.notifications.isEmpty()) {
			int notificationWidth = PANEL_WIDTH * 2 + 24;
			int notificationX = centerX - notificationWidth / 2;
			DiplomacyPanelRenderer.drawNotifications(graphics, this.font,
					notificationX, notificationY, notificationWidth, notificationHeight,
					data.notifications, notificationScroll);
		}

		DiplomacyPanelRenderer.drawPanel(graphics, this.font,
				panelLeftX, panelY, PANEL_WIDTH, panelHeight,
				Component.translatable("gui.siegedempires.allies"),
				0xFF55FF55, data.allies, 0xFF55FF55, alliesScroll);

		DiplomacyPanelRenderer.drawPanel(graphics, this.font,
				panelRightX, panelY, PANEL_WIDTH, panelHeight,
				Component.translatable("gui.siegedempires.enemies"),
				0xFFFF5555, data.enemies, 0xFFFF5555, enemiesScroll);
	}

	private void drawSelfHeader(GuiGraphicsExtractor graphics, int centerX, DiplomacyData data) {
		Component name = Component.literal(data.entityName);
		int nameWidth = this.font.width(name);
		int totalWidth = FLAG_WIDTH + 6 + nameWidth;
		int startX = centerX - totalWidth / 2;
		int headerY = 26;

		if ((data.bannerBaseColor != null && !data.bannerBaseColor.isEmpty())
				|| (data.bannerPixels != null && !data.bannerPixels.isEmpty())) {
			BannerEditorWidgets.drawBannerFlag(graphics,
					data.bannerPatterns, data.bannerBaseColor, data.bannerPixels,
					startX, headerY, FLAG_WIDTH, FLAG_HEIGHT);
		}
		graphics.text(this.font, name, startX + FLAG_WIDTH + 6,
				headerY + (FLAG_HEIGHT - this.font.lineHeight) / 2, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		ClientGuiData.setDiplomacyListener(null);
		if (parent instanceof ManageTownScreen manageTownScreen) {
			ClientGuiData.setManageTownListener(manageTownScreen::refreshButtons);
		} else {
			ClientGuiData.setManageTownListener(null);
		}
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
