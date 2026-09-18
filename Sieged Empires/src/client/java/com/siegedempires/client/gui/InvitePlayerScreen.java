package com.siegedempires.client.gui;

import com.siegedempires.client.network.ClientGuiData;
import com.siegedempires.client.network.ClientNetworking;
import com.siegedempires.network.ManageTownData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InvitePlayerScreen extends Screen {
	private static final int BUTTON_WIDTH = 200;
	private static final int LIST_TOP = 72;
	private static final int LIST_BOTTOM_PADDING = 80;

	private final Screen parent;
	private EditBox searchBox;
	private int scrollOffset = 0;
	private int maxScroll = 0;
	private int tabIndex = 0;
	private String errorMessage = "";
	private int errorTicks = 0;
	private boolean successMessage = false;
	private final List<Button> listButtons = new ArrayList<>();

	public InvitePlayerScreen(Screen parent) {
		super(Component.translatable("gui.siegedempires.invite_player_title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		super.init();
		clearWidgets();
		listButtons.clear();

		ClientGuiData.setManageTownListener(this::rebuildPlayerList);
		ClientNetworking.requestManageTownData();

		int centerX = this.width / 2;
		int contentLeft = centerX - BUTTON_WIDTH / 2;

		Component searchLabel = Component.translatable("gui.siegedempires.search_player");
		this.addRenderableWidget(new StringWidget(
				contentLeft, LIST_TOP - 28, this.font.width(searchLabel), this.font.lineHeight,
				searchLabel, this.font));

		searchBox = new EditBox(this.font, contentLeft, LIST_TOP - 14, BUTTON_WIDTH, 20,
				Component.translatable("gui.siegedempires.search_player"));
		searchBox.setMaxLength(16);
		searchBox.setResponder(value -> {
			tabIndex = 0;
			rebuildPlayerList();
		});
		this.addRenderableWidget(searchBox);
		setInitialFocus(searchBox);

		rebuildPlayerList();

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.invite"),
				button -> inviteSelectedPlayer()
		).bounds(centerX - 100, this.height - 52, BUTTON_WIDTH, 20).build());

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.back"),
				button -> onClose()
		).bounds(centerX - 100, this.height - 28, BUTTON_WIDTH, 20).build());
	}

	private List<String> getOnlinePlayers() {
		ManageTownData data = ClientGuiData.getManageTownData();
		if (data == null || data.onlinePlayers == null) {
			return List.of();
		}
		return data.onlinePlayers;
	}

	private List<String> getMatchingPlayers() {
		String search = searchBox != null ? searchBox.getValue().trim().toLowerCase(Locale.ROOT) : "";
		List<String> matches = new ArrayList<>();
		for (String player : getOnlinePlayers()) {
			if (player.toLowerCase(Locale.ROOT).startsWith(search)) {
				matches.add(player);
			}
		}
		return matches;
	}

	private void rebuildPlayerList() {
		for (Button button : listButtons) {
			removeWidget(button);
		}
		listButtons.clear();

		int centerX = this.width / 2;
		int y = LIST_TOP + 16 - scrollOffset;
		List<String> matches = getMatchingPlayers();

		for (String playerName : matches) {
			int height = 20;
			if (!isVisible(y, height)) {
				y += height + 2;
				continue;
			}

			Button button = Button.builder(Component.literal(playerName), btn -> invitePlayer(playerName))
					.bounds(centerX - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, height).build();
			this.addRenderableWidget(button);
			listButtons.add(button);
			y += height + 2;
		}

		maxScroll = Math.max(0, y + scrollOffset - (this.height - LIST_BOTTOM_PADDING));
	}

	private void inviteSelectedPlayer() {
		if (searchBox == null) {
			return;
		}

		String playerName = searchBox.getValue().trim();
		if (playerName.isEmpty()) {
			showError(Component.translatable("gui.siegedempires.player_name_required").getString());
			return;
		}

		invitePlayer(playerName);
	}

	private void invitePlayer(String playerName) {
		if (minecraft == null || minecraft.player == null) {
			return;
		}

		String safeName = playerName.replace(" ", "_");
		minecraft.player.connection.sendCommand("town invite " + safeName);
		showMessage(Component.translatable("gui.siegedempires.invite_sent", playerName).getString(), true);
		ClientNetworking.requestManageTownData();
	}

	private void showMessage(String message, boolean success) {
		errorMessage = message;
		errorTicks = 100;
		successMessage = success;
	}

	private void showError(String message) {
		showMessage(message, false);
	}

	private boolean isVisible(int y, int height) {
		return y + height >= LIST_TOP + 12 && y <= this.height - LIST_BOTTOM_PADDING;
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
	public boolean keyPressed(KeyEvent event) {
		if (searchBox != null && searchBox.isFocused() && event.key() == GLFW.GLFW_KEY_TAB) {
			List<String> matches = getMatchingPlayers();
			if (!matches.isEmpty()) {
				if (event.hasShiftDown()) {
					tabIndex = (tabIndex - 1 + matches.size()) % matches.size();
				} else {
					tabIndex = (tabIndex + 1) % matches.size();
				}
				searchBox.setValue(matches.get(tabIndex));
				searchBox.moveCursorToEnd(false);
				rebuildPlayerList();
				return true;
			}
		}

		if (searchBox != null && searchBox.keyPressed(event)) {
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (searchBox != null && searchBox.charTyped(event)) {
			tabIndex = 0;
			rebuildPlayerList();
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (maxScroll > 0) {
			scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) scrollY * 10));
			rebuildPlayerList();
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
			graphics.text(this.font, loading, centerX - this.font.width(loading) / 2, LIST_TOP + 20, 0xAAAAAA);
		}

		if (!errorMessage.isEmpty()) {
			int color = successMessage ? 0x55FF55 : 0xFF5555;
			int errorWidth = this.font.width(errorMessage);
			graphics.text(this.font, Component.literal(errorMessage).withStyle(
					successMessage ? ChatFormatting.GREEN : ChatFormatting.RED),
					centerX - errorWidth / 2, this.height - 76, color);
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
