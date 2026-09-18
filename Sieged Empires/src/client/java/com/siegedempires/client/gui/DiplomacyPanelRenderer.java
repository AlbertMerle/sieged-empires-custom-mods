package com.siegedempires.client.gui;

import com.siegedempires.network.DiplomacyData;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Scrollable allies/enemies list panel used by {@link DiplomacyScreen} and
 * the main menu side panels in {@link SiegedEmpiresGuiScreen}.
 */
public final class DiplomacyPanelRenderer {
	public static final int FLAG_WIDTH = 14;
	public static final int FLAG_HEIGHT = 22;
	public static final int ROW_HEIGHT = 28;
	public static final int ROW_GAP = 4;
	public static final int PANEL_PADDING = 6;
	public static final int SCROLLBAR_WIDTH = 8;
	public static final int NOTIFICATION_HEIGHT = 26;
	public static final int NOTIFICATION_GAP = 2;
	public static final int ONLINE_COLUMN_WIDTH = 52;

	/** Minecraft skips text draws when alpha is 0 — force fully opaque ARGB. */
	private static int opaque(int color) {
		return color | 0xFF000000;
	}

	private DiplomacyPanelRenderer() {
	}

	public static int contentHeight(List<DiplomacyData.FactionInfo> entries) {
		if (entries == null || entries.isEmpty()) {
			return 0;
		}
		return entries.size() * (ROW_HEIGHT + ROW_GAP) - ROW_GAP;
	}

	public static int notificationContentHeight(List<DiplomacyData.PendingNotification> notifications) {
		if (notifications == null || notifications.isEmpty()) {
			return 0;
		}
		return notifications.size() * (NOTIFICATION_HEIGHT + NOTIFICATION_GAP) - NOTIFICATION_GAP;
	}

	public static int maxScroll(int panelHeight, List<DiplomacyData.FactionInfo> entries) {
		int innerHeight = panelHeight - PANEL_PADDING * 2 - 14;
		return Math.max(0, contentHeight(entries) - innerHeight);
	}

	public static int maxNotificationScroll(int areaHeight, List<DiplomacyData.PendingNotification> notifications) {
		return Math.max(0, notificationContentHeight(notifications) - areaHeight);
	}

	public static void drawPanel(GuiGraphicsExtractor graphics, Font font,
								 int x, int y, int width, int height,
								 Component header, int headerColor,
								 List<DiplomacyData.FactionInfo> entries,
								 int nameColor, int scrollOffset) {
		drawPanel(graphics, font, x, y, width, height, header, headerColor, entries, nameColor, scrollOffset, true);
	}

	public static void drawPanel(GuiGraphicsExtractor graphics, Font font,
								 int x, int y, int width, int height,
								 Component header, int headerColor,
								 List<DiplomacyData.FactionInfo> entries,
								 int nameColor, int scrollOffset, boolean showOnlineCount) {
		graphics.fill(x, y, x + width, y + height, 0x88000000);
		graphics.outline(x, y, width, height, 0xFFAAAAAA);

		int headerWidth = font.width(header);
		graphics.text(font, header, x + (width - headerWidth) / 2, y + 4, opaque(headerColor));

		int listTop = y + PANEL_PADDING + font.lineHeight + 4;
		int listBottom = y + height - PANEL_PADDING;
		int listLeft = x + PANEL_PADDING;
		int listRight = x + width - PANEL_PADDING - SCROLLBAR_WIDTH - 2;

		int maxScroll = maxScroll(height, entries);
		int clampedScroll = Math.max(0, Math.min(maxScroll, scrollOffset));

		if (entries != null) {
			int rowY = listTop - clampedScroll;
			for (DiplomacyData.FactionInfo entry : entries) {
				if (rowY + ROW_HEIGHT >= listTop && rowY <= listBottom) {
					drawFactionRow(graphics, font, entry, listLeft, listRight, rowY, nameColor, showOnlineCount);
				}
				rowY += ROW_HEIGHT + ROW_GAP;
			}
		}

		if (entries != null && !entries.isEmpty()) {
			drawScrollbar(graphics, x, width, listTop, listBottom, maxScroll, clampedScroll);
		}
	}

	public static void drawFactionRow(GuiGraphicsExtractor graphics, Font font,
	                                  DiplomacyData.FactionInfo entry,
	                                  int listLeft, int listRight, int rowY,
	                                  int nameColor, boolean showOnlineCount) {
		int flagY = rowY + (ROW_HEIGHT - FLAG_HEIGHT) / 2;
		if ((entry.bannerBaseColor != null && !entry.bannerBaseColor.isEmpty())
				|| (entry.bannerPixels != null && !entry.bannerPixels.isEmpty())) {
			BannerEditorWidgets.drawBannerFlag(graphics,
					entry.bannerPatterns, entry.bannerBaseColor, entry.bannerPixels,
					listLeft, flagY, FLAG_WIDTH, FLAG_HEIGHT);
		}

		int nameX = listLeft + FLAG_WIDTH + 6;
		int onlineWidth = 0;
		if (showOnlineCount) {
			Component online = Component.translatable("gui.siegedempires.online_count", entry.onlineCount);
			onlineWidth = Math.min(font.width(online), ONLINE_COLUMN_WIDTH);
		}
		int nameMaxWidth = Math.max(0, listRight - nameX - onlineWidth - (onlineWidth > 0 ? 6 : 0));

		String displayName = entry.name != null ? entry.name : "";
		Component nameComponent = Component.literal(truncateToWidth(font, displayName, nameMaxWidth));
		int nameY = rowY + 4;
		if (!entry.hasTrade && !entry.hasOpenBorders) {
			nameY = rowY + (ROW_HEIGHT - font.lineHeight) / 2;
		}
		graphics.text(font, nameComponent, nameX, nameY, opaque(nameColor));

		int tagY = nameY + font.lineHeight + 1;
		int tagX = nameX;
		if (entry.hasTrade) {
			Component trade = Component.translatable("gui.siegedempires.trade_tag");
			graphics.text(font, trade, tagX, tagY, opaque(0x55FF55));
			tagX += font.width(trade) + 4;
		}
		if (entry.hasOpenBorders) {
			Component borders = Component.translatable("gui.siegedempires.open_borders_tag");
			if (tagX + font.width(borders) <= listRight - onlineWidth) {
				graphics.text(font, borders, tagX, tagY, opaque(0x55FF55));
			}
		}

		if (showOnlineCount) {
			Component online = Component.translatable("gui.siegedempires.online_count", entry.onlineCount);
			int onlineDrawWidth = font.width(online);
			graphics.text(font, online, listRight - onlineDrawWidth, nameY, opaque(0xAAAAAA));
		}
	}

	private static String truncateToWidth(Font font, String text, int maxWidth) {
		if (text.isEmpty() || maxWidth <= 0) {
			return "";
		}
		if (font.width(text) <= maxWidth) {
			return text;
		}
		String ellipsis = "...";
		int ellipsisWidth = font.width(ellipsis);
		for (int end = text.length() - 1; end > 0; end--) {
			if (font.width(text.substring(0, end)) + ellipsisWidth <= maxWidth) {
				return text.substring(0, end) + ellipsis;
			}
		}
		return ellipsis;
	}

	public static void drawNotifications(GuiGraphicsExtractor graphics, Font font,
	                                     int x, int y, int width, int height,
	                                     List<DiplomacyData.PendingNotification> notifications,
	                                     int scrollOffset) {
		if (notifications == null || notifications.isEmpty()) {
			return;
		}

		graphics.fill(x, y, x + width, y + height, 0x88442200);
		graphics.outline(x, y, width, height, 0xFFFFAA00);

		int maxScroll = maxNotificationScroll(height - PANEL_PADDING * 2, notifications);
		int clampedScroll = Math.max(0, Math.min(maxScroll, scrollOffset));
		int rowY = y + PANEL_PADDING - clampedScroll;

		for (DiplomacyData.PendingNotification notification : notifications) {
			if (rowY + NOTIFICATION_HEIGHT >= y && rowY <= y + height) {
				drawNotificationRow(graphics, font, notification, x + PANEL_PADDING, rowY, width - PANEL_PADDING * 2);
			}
			rowY += NOTIFICATION_HEIGHT + NOTIFICATION_GAP;
		}
	}

	public static void drawNotificationRow(GuiGraphicsExtractor graphics, Font font,
	                                       DiplomacyData.PendingNotification notification,
	                                       int x, int y, int width) {
		int flagY = y + (NOTIFICATION_HEIGHT - FLAG_HEIGHT) / 2;
		if ((notification.bannerBaseColor != null && !notification.bannerBaseColor.isEmpty())
				|| (notification.bannerPixels != null && !notification.bannerPixels.isEmpty())) {
			BannerEditorWidgets.drawBannerFlag(graphics,
					notification.bannerPatterns, notification.bannerBaseColor, notification.bannerPixels,
					x, flagY, FLAG_WIDTH, FLAG_HEIGHT);
		}

		Component message = notificationMessage(notification);
		int textY = y + (NOTIFICATION_HEIGHT - font.lineHeight) / 2;
		graphics.text(font, Component.literal("\u2709 "), x + FLAG_WIDTH + 4, textY, opaque(0xFFFFFF));
		graphics.text(font, message, x + FLAG_WIDTH + 4 + font.width("\u2709 "), textY, opaque(0xFFFF55));
	}

	public static Component notificationMessage(DiplomacyData.PendingNotification notification) {
		return switch (notification.inviteType) {
			case "trade" -> Component.translatable("gui.siegedempires.trade_request_notification", notification.name);
			case "open_borders" ->
					Component.translatable("gui.siegedempires.open_borders_request_notification", notification.name);
			case "peace" -> Component.translatable("gui.siegedempires.peace_request_notification", notification.name);
			default -> Component.translatable("gui.siegedempires.ally_request_notification", notification.name);
		};
	}

	public static int notificationIndexAt(double mouseX, double mouseY, int x, int y, int width, int height,
	                                      List<DiplomacyData.PendingNotification> notifications, int scrollOffset) {
		if (notifications == null || notifications.isEmpty()) {
			return -1;
		}
		if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
			return -1;
		}

		int maxScroll = maxNotificationScroll(height - PANEL_PADDING * 2, notifications);
		int clampedScroll = Math.max(0, Math.min(maxScroll, scrollOffset));
		int rowY = y + PANEL_PADDING - clampedScroll;
		for (int i = 0; i < notifications.size(); i++) {
			if (mouseY >= rowY && mouseY < rowY + NOTIFICATION_HEIGHT) {
				return i;
			}
			rowY += NOTIFICATION_HEIGHT + NOTIFICATION_GAP;
		}
		return -1;
	}

	public static int listTop(int panelY, Font font) {
		return panelY + PANEL_PADDING + font.lineHeight + 4;
	}

	public static int listBottom(int panelY, int panelHeight) {
		return panelY + panelHeight - PANEL_PADDING;
	}

	public static int scrollbarX(int panelX, int panelWidth) {
		return panelX + panelWidth - PANEL_PADDING - SCROLLBAR_WIDTH;
	}

	public static int thumbHeight(int barHeight, int maxScroll) {
		if (maxScroll <= 0) {
			return barHeight;
		}
		return Math.max(12, barHeight * barHeight / (barHeight + maxScroll));
	}

	private static void drawScrollbar(GuiGraphicsExtractor graphics, int x, int width,
	                                  int barTop, int barBottom,
	                                  int maxScroll, int clampedScroll) {
		int barX = scrollbarX(x, width);
		int barHeight = Math.max(1, barBottom - barTop);
		graphics.fill(barX, barTop, barX + SCROLLBAR_WIDTH, barBottom, 0xFF333333);

		int thumbH = thumbHeight(barHeight, maxScroll);
		if (maxScroll <= 0) {
			graphics.fill(barX, barTop, barX + SCROLLBAR_WIDTH, barBottom, 0xFF888888);
			return;
		}
		int thumbTravel = Math.max(1, barHeight - thumbH);
		int thumbY = barTop + (clampedScroll * thumbTravel / maxScroll);
		graphics.fill(barX, thumbY, barX + SCROLLBAR_WIDTH, thumbY + thumbH, 0xFFAAAAAA);
	}

	public static boolean isMouseOverPanel(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	public static boolean isMouseOverScrollbar(double mouseX, double mouseY,
	                                           int x, int y, int width, int height, Font font) {
		int barX = scrollbarX(x, width);
		int barTop = listTop(y, font);
		int barBottom = listBottom(y, height);
		return mouseX >= barX && mouseX < barX + SCROLLBAR_WIDTH
				&& mouseY >= barTop && mouseY < barBottom;
	}

	/** Maps a mouse Y on the scrollbar track to a clamped scroll offset. */
	public static int scrollFromMouseY(double mouseY, int panelY, int panelHeight,
	                                   Font font, int maxScroll) {
		if (maxScroll <= 0) {
			return 0;
		}
		int barTop = listTop(panelY, font);
		int barBottom = listBottom(panelY, panelHeight);
		int barHeight = Math.max(1, barBottom - barTop);
		int thumbH = thumbHeight(barHeight, maxScroll);
		int thumbTravel = Math.max(1, barHeight - thumbH);
		double relative = mouseY - barTop - thumbH / 2.0;
		return (int) Math.max(0, Math.min(maxScroll, relative * maxScroll / thumbTravel));
	}

	public static int scrollBy(int scrollOffset, int maxScroll, double scrollDelta) {
		return Math.max(0, Math.min(maxScroll, scrollOffset - (int) scrollDelta * 10));
	}
}
