package com.siegedempires.client.gui;

import com.siegedempires.client.network.ClientGuiData;
import com.siegedempires.client.network.ClientNetworking;
import com.siegedempires.network.ManageTownData;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class StepDownMonarchScreen extends Screen {
	private static final int BUTTON_WIDTH = 200;
	private static final int LIST_TOP = 40;
	private static final int LIST_BOTTOM_PADDING = 56;

	private final Screen parent;
	private int scrollOffset = 0;
	private int maxScroll = 0;
	private final List<Button> listButtons = new ArrayList<>();
	private boolean hasLords = false;

	public StepDownMonarchScreen(Screen parent) {
		super(Component.translatable("gui.siegedempires.step_down_monarch_title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		super.init();
		clearWidgets();
		listButtons.clear();

		ClientGuiData.setManageTownListener(this::rebuildLordList);
		ClientNetworking.requestManageTownData();
		rebuildLordList();

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.back"),
				button -> onClose()
		).bounds(this.width / 2 - 100, this.height - 28, BUTTON_WIDTH, 20).build());
	}

	private void rebuildLordList() {
		for (Button button : listButtons) {
			removeWidget(button);
		}
		listButtons.clear();
		hasLords = false;

		ManageTownData data = ClientGuiData.getManageTownData();
		if (data == null) {
			maxScroll = 0;
			return;
		}

		int centerX = this.width / 2;
		int y = LIST_TOP - scrollOffset;

		for (ManageTownData.MemberInfo member : data.members) {
			if (!"Lord".equals(member.role)) {
				continue;
			}
			hasLords = true;

			int height = 20;
			if (!isVisible(y, height)) {
				y += height + 4;
				continue;
			}

			Button button = Button.builder(Component.literal(member.name),
					btn -> minecraft.gui.setScreen(new ConfirmStepDownMonarchScreen(this, member)))
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
			rebuildLordList();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		int centerX = this.width / 2;
		graphics.text(this.font, this.title, centerX - this.font.width(this.title) / 2, 8, 0xFFFFFF);

		if (ClientGuiData.getManageTownData() == null) {
			Component loading = Component.translatable("gui.siegedempires.loading");
			graphics.text(this.font, loading, centerX - this.font.width(loading) / 2, LIST_TOP, 0xAAAAAA);
		} else if (!hasLords) {
			Component empty = Component.translatable("gui.siegedempires.step_down_monarch_no_lords");
			graphics.text(this.font, empty, centerX - this.font.width(empty) / 2, LIST_TOP, 0xAAAAAA);
		}
	}

	@Override
	public void onClose() {
		ClientGuiData.setManageTownListener(null);
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
