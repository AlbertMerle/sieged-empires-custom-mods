package com.siegedempires.client.gui;

import com.siegedempires.client.network.ClientGuiData;
import com.siegedempires.client.network.ClientNetworking;
import com.siegedempires.network.payload.ConvertInventoryBannersPayload;
import com.siegedempires.util.FactionEditCost;
import com.siegedempires.util.InventoryHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class ManageEmpireScreen extends Screen {
	private static final int BUTTON_WIDTH = ScrollableButtonGridPanel.BUTTON_WIDTH;
	private static final int BUTTON_HEIGHT = ScrollableButtonGridPanel.BUTTON_HEIGHT;

	private final Screen parent;
	private ScrollableButtonGridPanel actionGrid;
	private Button empirePublicButton;
	private Button manageWartownsButton;
	private Button editEmpireButton;
	private FooterLayout footer;

	public ManageEmpireScreen(Screen parent) {
		super(Component.translatable("gui.siegedempires.manage_empire_title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		super.init();
		clearWidgets();

		ClientGuiData.setManageEmpireListener(this::refreshEmpireButtons);
		ClientNetworking.requestManageEmpireData();

		footer = computeFooterLayout();
		int centerX = this.width / 2;
		actionGrid = new ScrollableButtonGridPanel(this::addRenderableWidget);

		actionGrid.add(Button.builder(
				Component.translatable("gui.siegedempires.diplomacy").withStyle(ChatFormatting.DARK_PURPLE),
				button -> minecraft.gui.setScreen(new DiplomacyScreen(this))
		).bounds(centerX - 100, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());

		actionGrid.add(Button.builder(
				Component.translatable("gui.siegedempires.invite_town_empire"),
				button -> minecraft.gui.setScreen(TownSelectionScreen.forInvite(this))
		).bounds(centerX - 100, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());

		actionGrid.add(Button.builder(
				Component.translatable("gui.siegedempires.disannex_town_empire"),
				button -> minecraft.gui.setScreen(TownSelectionScreen.forDisannex(this))
		).bounds(centerX - 100, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());

		empirePublicButton = actionGrid.add(Button.builder(
				getEmpirePublicLabel(),
				button -> onTogglePublic()
		).bounds(centerX - 100, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());

		actionGrid.add(Button.builder(
				Component.translatable("gui.siegedempires.give_banner"),
				button -> minecraft.gui.setScreen(new ConfirmConvertBannersScreen(
						this, ConvertInventoryBannersPayload.KIND_EMPIRE))
		).bounds(centerX - 100, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());

		int gridBottom = ManageScreenLayout.actionGridBottom(footer.topY(), false);
		actionGrid.layout(centerX, ManageScreenLayout.ACTION_GRID_TOP, gridBottom);

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.back"),
				button -> onClose()
		).bounds(centerX - BUTTON_WIDTH / 2, footer.backY(), BUTTON_WIDTH, BUTTON_HEIGHT).build());

		if (footer.side()) {
			int sideX = ManageScreenLayout.bottomRightButtonX(this.width, footer.sideWidth());
			this.addRenderableWidget(Button.builder(
					Component.translatable("gui.siegedempires.step_down_emperor").withStyle(ChatFormatting.RED),
					button -> minecraft.gui.setScreen(new StepDownEmperorScreen(this))
			).bounds(sideX, footer.stepY(), footer.sideWidth(), BUTTON_HEIGHT).build());

			editEmpireButton = this.addRenderableWidget(Button.builder(
					Component.translatable("gui.siegedempires.edit_empire_name_banner", getEditEmpireCost())
							.withStyle(ChatFormatting.GOLD),
					button -> onEditEmpire()
			).bounds(sideX, footer.editY(), footer.sideWidth(), BUTTON_HEIGHT).build());
		} else {
			this.addRenderableWidget(Button.builder(
					Component.translatable("gui.siegedempires.step_down_emperor").withStyle(ChatFormatting.RED),
					button -> minecraft.gui.setScreen(new StepDownEmperorScreen(this))
			).bounds(centerX - BUTTON_WIDTH / 2, footer.stepY(), BUTTON_WIDTH, BUTTON_HEIGHT).build());

			editEmpireButton = this.addRenderableWidget(Button.builder(
					Component.translatable("gui.siegedempires.edit_empire_name_banner", getEditEmpireCost())
							.withStyle(ChatFormatting.GOLD),
					button -> onEditEmpire()
			).bounds(centerX - BUTTON_WIDTH / 2, footer.editY(), BUTTON_WIDTH, BUTTON_HEIGHT).build());
		}

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.delete_empire").withStyle(ChatFormatting.RED),
				button -> minecraft.gui.setScreen(new ConfirmDeleteEmpireScreen(this))
		).bounds(centerX - BUTTON_WIDTH / 2, footer.deleteY(), BUTTON_WIDTH, BUTTON_HEIGHT).build());

		if (footer.side()) {
			manageWartownsButton = this.addRenderableWidget(Button.builder(
					Component.translatable("gui.siegedempires.manage_wartowns").withStyle(ChatFormatting.GOLD),
					button -> minecraft.gui.setScreen(new ManageWartownsListScreen(this))
			).bounds(ManageScreenLayout.bottomLeftButtonX(), footer.backY(),
					footer.sideWidth(), BUTTON_HEIGHT).build());
		} else {
			manageWartownsButton = this.addRenderableWidget(Button.builder(
					Component.translatable("gui.siegedempires.manage_wartowns").withStyle(ChatFormatting.GOLD),
					button -> minecraft.gui.setScreen(new ManageWartownsListScreen(this))
			).bounds(centerX - BUTTON_WIDTH / 2, footer.wartownsY(), BUTTON_WIDTH, BUTTON_HEIGHT).build());
		}
		updateManageWartownsButton();
	}

	private FooterLayout computeFooterLayout() {
		int buttonH = BUTTON_HEIGHT;
		int gap = ManageScreenLayout.FOOTER_BUTTON_GAP;
		int sideWidth = ManageScreenLayout.sideFooterButtonWidth(this.width);
		boolean side = sideWidth > 0;
		int deleteY = ManageScreenLayout.deleteButtonY(this.height);

		if (side) {
			int backY = ManageScreenLayout.backButtonY(this.height);
			int stepY = backY;
			int editY = ManageScreenLayout.stepDownButtonY(this.height);
			return new FooterLayout(true, deleteY, backY, stepY, editY, 0, backY, sideWidth);
		}

		// Narrow: Manage Wartowns, Step Down, Edit, Back, Delete (edit below step).
		int backY = deleteY - gap - buttonH;
		int editY = backY - gap - buttonH;
		int stepY = editY - gap - buttonH;
		int wartownsY = stepY - gap - buttonH;
		return new FooterLayout(false, deleteY, backY, stepY, editY, wartownsY, wartownsY, 0);
	}

	private Component getEmpirePublicLabel() {
		var data = ClientGuiData.getManageEmpireData();
		boolean empirePublic = data != null && data.empirePublic;
		return Component.translatable(
				"gui.siegedempires.empire_public",
				Component.translatable(empirePublic ? "gui.siegedempires.true" : "gui.siegedempires.false")
		);
	}

	public void refreshEmpireButtons() {
		refreshEmpirePublicButton();
		updateManageWartownsButton();
		if (editEmpireButton != null) {
			editEmpireButton.setMessage(
					Component.translatable("gui.siegedempires.edit_empire_name_banner", getEditEmpireCost())
							.withStyle(ChatFormatting.GOLD));
		}
	}

	public void refreshEmpirePublicButton() {
		if (empirePublicButton != null) {
			empirePublicButton.setMessage(getEmpirePublicLabel());
		}
		if (actionGrid != null) {
			actionGrid.relayout();
		}
	}

	private void updateManageWartownsButton() {
		if (manageWartownsButton == null) {
			return;
		}
		var data = ClientGuiData.getManageEmpireData();
		boolean hasWartowns = data != null && data.warTowns != null && !data.warTowns.isEmpty();
		manageWartownsButton.active = hasWartowns;
	}

	private void onTogglePublic() {
		if (minecraft == null || minecraft.player == null) {
			return;
		}

		minecraft.player.connection.sendCommand("empire togglepublic");
		ClientNetworking.requestManageEmpireData();
	}

	private int getEditEmpireCost() {
		var data = ClientGuiData.getManageEmpireData();
		if (data == null) {
			return FactionEditCost.BASE_COST;
		}
		return FactionEditCost.costForChunks(data.claimedChunks);
	}

	private void onEditEmpire() {
		if (minecraft == null || minecraft.player == null) {
			return;
		}
		int cost = getEditEmpireCost();
		if (!InventoryHelper.hasEnoughGold(minecraft.player, cost)) {
			GuiNotifications.showCantAfford(minecraft);
			return;
		}
		minecraft.gui.setScreen(new EditEmpireScreen(this));
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
		graphics.text(this.font, this.title, centerX - this.font.width(this.title) / 2, 8, 0xFFFFFF);

		if (ClientGuiData.getManageEmpireData() == null
				|| ClientGuiData.getManageEmpireData().empireId == null
				|| ClientGuiData.getManageEmpireData().empireId.isEmpty()) {
			Component loading = Component.translatable("gui.siegedempires.loading");
			graphics.text(this.font, loading, centerX - this.font.width(loading) / 2, this.height / 2 + 30, 0xAAAAAA);
		}
	}

	@Override
	public void onClose() {
		ClientGuiData.setManageEmpireListener(null);
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
			int editY,
			int wartownsY,
			int topY,
			int sideWidth
	) {
	}
}
