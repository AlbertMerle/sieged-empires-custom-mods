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

/**
 * Lists every member of the player's town (online AND offline) so a Lord or
 * Monarch can pick the citizen who will receive a CitizenGiveLandBanner.
 *
 * <p>Mirrors {@link InvitePlayerScreen} but uses {@link ManageTownData#members}
 * instead of {@code onlinePlayers} so offline citizens are also visible.
 */
public class GiveCitizenLandScreen extends Screen {
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

	public GiveCitizenLandScreen(Screen parent) {
		super(Component.translatable("gui.siegedempires.give_citizen_land_title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		super.init();
		clearWidgets();
		listButtons.clear();

		ClientGuiData.setManageTownListener(this::rebuildMemberList);
		ClientNetworking.requestManageTownData();

		int centerX = this.width / 2;
		int contentLeft = centerX - BUTTON_WIDTH / 2;

		Component searchLabel = Component.translatable("gui.siegedempires.search_player");
		this.addRenderableWidget(new StringWidget(
				contentLeft, LIST_TOP - 28, this.font.width(searchLabel), this.font.lineHeight,
				searchLabel, this.font));

		searchBox = new EditBox(this.font, contentLeft, LIST_TOP - 14, BUTTON_WIDTH, 20,
				Component.translatable("gui.siegedempires.search_player"));
		searchBox.setMaxLength(32);
		searchBox.setResponder(value -> {
			tabIndex = 0;
			rebuildMemberList();
		});
		this.addRenderableWidget(searchBox);
		setInitialFocus(searchBox);

		rebuildMemberList();

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.create_citizen_banner"),
				button -> createCitizenBanner()
		).bounds(centerX - 100, this.height - 52, BUTTON_WIDTH, 20).build());

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.back"),
				button -> onClose()
		).bounds(centerX - 100, this.height - 28, BUTTON_WIDTH, 20).build());
	}

	private List<ManageTownData.MemberInfo> getMembers() {
		ManageTownData data = ClientGuiData.getManageTownData();
		if (data == null || data.members == null) {
			return List.of();
		}
		return data.members;
	}

	private List<ManageTownData.MemberInfo> getMatchingMembers() {
		String search = searchBox != null ? searchBox.getValue().trim().toLowerCase(Locale.ROOT) : "";
		List<ManageTownData.MemberInfo> matches = new ArrayList<>();
		for (ManageTownData.MemberInfo member : getMembers()) {
			if (member == null || member.name == null) {
				continue;
			}
			if (member.name.toLowerCase(Locale.ROOT).startsWith(search)) {
				matches.add(member);
			}
		}
		return matches;
	}

	private void rebuildMemberList() {
		for (Button button : listButtons) {
			removeWidget(button);
		}
		listButtons.clear();

		List<ManageTownData.MemberInfo> matches = getMatchingMembers();
		int buttonHeight = 14;
		int contentLeft = this.width / 2 - BUTTON_WIDTH / 2;
		int listBottom = this.height - LIST_BOTTOM_PADDING;
		int visibleHeight = listBottom - (LIST_TOP + 12);
		maxScroll = Math.max(0, matches.size() * buttonHeight - visibleHeight);
		scrollOffset = Math.min(scrollOffset, maxScroll);

		for (int i = 0; i < matches.size(); i++) {
			ManageTownData.MemberInfo member = matches.get(i);
			int y = LIST_TOP + 12 + i * buttonHeight - scrollOffset;
			if (!isVisible(y, buttonHeight)) {
				continue;
			}
			String label = member.name + " (" + member.role + ")";
			Button btn = Button.builder(
					Component.literal(label),
					button -> {
						searchBox.setValue(member.name);
						searchBox.moveCursorToEnd(false);
					}
			).bounds(contentLeft, y, BUTTON_WIDTH, buttonHeight).build();
			listButtons.add(btn);
			this.addRenderableWidget(btn);
		}
	}

	private void createCitizenBanner() {
		String playerName = searchBox != null ? searchBox.getValue().trim() : "";
		if (playerName.isEmpty()) {
			showError(Component.translatable("gui.siegedempires.player_name_required").getString());
			return;
		}
		minecraft.gui.setScreen(new ConfirmGiveCitizenLandScreen(this, playerName));
	}

	private void showError(String message) {
		errorMessage = message;
		errorTicks = 100;
		successMessage = false;
	}

	private void showMessage(String message, boolean success) {
		errorMessage = message;
		errorTicks = 100;
		successMessage = success;
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
			List<ManageTownData.MemberInfo> matches = getMatchingMembers();
			if (!matches.isEmpty()) {
				if (event.hasShiftDown()) {
					tabIndex = (tabIndex - 1 + matches.size()) % matches.size();
				} else {
					tabIndex = (tabIndex + 1) % matches.size();
				}
				searchBox.setValue(matches.get(tabIndex).name);
				searchBox.moveCursorToEnd(false);
				rebuildMemberList();
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
			rebuildMemberList();
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (maxScroll > 0) {
			scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) scrollY * 10));
			rebuildMemberList();
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
		} else if (getMatchingMembers().isEmpty()) {
			Component none = Component.translatable("gui.siegedempires.no_citizens_found");
			graphics.text(this.font, Component.literal(none.getString()).withStyle(ChatFormatting.GRAY),
					centerX - this.font.width(none) / 2, LIST_TOP + 20, 0xAAAAAA);
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
