package com.siegedempires.client.gui;

import com.siegedempires.client.network.ClientGuiData;
import com.siegedempires.client.network.ClientNetworking;
import com.siegedempires.network.ManageEmpireData;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class StepDownEmperorScreen extends Screen {
	private static final int BUTTON_WIDTH = 220;
	private static final int LIST_TOP = 40;
	private static final int LIST_BOTTOM_PADDING = 56;

	private final Screen parent;
	private int scrollOffset = 0;
	private int maxScroll = 0;
	private final List<Button> listButtons = new ArrayList<>();
	private boolean hasCandidates = false;

	public StepDownEmperorScreen(Screen parent) {
		super(Component.translatable("gui.siegedempires.step_down_emperor_title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		super.init();
		clearWidgets();
		listButtons.clear();

		ClientGuiData.setManageEmpireListener(this::rebuildMonarchList);
		ClientNetworking.requestManageEmpireData();
		rebuildMonarchList();

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.back"),
				button -> onClose()
		).bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
	}

	private void rebuildMonarchList() {
		for (Button button : listButtons) {
			removeWidget(button);
		}
		listButtons.clear();
		hasCandidates = false;

		ManageEmpireData data = ClientGuiData.getManageEmpireData();
		if (data == null || data.memberTowns == null) {
			maxScroll = 0;
			return;
		}

		String selfUuid = minecraft != null && minecraft.player != null
				? minecraft.player.getUUID().toString()
				: "";

		List<ManageEmpireData.TownInfo> candidates = new ArrayList<>();
		for (ManageEmpireData.TownInfo town : data.memberTowns) {
			if (town.monarchName == null || town.monarchName.isEmpty()) {
				continue;
			}
			if (town.monarchUuid != null && town.monarchUuid.equalsIgnoreCase(selfUuid)) {
				continue;
			}
			candidates.add(town);
		}
		candidates.sort(Comparator.comparing(
				t -> t.monarchName.toLowerCase(), Comparator.naturalOrder()));

		int centerX = this.width / 2;
		int y = LIST_TOP - scrollOffset;

		for (ManageEmpireData.TownInfo town : candidates) {
			hasCandidates = true;
			int height = 20;
			if (!isVisible(y, height)) {
				y += height + 4;
				continue;
			}

			String townLabel = town.nation
					? "Nation of " + town.name
					: "Town of " + town.name;
			Component label = Component.literal(town.monarchName + " — " + townLabel);

			Button button = Button.builder(label,
					btn -> minecraft.gui.setScreen(new ConfirmStepDownEmperorScreen(this, town)))
					.bounds(centerX - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, height).build();
			this.addRenderableWidget(button);
			listButtons.add(button);
			y += height + 4;
		}

		maxScroll = Math.max(0, y + scrollOffset - (this.height - LIST_BOTTOM_PADDING));
	}

	private boolean isVisible(int y, int height) {
		return y + height >= LIST_TOP && y <= this.height - LIST_BOTTOM_PADDING;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (maxScroll > 0) {
			scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) scrollY * 10));
			rebuildMonarchList();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		int centerX = this.width / 2;
		graphics.text(this.font, this.title, centerX - this.font.width(this.title) / 2, 8, 0xFFFFFF);

		ManageEmpireData data = ClientGuiData.getManageEmpireData();
		if (data == null || data.empireId == null || data.empireId.isEmpty()) {
			Component loading = Component.translatable("gui.siegedempires.loading");
			graphics.text(this.font, loading, centerX - this.font.width(loading) / 2, LIST_TOP, 0xAAAAAA);
		} else if (!hasCandidates) {
			Component empty = Component.translatable("gui.siegedempires.step_down_emperor_no_monarchs");
			graphics.text(this.font, empty, centerX - this.font.width(empty) / 2, LIST_TOP, 0xAAAAAA);
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
}
