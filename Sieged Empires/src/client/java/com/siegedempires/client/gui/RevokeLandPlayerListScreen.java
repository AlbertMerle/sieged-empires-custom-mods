package com.siegedempires.client.gui;

import com.siegedempires.client.network.ClientGuiData;
import com.siegedempires.client.network.ClientNetworking;
import com.siegedempires.network.ManageTownData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lists every member of the player's town so a Lord or Monarch can pick the
 * citizen whose land grants they want to manage. Selecting a row opens
 * {@link RevokeLandPlayerActionScreen}.
 */
public class RevokeLandPlayerListScreen extends Screen {
	private static final int BUTTON_WIDTH = 200;
	private static final int BUTTON_HEIGHT = 20;

	private final Screen parent;
	private final List<ManageTownData.MemberInfo> members = new ArrayList<>();
	private final List<Button> listButtons = new ArrayList<>();
	private int scrollOffset = 0;
	private int maxScroll = 0;

	public RevokeLandPlayerListScreen(Screen parent) {
		super(Component.translatable("gui.siegedempires.revoke_land_title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		super.init();
		clearWidgets();
		listButtons.clear();

		ClientGuiData.setManageTownListener(this::rebuildList);
		ClientNetworking.requestManageTownData();

		rebuildList();

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.back"),
				button -> onClose()
		).bounds(this.width / 2 - 100, this.height - 28, BUTTON_WIDTH, BUTTON_HEIGHT).build());
	}

	private void rebuildList() {
		for (Button b : listButtons) {
			removeWidget(b);
		}
		listButtons.clear();

		members.clear();
		ManageTownData data = ClientGuiData.getManageTownData();
		if (data != null && data.members != null) {
			members.addAll(data.members);
		}
		members.sort(Comparator.comparing(m -> m.name == null ? "" : m.name.toLowerCase()));

		int buttonHeight = 14;
		int contentLeft = this.width / 2 - BUTTON_WIDTH / 2;
		int listBottom = this.height - 64;
		int visibleHeight = listBottom - 40;
		maxScroll = Math.max(0, members.size() * buttonHeight - visibleHeight);
		scrollOffset = Math.min(scrollOffset, maxScroll);

		for (int i = 0; i < members.size(); i++) {
			ManageTownData.MemberInfo member = members.get(i);
			int y = 40 + i * buttonHeight - scrollOffset;
			if (!isVisible(y, buttonHeight)) continue;
			String label = member.name + " (" + member.role + ")";
			Button btn = Button.builder(
					Component.literal(label),
					button -> minecraft.gui.setScreen(new RevokeLandPlayerActionScreen(this, member))
			).bounds(contentLeft, y, BUTTON_WIDTH, buttonHeight).build();
			listButtons.add(btn);
			this.addRenderableWidget(btn);
		}
	}

	private boolean isVisible(int y, int height) {
		return y + height >= 40 && y <= this.height - 64;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (maxScroll > 0) {
			scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) scrollY * 10));
			rebuildList();
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
			graphics.text(this.font, loading, centerX - this.font.width(loading) / 2, 40, 0xAAAAAA);
		} else if (members.isEmpty()) {
			Component none = Component.translatable("gui.siegedempires.no_citizens_found");
			graphics.text(this.font, Component.literal(none.getString()).withStyle(ChatFormatting.GRAY),
					centerX - this.font.width(none) / 2, 40, 0xAAAAAA);
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
