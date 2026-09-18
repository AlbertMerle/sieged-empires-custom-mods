package com.siegedempires.client.gui;

import com.siegedempires.client.network.ClientGuiData;
import com.siegedempires.client.network.ClientNetworking;
import com.siegedempires.client.title.SiegedLogoLayout;
import com.siegedempires.config.ModSettings;
import com.siegedempires.util.InventoryHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SiegedEmpiresGuiScreen extends Screen {
	private static final int BUTTON_WIDTH = 200;
	private static final int BUTTON_HEIGHT = 20;
	/** Slightly larger than the other main-menu actions. */
	private static final int DIPLOMACY_BUTTON_WIDTH = 220;
	private static final int DIPLOMACY_BUTTON_HEIGHT = 24;
	/** Width of the translucent background panel behind the whole menu. */
	private static final int PANEL_WIDTH = 300;
	/** Vertical space between consecutive buttons. */
	private static final int BUTTON_SPACING = 26;
	/**
	 * Extra vertical gap before Diplomacy (matches the old 2nd "coming soon" slot:
	 * leave town, then one empty row, then Diplomacy).
	 */
	private static final int DIPLOMACY_ROW_OFFSET = 2;
	private static final int LOGO_HEADER_PADDING = 14;

	private String errorMessage = "";
	private int errorTicks = 0;
	private Button leaveTownButton;
	private Button manageTownButton;
	private Button manageEmpireButton;
	private Button diplomacyButton;
	private Button mailButton;
	private final MailBoxWidget mailBox = new MailBoxWidget();
	private int alliesScroll;
	private int enemiesScroll;
	private int sidePanelTop;
	private int sidePanelHeight;
	private int alliesPanelX;
	private int enemiesPanelX;
	private static final int SIDE_PANEL_WIDTH = 140;

	public SiegedEmpiresGuiScreen() {
		super(Component.translatable("gui.siegedempires.title"));
	}

	@Override
	protected void init() {
		super.init();
		clearWidgets();

		ClientGuiData.setGuiStatusListener(this::refreshButtons);
		ClientGuiData.setDiplomacyListener(this::refreshDiplomacyPanels);
		ClientGuiData.setMailListener(this::onMailUpdated);
		ClientNetworking.requestGuiStatus();
		ClientNetworking.requestDiplomacyData();
		ClientNetworking.requestMailData();

		int centerX = this.width / 2;
		int buttonLeft = centerX - BUTTON_WIDTH / 2;
		MenuLayout layout = this.menuLayout();
		int blockTop = layout.blockTop();
		int btnY = layout.buttonTop();

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.create_town_button", ModSettings.get().createTownCost),
				button -> onCreateTown()
		).bounds(buttonLeft, btnY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		btnY += BUTTON_SPACING;

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.join_town"),
				button -> onJoinTown()
		).bounds(buttonLeft, btnY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		btnY += BUTTON_SPACING;

		manageTownButton = Button.builder(
				Component.translatable("gui.siegedempires.manage_town"),
				button -> onManageTown()
		).bounds(buttonLeft, btnY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
		updateManageTownButton();
		this.addRenderableWidget(manageTownButton);
		btnY += BUTTON_SPACING;

		manageEmpireButton = Button.builder(
				Component.translatable("gui.siegedempires.manage_empire"),
				button -> onManageEmpire()
		).bounds(buttonLeft, btnY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
		updateManageEmpireButton();
		this.addRenderableWidget(manageEmpireButton);
		btnY += BUTTON_SPACING;

		leaveTownButton = Button.builder(
				Component.translatable("gui.siegedempires.leave_town").withStyle(ChatFormatting.RED),
				button -> onLeaveTown()
		).bounds(buttonLeft, btnY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
		updateLeaveTownButton();
		this.addRenderableWidget(leaveTownButton);

		int diplomacyLeft = centerX - DIPLOMACY_BUTTON_WIDTH / 2;
		int diplomacyY = btnY + DIPLOMACY_ROW_OFFSET * BUTTON_SPACING;
		diplomacyButton = Button.builder(
				Component.translatable("gui.siegedempires.diplomacy").withStyle(ChatFormatting.DARK_PURPLE),
				button -> onDiplomacy()
		).bounds(diplomacyLeft, diplomacyY, DIPLOMACY_BUTTON_WIDTH, DIPLOMACY_BUTTON_HEIGHT).build();
		updateDiplomacyButton();
		this.addRenderableWidget(diplomacyButton);

		// Close (X) button pinned to the top-right corner of the panel (above the logo row).
		this.addRenderableWidget(Button.builder(
				Component.literal("X"),
				button -> onClose()
		).bounds(centerX + PANEL_WIDTH / 2 - 24, blockTop + 4, 20, 20).build());

		Runnable openMail = () -> {
			if (minecraft != null) {
				minecraft.gui.setScreen(new MailScreen(this));
			}
		};
		mailBox.layout(this);
		if (mailButton != null) {
			this.removeWidget(mailButton);
		}
		mailButton = mailBox.createButton(openMail);
		mailButton.visible = true;
		this.addRenderableWidget(mailButton);
	}

	public void refreshButtons() {
		updateManageTownButton();
		updateManageEmpireButton();
		updateLeaveTownButton();
		updateDiplomacyButton();
		if (ClientGuiData.isInTown()) {
			ClientNetworking.requestDiplomacyData();
		}
	}

	public void refreshDiplomacyPanels() {
		alliesScroll = 0;
		enemiesScroll = 0;
	}

	private void onMailUpdated() {
		mailBox.layout(this);
		if (mailButton != null) {
			mailBox.applyButtonBounds(mailButton);
		}
	}

	private void updateManageTownButton() {
		if (manageTownButton != null) {
			manageTownButton.active = ClientGuiData.canManageTown();
		}
	}

	private void updateManageEmpireButton() {
		if (manageEmpireButton != null) {
			manageEmpireButton.active = ClientGuiData.isEmperor();
		}
	}

	private void updateLeaveTownButton() {
		if (leaveTownButton != null) {
			leaveTownButton.active = ClientGuiData.isInTown() && !ClientGuiData.isMonarch();
		}
	}

	private void updateDiplomacyButton() {
		if (diplomacyButton == null) {
			return;
		}
		boolean canAccess = ClientGuiData.canAccessDiplomacy();
		diplomacyButton.visible = canAccess;
		diplomacyButton.active = canAccess;
	}

	private void onCreateTown() {
		if (minecraft == null || minecraft.player == null) {
			return;
		}

		if (ClientGuiData.isInTown()) {
			showError(Component.translatable("gui.siegedempires.already_in_town").getString());
			return;
		}

		if (!InventoryHelper.hasEnoughGold(minecraft.player, ModSettings.get().createTownCost)) {
			GuiNotifications.showCantAfford(minecraft);
			return;
		}

		this.minecraft.gui.setScreen(new CreateTownScreen(this));
	}

	private void onJoinTown() {
		if (minecraft == null || minecraft.player == null) {
			return;
		}

		if (ClientGuiData.isInTown()) {
			showError(Component.translatable("gui.siegedempires.already_in_town").getString());
			return;
		}

		this.minecraft.gui.setScreen(TownSelectionScreen.forJoin(this));
	}

	private void onManageTown() {
		if (!ClientGuiData.canManageTown()) {
			return;
		}

		this.minecraft.gui.setScreen(new ManageTownScreen(this));
	}

	private void onManageEmpire() {
		if (!ClientGuiData.isEmperor()) {
			return;
		}

		this.minecraft.gui.setScreen(new ManageEmpireScreen(this));
	}

	private void onDiplomacy() {
		if (!ClientGuiData.canAccessDiplomacy()) {
			return;
		}

		this.minecraft.gui.setScreen(new DiplomacyScreen(this));
	}

	private void onLeaveTown() {
		if (!ClientGuiData.isInTown() || ClientGuiData.isMonarch()) {
			return;
		}

		this.minecraft.gui.setScreen(new ConfirmLeaveTownScreen(this));
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
			if (errorTicks == 0) {
				errorMessage = "";
			}
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		var data = ClientGuiData.getDiplomacyData();
		if (data != null && data.entityName != null && !data.entityName.isEmpty()) {
			if (DiplomacyPanelRenderer.isMouseOverPanel(
					mouseX, mouseY, alliesPanelX, sidePanelTop, SIDE_PANEL_WIDTH, sidePanelHeight)) {
				int maxScroll = DiplomacyPanelRenderer.maxScroll(sidePanelHeight, data.allies);
				alliesScroll = DiplomacyPanelRenderer.scrollBy(alliesScroll, maxScroll, scrollY);
				return true;
			}
			if (DiplomacyPanelRenderer.isMouseOverPanel(
					mouseX, mouseY, enemiesPanelX, sidePanelTop, SIDE_PANEL_WIDTH, sidePanelHeight)) {
				int maxScroll = DiplomacyPanelRenderer.maxScroll(sidePanelHeight, data.enemies);
				enemiesScroll = DiplomacyPanelRenderer.scrollBy(enemiesScroll, maxScroll, scrollY);
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int centerX = this.width / 2;
		MenuLayout layout = this.menuLayout();
		int leaveButtonTop = layout.buttonTop() + 4 * BUTTON_SPACING;
		int diplomacyTop = leaveButtonTop + DIPLOMACY_ROW_OFFSET * BUTTON_SPACING;
		boolean showDiplomacy = diplomacyButton != null && diplomacyButton.visible;
		int lastButtonTop = showDiplomacy ? diplomacyTop : leaveButtonTop;
		int lastButtonHeight = showDiplomacy ? DIPLOMACY_BUTTON_HEIGHT : BUTTON_HEIGHT;
		int panelLeft = centerX - PANEL_WIDTH / 2;
		int panelTop = layout.blockTop() - 10;
		int panelBottom = lastButtonTop + lastButtonHeight + 8;

		sidePanelTop = panelTop;
		sidePanelHeight = panelBottom - panelTop;
		alliesPanelX = panelLeft - SIDE_PANEL_WIDTH - 12;
		enemiesPanelX = panelLeft + PANEL_WIDTH + 12;
		if (mailBox != null) {
			mailBox.layout(this);
			// Keep the right (enemies) panel clear of the top-right mail box.
			int mailClearRight = mailBox.getBoxLeft() - 8;
			if (enemiesPanelX + SIDE_PANEL_WIDTH > mailClearRight) {
				enemiesPanelX = Math.max(panelLeft + PANEL_WIDTH + 12,
						mailClearRight - SIDE_PANEL_WIDTH);
			}
			if (mailBox.getBoxBottom() + 8 > sidePanelTop
					&& enemiesPanelX + SIDE_PANEL_WIDTH > mailBox.getBoxLeft()) {
				sidePanelTop = Math.max(panelTop, mailBox.getBoxBottom() + 8);
				sidePanelHeight = Math.max(40, panelBottom - sidePanelTop);
			}
		}

		// Translucent background panel behind the whole menu (widgets draw on top).
		graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelBottom, 0x88000000);
		graphics.outline(panelLeft, panelTop, PANEL_WIDTH, panelBottom - panelTop, 0xFF555555);

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		int logoX = centerX - layout.logoWidth() / 2;
		int logoY = layout.blockTop() + 6;
		graphics.blit(
				SiegedLogoLayout.TEXTURE,
				logoX,
				logoY,
				logoX + layout.logoWidth(),
				logoY + layout.logoHeight(),
				0.0F,
				1.0F,
				0.0F,
				1.0F
		);

		drawDiplomacySidePanels(graphics);

		if (mailBox != null) {
			mailBox.layout(this);
			if (mailButton != null) {
				mailBox.applyButtonBounds(mailButton);
			}
			mailBox.render(graphics, ClientGuiData.hasMail());
		}

		if (!errorMessage.isEmpty()) {
			int errorWidth = this.font.width(errorMessage);
			graphics.text(this.font, Component.literal(errorMessage),
					centerX - errorWidth / 2, panelBottom + 4, 0xFFFF5555);
		}
	}

	/**
	 * Preferred logo size at roomy GUI scales; shrinks with the scaled window so the
	 * button stack still fits without colliding into the logo.
	 */
	private MenuLayout menuLayout() {
		int rows = 5 + DIPLOMACY_ROW_OFFSET;
		int buttonBlock = rows * BUTTON_SPACING;
		int maxLogoHeight = Math.max(24, this.height - 48 - buttonBlock - LOGO_HEADER_PADDING);
		int maxLogoWidth = PANEL_WIDTH - 20;
		SiegedLogoLayout.Size logo = SiegedLogoLayout.fit(
				SiegedLogoLayout.MENU_PREFERRED_WIDTH, maxLogoWidth, maxLogoHeight);
		int headerHeight = logo.height() + LOGO_HEADER_PADDING;
		int blockTop = this.height / 2 - (headerHeight + buttonBlock) / 2;
		return new MenuLayout(blockTop, headerHeight, logo.width(), logo.height());
	}

	private record MenuLayout(int blockTop, int headerHeight, int logoWidth, int logoHeight) {
		int buttonTop() {
			return this.blockTop + this.headerHeight;
		}
	}

	@Override
	public void onClose() {
		ClientGuiData.setGuiStatusListener(null);
		ClientGuiData.setDiplomacyListener(null);
		super.onClose();
	}

	private void drawDiplomacySidePanels(GuiGraphicsExtractor graphics) {
		if (!ClientGuiData.isInTown()) {
			return;
		}
		var data = ClientGuiData.getDiplomacyData();
		if (data == null || data.entityName == null || data.entityName.isEmpty()) {
			return;
		}

		DiplomacyPanelRenderer.drawPanel(graphics, this.font,
				alliesPanelX, sidePanelTop, SIDE_PANEL_WIDTH, sidePanelHeight,
				Component.translatable("gui.siegedempires.allies"),
				0xFF55FF55, data.allies, 0xFF55FF55, alliesScroll);

		DiplomacyPanelRenderer.drawPanel(graphics, this.font,
				enemiesPanelX, sidePanelTop, SIDE_PANEL_WIDTH, sidePanelHeight,
				Component.translatable("gui.siegedempires.enemies"),
				0xFFFF5555, data.enemies, 0xFFFF5555, enemiesScroll);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}
}
