package com.siegedempires.client.gui;

import com.siegedempires.client.network.ClientGuiData;
import com.siegedempires.client.network.ClientNetworking;
import com.siegedempires.network.payload.ConvertInventoryBannersPayload;
import com.siegedempires.util.FactionEditCost;
import com.siegedempires.util.InventoryHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class ManageTownScreen extends Screen {
	private static final int BUTTON_WIDTH = ScrollableButtonGridPanel.BUTTON_WIDTH;
	private static final int BUTTON_HEIGHT = ScrollableButtonGridPanel.BUTTON_HEIGHT;

	private final Screen parent;
	private final String wartownId;
	private ScrollableButtonGridPanel actionGrid;
	private Button townPublicButton;
	private Button diplomacyButton;
	private Button crownDukeButton;
	private Button editTownButton;
	private FooterLayout footer;
	private String errorMessage = "";
	private int errorTicks = 0;

	public ManageTownScreen(Screen parent) {
		this(parent, null);
	}

	public ManageTownScreen(Screen parent, String wartownId) {
		super(Component.translatable(wartownId != null
				? "gui.siegedempires.manage_wartown_title"
				: "gui.siegedempires.manage_town_title"));
		this.parent = parent;
		this.wartownId = wartownId;
	}

	private boolean isWartownMode() {
		return wartownId != null && !wartownId.isEmpty();
	}

	private boolean isLordMode() {
		return ClientGuiData.isLord() && !ClientGuiData.isMonarch();
	}

	private boolean hasDiplomacyStrip() {
		return isWartownMode() || (!isLordMode() && !ClientGuiData.isEmperor());
	}

	@Override
	protected void init() {
		super.init();
		clearWidgets();

		ClientGuiData.setManageTownListener(this::refreshButtons);
		if (isWartownMode()) {
			ClientNetworking.requestManageWartownData(wartownId);
		} else {
			ClientNetworking.requestManageTownData();
		}

		footer = computeFooterLayout();
		int centerX = this.width / 2;
		actionGrid = new ScrollableButtonGridPanel(this::addRenderableWidget);

		if (isLordMode()) {
			addLordActions(centerX);
		} else {
			addMonarchActions(centerX);
		}

		int gridBottom = ManageScreenLayout.actionGridBottom(footer.topY(), hasDiplomacyStrip());
		actionGrid.layout(centerX, ManageScreenLayout.ACTION_GRID_TOP, gridBottom);

		if (isWartownMode()) {
			addCrownDukeButton(centerX);
		} else {
			addDiplomacyButton(centerX);
		}
		addFooterButtons(centerX);
	}

	private FooterLayout computeFooterLayout() {
		int buttonH = BUTTON_HEIGHT;
		int gap = ManageScreenLayout.FOOTER_BUTTON_GAP;

		if (isLordMode()) {
			int backY = this.height - 28;
			return new FooterLayout(false, 0, backY, 0, 0, backY, 0);
		}

		int sideWidth = ManageScreenLayout.sideFooterButtonWidth(this.width);
		boolean side = sideWidth > 0 && !isWartownMode();
		// Wartown: no Rename; Step Down still prefers the right column when it fits.
		if (isWartownMode()) {
			sideWidth = ManageScreenLayout.sideFooterButtonWidth(this.width);
			side = sideWidth > 0;
		}

		int deleteY = ManageScreenLayout.deleteButtonY(this.height);
		if (side) {
			int backY = ManageScreenLayout.backButtonY(this.height);
			// Right column: Step Down above Edit (edit sits where Step Down used to).
			int stepY = backY;
			int editY = ManageScreenLayout.stepDownButtonY(this.height);
			int renameY = isWartownMode() ? 0 : editY;
			return new FooterLayout(true, deleteY, backY, stepY, renameY, backY, sideWidth);
		}

		// Narrow / high GUI scale: stack every footer action in the center column.
		// Order top→bottom: Edit, Step Down, Back, Delete (edit below step when side-by-side;
		// here Step then Edit then Back).
		int backY = deleteY - gap - buttonH;
		int stepY = backY - gap - buttonH;
		int renameY = isWartownMode() ? 0 : stepY - gap - buttonH;
		// Put Step above Edit so Edit is below Step Down.
		if (!isWartownMode()) {
			int editY = stepY;
			stepY = renameY;
			renameY = editY;
		}
		int topY = isWartownMode() ? stepY : Math.min(stepY, renameY);
		return new FooterLayout(false, deleteY, backY, stepY, renameY, topY, 0);
	}

	private void addLordActions(int centerX) {
		actionGrid.addLeft(Button.builder(
				Component.translatable("gui.siegedempires.invite_player"),
				button -> minecraft.gui.setScreen(new InvitePlayerScreen(this))
		).bounds(centerX - 100, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());

		actionGrid.addLeft(Button.builder(
				Component.translatable("gui.siegedempires.give_citizen_land"),
				button -> minecraft.gui.setScreen(new GiveCitizenLandScreen(this))
		).bounds(centerX - 100, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());

		actionGrid.addLeft(Button.builder(
				Component.translatable("gui.siegedempires.revoke_land"),
				button -> minecraft.gui.setScreen(new RevokeLandPlayerListScreen(this))
		).bounds(centerX - 100, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());

		actionGrid.addRight(Button.builder(
				Component.translatable("gui.siegedempires.buy_claim_banner", BuyClaimBannerScreen.COST_PER_BANNER)
						.withStyle(ChatFormatting.GREEN),
				button -> onBuyClaimBanner()
		).bounds(centerX - 100, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());

		actionGrid.addRight(Button.builder(
				Component.translatable("gui.siegedempires.give_banner"),
				button -> onGiveBanner()
		).bounds(centerX - 100, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());
	}

	private void addMonarchActions(int centerX) {
		actionGrid.addLeft(Button.builder(
				Component.translatable("gui.siegedempires.invite_player"),
				button -> minecraft.gui.setScreen(new InvitePlayerScreen(this))
		).bounds(centerX - 100, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());

		actionGrid.addLeft(Button.builder(
				Component.translatable("gui.siegedempires.evict_player").withStyle(ChatFormatting.RED),
				button -> minecraft.gui.setScreen(new EvictPlayerScreen(this))
		).bounds(centerX - 100, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());

		actionGrid.addLeft(Button.builder(
				Component.translatable("gui.siegedempires.give_citizen_land"),
				button -> minecraft.gui.setScreen(new GiveCitizenLandScreen(this))
		).bounds(centerX - 100, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());

		actionGrid.addLeft(Button.builder(
				Component.translatable("gui.siegedempires.revoke_land"),
				button -> minecraft.gui.setScreen(new RevokeLandPlayerListScreen(this))
		).bounds(centerX - 100, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());

		actionGrid.addLeftSpacer();

		actionGrid.addLeft(Button.builder(
				Component.translatable("gui.siegedempires.trust_citizens"),
				button -> minecraft.gui.setScreen(new TrustCitizensScreen(this))
		).bounds(centerX - 100, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());

		actionGrid.addLeft(Button.builder(
				Component.translatable("gui.siegedempires.make_lord"),
				button -> minecraft.gui.setScreen(new MakeLordScreen(this))
		).bounds(centerX - 100, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());

		actionGrid.addRight(Button.builder(
				Component.translatable("gui.siegedempires.buy_claim_banner", BuyClaimBannerScreen.COST_PER_BANNER)
						.withStyle(ChatFormatting.GREEN),
				button -> onBuyClaimBanner()
		).bounds(centerX - 100, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());

		Button restrictedLandButton = actionGrid.addRight(Button.builder(
				Component.translatable("gui.siegedempires.make_restricted_land", ConfirmMakeRestrictedLandScreen.COST)
						.withStyle(ChatFormatting.AQUA),
				button -> onMakeRestrictedLand()
		).bounds(centerX - 100, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		restrictedLandButton.setTooltip(Tooltip.create(
				Component.translatable("gui.siegedempires.make_restricted_land_tooltip")));

		actionGrid.addRight(Button.builder(
				Component.translatable("gui.siegedempires.give_banner"),
				button -> onGiveBanner()
		).bounds(centerX - 100, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());

		townPublicButton = actionGrid.addRight(Button.builder(
				getTownPublicLabel(),
				button -> onTogglePublic()
		).bounds(centerX - 100, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());
	}

	private void addCrownDukeButton(int centerX) {
		int buttonWidth = ManageScreenLayout.DIPLOMACY_BUTTON_WIDTH;
		int buttonHeight = ManageScreenLayout.DIPLOMACY_BUTTON_HEIGHT;
		crownDukeButton = this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.crown_duke_duchess").withStyle(ChatFormatting.GOLD),
				button -> minecraft.gui.setScreen(new CrownDukeScreen(this, wartownId))
		).bounds(centerX - buttonWidth / 2, 0, buttonWidth, buttonHeight).build());
		layoutCrownDukeButton(centerX);
	}

	private void layoutCrownDukeButton(int centerX) {
		if (crownDukeButton == null || actionGrid == null) {
			return;
		}
		int buttonWidth = ManageScreenLayout.DIPLOMACY_BUTTON_WIDTH;
		int buttonHeight = ManageScreenLayout.DIPLOMACY_BUTTON_HEIGHT;
		int y = ManageScreenLayout.diplomacyButtonY(actionGrid.getPanelBottom());
		crownDukeButton.setPosition(centerX - buttonWidth / 2, y);
		crownDukeButton.setSize(buttonWidth, buttonHeight);
	}

	private void addDiplomacyButton(int centerX) {
		if (isLordMode() || ClientGuiData.isEmperor()) {
			diplomacyButton = null;
			return;
		}

		int diplomacyWidth = ManageScreenLayout.DIPLOMACY_BUTTON_WIDTH;
		int diplomacyHeight = ManageScreenLayout.DIPLOMACY_BUTTON_HEIGHT;
		diplomacyButton = this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.diplomacy").withStyle(ChatFormatting.DARK_PURPLE),
				button -> minecraft.gui.setScreen(new DiplomacyScreen(this))
		).bounds(centerX - diplomacyWidth / 2, 0, diplomacyWidth, diplomacyHeight).build());
		layoutDiplomacyButton(centerX);
		updateDiplomacyButton();
	}

	private void layoutDiplomacyButton(int centerX) {
		if (diplomacyButton == null || actionGrid == null) {
			return;
		}
		int diplomacyWidth = ManageScreenLayout.DIPLOMACY_BUTTON_WIDTH;
		int diplomacyHeight = ManageScreenLayout.DIPLOMACY_BUTTON_HEIGHT;
		int y = ManageScreenLayout.diplomacyButtonY(actionGrid.getPanelBottom());
		diplomacyButton.setPosition(centerX - diplomacyWidth / 2, y);
		diplomacyButton.setSize(diplomacyWidth, diplomacyHeight);
	}

	private void addFooterButtons(int centerX) {
		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.back"),
				button -> onClose()
		).bounds(centerX - BUTTON_WIDTH / 2, footer.backY(), BUTTON_WIDTH, BUTTON_HEIGHT).build());

		if (isLordMode()) {
			return;
		}

		if (footer.side()) {
			int sideX = ManageScreenLayout.bottomRightButtonX(this.width, footer.sideWidth());
			this.addRenderableWidget(Button.builder(
					Component.translatable("gui.siegedempires.step_down_monarch").withStyle(ChatFormatting.RED),
					button -> minecraft.gui.setScreen(new StepDownMonarchScreen(this))
			).bounds(sideX, footer.stepY(), footer.sideWidth(), BUTTON_HEIGHT).build());

			if (!isWartownMode()) {
				editTownButton = this.addRenderableWidget(Button.builder(
						Component.translatable("gui.siegedempires.edit_town_name_banner", getEditTownCost())
								.withStyle(ChatFormatting.GOLD),
						button -> onEditTown()
				).bounds(sideX, footer.renameY(), footer.sideWidth(), BUTTON_HEIGHT).build());
			}
		} else {
			this.addRenderableWidget(Button.builder(
					Component.translatable("gui.siegedempires.step_down_monarch").withStyle(ChatFormatting.RED),
					button -> minecraft.gui.setScreen(new StepDownMonarchScreen(this))
			).bounds(centerX - BUTTON_WIDTH / 2, footer.stepY(), BUTTON_WIDTH, BUTTON_HEIGHT).build());

			if (!isWartownMode()) {
				editTownButton = this.addRenderableWidget(Button.builder(
						Component.translatable("gui.siegedempires.edit_town_name_banner", getEditTownCost())
								.withStyle(ChatFormatting.GOLD),
						button -> onEditTown()
				).bounds(centerX - BUTTON_WIDTH / 2, footer.renameY(), BUTTON_WIDTH, BUTTON_HEIGHT).build());
			}
		}

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.delete_town").withStyle(ChatFormatting.RED),
				button -> minecraft.gui.setScreen(new ConfirmDeleteTownScreen(this))
		).bounds(centerX - BUTTON_WIDTH / 2, footer.deleteY(), BUTTON_WIDTH, BUTTON_HEIGHT).build());
	}

	private Component getTownPublicLabel() {
		var data = ClientGuiData.getManageTownData();
		boolean townPublic = data != null && data.townPublic;
		return Component.translatable(
				"gui.siegedempires.town_public",
				Component.translatable(townPublic ? "gui.siegedempires.true" : "gui.siegedempires.false")
		);
	}

	public void refreshButtons() {
		if (townPublicButton != null) {
			townPublicButton.setMessage(getTownPublicLabel());
		}
		if (editTownButton != null) {
			editTownButton.setMessage(
					Component.translatable("gui.siegedempires.edit_town_name_banner", getEditTownCost())
							.withStyle(ChatFormatting.GOLD));
		}
		updateDiplomacyButton();
		if (actionGrid != null) {
			actionGrid.relayout();
			int centerX = this.width / 2;
			if (isWartownMode()) {
				layoutCrownDukeButton(centerX);
			} else {
				layoutDiplomacyButton(centerX);
			}
		}
	}

	private void updateDiplomacyButton() {
		if (diplomacyButton == null || isLordMode() || ClientGuiData.isEmperor()) {
			return;
		}
		var data = ClientGuiData.getManageTownData();
		boolean inEmpire = data != null && data.empireId != null && !data.empireId.isEmpty();
		diplomacyButton.active = data != null && !inEmpire;
	}

	private void onBuyClaimBanner() {
		if (minecraft == null || minecraft.player == null) {
			return;
		}
		minecraft.gui.setScreen(new BuyClaimBannerScreen(this));
	}

	private void onMakeRestrictedLand() {
		if (minecraft == null || minecraft.player == null) {
			return;
		}
		if (!InventoryHelper.hasEnoughGold(minecraft.player, ConfirmMakeRestrictedLandScreen.COST)) {
			GuiNotifications.showCantAfford(minecraft);
			return;
		}
		minecraft.gui.setScreen(new ConfirmMakeRestrictedLandScreen(this));
	}

	private void onGiveBanner() {
		minecraft.gui.setScreen(new ConfirmConvertBannersScreen(
				this, ConvertInventoryBannersPayload.KIND_TOWN));
	}

	private void onTogglePublic() {
		if (minecraft == null || minecraft.player == null) {
			return;
		}

		minecraft.player.connection.sendCommand("town togglepublic");
		ClientNetworking.requestManageTownData();
	}

	private int getEditTownCost() {
		var data = ClientGuiData.getManageTownData();
		if (data == null) {
			return FactionEditCost.BASE_COST;
		}
		return FactionEditCost.costForChunks(data.claimedChunks);
	}

	private void onEditTown() {
		if (minecraft == null || minecraft.player == null) {
			return;
		}
		int cost = getEditTownCost();
		if (!InventoryHelper.hasEnoughGold(minecraft.player, cost)) {
			GuiNotifications.showCantAfford(minecraft);
			return;
		}
		minecraft.gui.setScreen(new EditTownScreen(this));
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
		if (actionGrid != null && actionGrid.mouseScrolled(mouseX, mouseY, scrollY)) {
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (actionGrid != null && actionGrid.mouseClicked(event)) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (actionGrid != null && actionGrid.mouseDragged(event)) {
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (actionGrid != null && actionGrid.mouseReleased(event)) {
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (actionGrid != null) {
			actionGrid.renderBackground(graphics);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		int centerX = this.width / 2;
		Component title;
		if (isWartownMode()) {
			var data = ClientGuiData.getManageTownData();
			String name = data != null && data.townName != null ? data.townName : "";
			title = Component.translatable("gui.siegedempires.manage_wartown_title_named", name);
		} else {
			title = isLordMode()
					? Component.translatable("gui.siegedempires.manage_town_lord_title")
					: this.title;
		}
		graphics.text(this.font, title, centerX - this.font.width(title) / 2, 8, 0xFFFFFF);

		if (ClientGuiData.getManageTownData() == null) {
			Component loading = Component.translatable("gui.siegedempires.loading");
			graphics.text(this.font, loading, centerX - this.font.width(loading) / 2, this.height / 2 + 30, 0xAAAAAA);
		}

		if (!errorMessage.isEmpty() && footer != null) {
			int errorWidth = this.font.width(errorMessage);
			graphics.text(this.font, Component.literal(errorMessage),
					centerX - errorWidth / 2, footer.topY() - 14, 0xFF5555);
		}
	}

	@Override
	public void onClose() {
		ClientGuiData.setManageTownListener(null);
		if (isWartownMode()) {
			ClientNetworking.clearWartownContext();
		}
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private record FooterLayout(
			boolean side,
			int deleteY,
			int backY,
			int stepY,
			int renameY,
			int topY,
			int sideWidth
	) {
	}
}
