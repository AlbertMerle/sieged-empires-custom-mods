package com.siegedempires.client.gui;

import com.siegedempires.client.network.ClientGuiData;
import com.siegedempires.client.network.ClientNetworking;
import com.siegedempires.client.session.SessionJoinCinematic;
import com.siegedempires.network.MailBuilder;
import com.siegedempires.network.MailData;
import com.siegedempires.network.payload.MailActionPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;

/**
 * Inbox list: each row shows sender flag, town/empire name, request message,
 * and Accept / Decline (or Dismiss for response mail).
 */
public class MailScreen extends Screen {
	private static final int FLAG_WIDTH = 16;
	private static final int FLAG_HEIGHT = 24;
	private static final int FLAG_GAP = 8;
	private static final int ROW_HEIGHT = 40;
	private static final int ROW_GAP = 6;
	private static final int PANEL_WIDTH = 440;
	private static final int LIST_TOP = 36;
	private static final int FOOTER = 32;
	private static final int BTN_W = 60;
	private static final int BTN_H = 18;
	private static final int BTN_GAP = 6;
	/** Minecraft chat gold / green / red. */
	private static final int COLOR_GOLD = 0xFFAA00;
	private static final int COLOR_GREEN = 0x55FF55;
	private static final int COLOR_RED = 0xFF5555;
	private static final int COLOR_DEFAULT = 0xFFFF55;

	private final Screen parent;
	private int scroll;
	private int listHeight;
	private int panelLeft;
	private int panelTop;

	public MailScreen(Screen parent) {
		super(Component.translatable("gui.siegedempires.mail.title"));
		this.parent = parent;
	}

	/** Minecraft skips text draws when alpha is 0 — force fully opaque ARGB. */
	private static int opaque(int color) {
		return color | 0xFF000000;
	}

	@Override
	protected void init() {
		super.init();
		ClientGuiData.setMailListener(this::rebuild);
		ClientNetworking.requestMailData();

		panelLeft = this.width / 2 - PANEL_WIDTH / 2;
		panelTop = 24;
		listHeight = this.height - panelTop - LIST_TOP - FOOTER - 16;

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.back"),
				button -> onClose()
		).bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
	}

	private void rebuild() {
		scroll = 0;
	}

	@Override
	public void onClose() {
		ClientGuiData.clearMailListener();
		if (minecraft == null) {
			return;
		}
		// Game Menu instances are reused via Screen.initialized → rebuildWidgets.
		// Always open a fresh Game Menu while the session gate still needs Join,
		// so the Join button cannot be lost after See Mail.
		if (parent instanceof GameMenuScreen) {
			if (SessionJoinCinematic.isGateActive()) {
				minecraft.gui.setScreen(new GameMenuScreen());
			} else {
				minecraft.gui.setScreen(null);
			}
			return;
		}
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.minecraft != null && SessionJoinCinematic.tryBreakGateOnKey(this.minecraft, event)) {
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		List<MailData.MailEntry> entries = mailEntries();
		int maxScroll = maxScroll(entries);
		scroll = Math.max(0, Math.min(maxScroll, scroll - (int) scrollY));
		return true;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (parent instanceof GameMenuScreen) {
			GameMenuBackground.draw(graphics, this.width, this.height);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (parent instanceof GameMenuScreen) {
			GameMenuBackground.draw(graphics, this.width, this.height);
		}
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		int panelBottom = panelTop + LIST_TOP + listHeight + 8;
		graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelBottom, 0xCC000000);
		graphics.outline(panelLeft, panelTop, PANEL_WIDTH, panelBottom - panelTop, opaque(0x555555));

		Component title = Component.translatable("gui.siegedempires.mail.title");
		int titleWidth = this.font.width(title);
		graphics.text(this.font, title, panelLeft + PANEL_WIDTH / 2 - titleWidth / 2, panelTop + 8, opaque(0xFFFFFF));

		List<MailData.MailEntry> entries = mailEntries();
		if (entries.isEmpty()) {
			Component empty = Component.translatable("gui.siegedempires.mail.empty");
			graphics.text(this.font, empty,
					panelLeft + PANEL_WIDTH / 2 - this.font.width(empty) / 2,
					panelTop + LIST_TOP + listHeight / 2 - 4, opaque(0xAAAAAA));
			return;
		}

		int y = panelTop + LIST_TOP - scroll;
		for (MailData.MailEntry entry : entries) {
			if (y + ROW_HEIGHT < panelTop + LIST_TOP) {
				y += ROW_HEIGHT + ROW_GAP;
				continue;
			}
			if (y > panelTop + LIST_TOP + listHeight) {
				break;
			}
			drawRow(graphics, entry, panelLeft + 8, y, PANEL_WIDTH - 16, mouseX, mouseY);
			y += ROW_HEIGHT + ROW_GAP;
		}
	}

	private void drawRow(GuiGraphicsExtractor graphics, MailData.MailEntry entry,
	                     int x, int y, int width, int mouseX, int mouseY) {
		graphics.fill(x, y, x + width, y + ROW_HEIGHT, 0x88333333);

		int flagX = x + 4;
		int flagY = y + (ROW_HEIGHT - FLAG_HEIGHT) / 2;
		if ((entry.bannerBaseColor != null && !entry.bannerBaseColor.isEmpty())
				|| (entry.bannerPixels != null && !entry.bannerPixels.isEmpty())) {
			BannerEditorWidgets.drawBannerFlag(graphics,
					entry.bannerPatterns, entry.bannerBaseColor, entry.bannerPixels,
					flagX, flagY, FLAG_WIDTH, FLAG_HEIGHT);
		}

		int textLeft = flagX + FLAG_WIDTH + FLAG_GAP;
		int buttonsWidth = entry.actionable ? BTN_W * 2 + BTN_GAP + 4 : BTN_W + 4;
		int textRight = x + width - buttonsWidth - 8;
		int textMaxWidth = Math.max(40, textRight - textLeft);

		String sender = entry.senderName != null ? entry.senderName : "";
		Component senderComponent = Component.literal(sender);
		String trimmedSender = this.font.plainSubstrByWidth(sender, textMaxWidth);
		if (!trimmedSender.equals(sender)) {
			senderComponent = Component.literal(trimmedSender);
		}
		graphics.text(this.font, senderComponent, textLeft, y + 6, opaque(0xFFFFFF));

		Component message = formatMessage(entry);
		String messageStr = message.getString();
		String trimmedMessage = this.font.plainSubstrByWidth(messageStr, textMaxWidth);
		Component messageDraw = trimmedMessage.equals(messageStr)
				? message
				: Component.literal(trimmedMessage);
		graphics.text(this.font, messageDraw, textLeft, y + 6 + this.font.lineHeight + 2,
				opaque(messageColor(entry)));

		int btnY = y + (ROW_HEIGHT - BTN_H) / 2;
		if (entry.actionable) {
			int acceptX = x + width - BTN_W * 2 - BTN_GAP - 4;
			int declineX = x + width - BTN_W - 4;
			drawActionButton(graphics, Component.translatable("gui.siegedempires.accept"),
					acceptX, btnY, BTN_W, BTN_H, mouseX, mouseY, 0xFF336633, 0xFF448844);
			drawActionButton(graphics, Component.translatable("gui.siegedempires.decline"),
					declineX, btnY, BTN_W, BTN_H, mouseX, mouseY, 0xFF663333, 0xFF884444);
		} else {
			int dismissX = x + width - BTN_W - 4;
			drawActionButton(graphics, Component.translatable("gui.siegedempires.mail.dismiss"),
					dismissX, btnY, BTN_W, BTN_H, mouseX, mouseY, 0xFF444444, 0xFF666666);
		}
	}

	private void drawActionButton(GuiGraphicsExtractor graphics, Component label,
	                              int x, int y, int w, int h, int mouseX, int mouseY,
	                              int color, int hoverColor) {
		boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		graphics.fill(x, y, x + w, y + h, hover ? hoverColor : color);
		graphics.outline(x, y, w, h, opaque(0x666666));
		int textX = x + w / 2 - this.font.width(label) / 2;
		graphics.text(this.font, label, textX, y + (h - 8) / 2, opaque(0xFFFFFF));
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != 0) {
			return super.mouseClicked(event, doubleClick);
		}
		List<MailData.MailEntry> entries = mailEntries();
		int y = panelTop + LIST_TOP - scroll;
		for (MailData.MailEntry entry : entries) {
			if (y + ROW_HEIGHT >= panelTop + LIST_TOP && y <= panelTop + LIST_TOP + listHeight) {
				if (handleRowClick(entry, panelLeft + 8, y, PANEL_WIDTH - 16, event.x(), event.y())) {
					return true;
				}
			}
			y += ROW_HEIGHT + ROW_GAP;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private boolean handleRowClick(MailData.MailEntry entry, int x, int y, int width, double mouseX, double mouseY) {
		int btnY = y + (ROW_HEIGHT - BTN_H) / 2;
		if (entry.actionable) {
			int acceptX = x + width - BTN_W * 2 - BTN_GAP - 4;
			int declineX = x + width - BTN_W - 4;
			if (inRect(mouseX, mouseY, acceptX, btnY, BTN_W, BTN_H)) {
				onAccept(entry);
				return true;
			}
			if (inRect(mouseX, mouseY, declineX, btnY, BTN_W, BTN_H)) {
				onDecline(entry);
				return true;
			}
		} else {
			int dismissX = x + width - BTN_W - 4;
			if (inRect(mouseX, mouseY, dismissX, btnY, BTN_W, BTN_H)) {
				onDismiss(entry);
				return true;
			}
		}
		return false;
	}

	private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private void onAccept(MailData.MailEntry entry) {
		if (MailBuilder.TYPE_CROWN.equals(entry.type)) {
			if (minecraft != null) {
				minecraft.gui.setScreen(new DukeDuchessScreen(this,
						entry.targetId, entry.arg2, entry.arg1));
			}
			return;
		}
		ClientNetworking.sendMailAction(MailActionPayload.ACTION_ACCEPT,
				entry.type, entry.targetId, entry.entityType, entry.entityName);
	}

	private void onDecline(MailData.MailEntry entry) {
		ClientNetworking.sendMailAction(MailActionPayload.ACTION_DECLINE,
				entry.type, entry.targetId, entry.entityType, entry.entityName);
	}

	private void onDismiss(MailData.MailEntry entry) {
		ClientNetworking.sendMailAction(MailActionPayload.ACTION_DISMISS,
				entry.type, entry.targetId, entry.entityType, entry.entityName);
	}

	private Component formatMessage(MailData.MailEntry entry) {
		if (MailBuilder.TYPE_RESPONSE.equals(entry.type)
				&& entry.arg2 != null && entry.arg2.startsWith("mail.kind.")) {
			return Component.translatable(entry.messageKey, entry.arg1, Component.translatable(entry.arg2));
		}
		if (entry.arg2 != null && !entry.arg2.isEmpty()) {
			return Component.translatable(entry.messageKey, entry.arg1, entry.arg2);
		}
		if (entry.arg1 != null && !entry.arg1.isEmpty()) {
			return Component.translatable(entry.messageKey, entry.arg1);
		}
		return Component.translatable(entry.messageKey);
	}

	/**
	 * Invites = gold; alliance/trade/open-borders/peace = green; war = red.
	 * Response mail colors follow the related topic.
	 */
	private static int messageColor(MailData.MailEntry entry) {
		if (entry == null || entry.type == null) {
			return COLOR_DEFAULT;
		}
		return switch (entry.type) {
			case MailBuilder.TYPE_TOWN_INVITE, MailBuilder.TYPE_EMPIRE_INVITE -> COLOR_GOLD;
			case MailBuilder.TYPE_ALLY, MailBuilder.TYPE_TRADE,
					MailBuilder.TYPE_OPEN_BORDERS, MailBuilder.TYPE_PEACE -> COLOR_GREEN;
			case MailBuilder.TYPE_WAR -> COLOR_RED;
			case MailBuilder.TYPE_RESPONSE -> responseMessageColor(entry.messageKey);
			default -> COLOR_DEFAULT;
		};
	}

	private static int responseMessageColor(String messageKey) {
		if (messageKey == null) {
			return COLOR_DEFAULT;
		}
		if (messageKey.contains("town_") || messageKey.contains("empire_")
				|| messageKey.contains("crown_")) {
			return COLOR_GOLD;
		}
		if (messageKey.contains("diplomacy_")) {
			return COLOR_GREEN;
		}
		if (messageKey.contains("war")) {
			return COLOR_RED;
		}
		return COLOR_DEFAULT;
	}

	private List<MailData.MailEntry> mailEntries() {
		MailData data = ClientGuiData.getMailData();
		if (data == null || data.entries == null) {
			return Collections.emptyList();
		}
		return data.entries;
	}

	private int maxScroll(List<MailData.MailEntry> entries) {
		if (entries.isEmpty()) {
			return 0;
		}
		int content = entries.size() * (ROW_HEIGHT + ROW_GAP) - ROW_GAP;
		return Math.max(0, content - listHeight);
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
