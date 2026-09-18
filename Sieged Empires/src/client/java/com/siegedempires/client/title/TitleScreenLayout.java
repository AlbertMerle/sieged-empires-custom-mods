package com.siegedempires.client.title;

import net.minecraft.client.gui.Font;

/**
 * Shared title-screen layout math for the join button and sponsor credit.
 */
public final class TitleScreenLayout {
	public static final int JOIN_BUTTON_WIDTH = 280;
	public static final int JOIN_BUTTON_HEIGHT = 36;
	public static final int JOIN_BUTTON_GAP = 24;
	/** Extra Y under the logo now that SP/MP/Realms are off the title menu. */
	public static final int JOIN_BUTTON_NUDGE = 16;
	public static final int MENU_SPACING = 24;
	public static final int MENU_BUTTON_HEIGHT = 20;

	private static final int SPONSOR_BUTTON_WIDTH = 118;
	private static final int SPONSOR_BUTTON_HEIGHT = 18;
	private static final int SPONSOR_TEXT_GAP = 4;
	private static final int LEFT_MARGIN = 8;
	private static final int TOP_MARGIN = 8;
	private static final int RIGHT_MARGIN = 8;
	private static final int FOOTER_RESERVE = 12;
	private static final int MENU_SPONSOR_GAP = 8;
	private static final int BOTTOM_SPACING = 8;

	private TitleScreenLayout() {
	}

	public static int estimateMenuBottom(int menuTop) {
		int joinTop = menuTop + JOIN_BUTTON_NUDGE;
		int afterJoin = joinTop + JOIN_BUTTON_HEIGHT + JOIN_BUTTON_GAP;
		// Icon row, Options/Quit, Other Play Options (SP/MP/Realms moved off the title screen).
		return afterJoin + 3 * MENU_SPACING + MENU_BUTTON_HEIGHT;
	}

	public static int menuLeft(int screenWidth) {
		return screenWidth / 2 - Math.max(JOIN_BUTTON_WIDTH, 200) / 2;
	}

	public static int menuRight(int screenWidth) {
		return screenWidth / 2 + Math.max(JOIN_BUTTON_WIDTH, 200) / 2;
	}

	public static int sponsorBlockHeight(Font font) {
		return font.lineHeight + SPONSOR_TEXT_GAP + SPONSOR_BUTTON_HEIGHT;
	}

	/**
	 * Shifts the main menu upward when the default top would leave no room for a bottom-center sponsor block.
	 */
	public static int adjustMenuTop(int screenHeight, Font font, int defaultTop) {
		int menuBottom = estimateMenuBottom(defaultTop);
		if (bottomCenterFits(screenHeight, font, menuBottom)) {
			return defaultTop;
		}

		int blockHeight = sponsorBlockHeight(font);
		int requiredHeight = menuBottom + MENU_SPONSOR_GAP + blockHeight + FOOTER_RESERVE;
		int overflow = requiredHeight - screenHeight;
		if (overflow <= 0) {
			return defaultTop;
		}
		return Math.max(TOP_MARGIN, defaultTop - overflow);
	}

	public static SponsorPlacement computeSponsorPlacement(Font font, int screenWidth, int screenHeight, int menuTop) {
		int textWidth = font.width(TitleScreenSponsor.SPONSOR_TEXT);
		int menuBottom = estimateMenuBottom(menuTop);

		int bottomButtonY = screenHeight - FOOTER_RESERVE - SPONSOR_BUTTON_HEIGHT - BOTTOM_SPACING;
		int bottomTextY = bottomButtonY - font.lineHeight - SPONSOR_TEXT_GAP;
		int bottomButtonX = screenWidth / 2 - SPONSOR_BUTTON_WIDTH / 2;
		int bottomTextX = screenWidth / 2 - textWidth / 2;
		if (placementValid(font, screenWidth, screenHeight, bottomTextX, bottomTextY, textWidth, bottomButtonX, bottomButtonY, menuTop, menuBottom)) {
			return new SponsorPlacement(
					bottomButtonX,
					bottomButtonY,
					bottomTextX,
					bottomTextY,
					Placement.BOTTOM_CENTER
			);
		}

		return topLeftPlacement(font, screenWidth, screenHeight, textWidth, menuTop);
	}

	private static SponsorPlacement topLeftPlacement(
			Font font,
			int screenWidth,
			int screenHeight,
			int textWidth,
			int menuTop
	) {
		int textX = LEFT_MARGIN;
		int textY = TOP_MARGIN;
		int buttonX = LEFT_MARGIN;
		int buttonY = textY + font.lineHeight + SPONSOR_TEXT_GAP;

		SiegedLogoLayout.TitlePlacement logo = SiegedLogoLayout.titlePlacement(screenWidth, TOP_MARGIN, menuTop);
		int blockBottom = buttonY + SPONSOR_BUTTON_HEIGHT;
		int blockRight = Math.max(textX + textWidth, buttonX + SPONSOR_BUTTON_WIDTH);
		if (rectsOverlap(textX, textY, blockRight, blockBottom, logo.x(), logo.y(), logo.x() + logo.width(), logo.y() + logo.height())) {
			textY = logo.y() + logo.height() + MENU_SPONSOR_GAP;
			buttonY = textY + font.lineHeight + SPONSOR_TEXT_GAP;
		}

		blockBottom = buttonY + SPONSOR_BUTTON_HEIGHT;
		int maxBottom = screenHeight - FOOTER_RESERVE;
		if (blockBottom > maxBottom) {
			buttonY = maxBottom - SPONSOR_BUTTON_HEIGHT;
			textY = buttonY - font.lineHeight - SPONSOR_TEXT_GAP;
		}
		if (textY < TOP_MARGIN) {
			textY = TOP_MARGIN;
			buttonY = textY + font.lineHeight + SPONSOR_TEXT_GAP;
		}

		blockRight = Math.max(textX + textWidth, buttonX + SPONSOR_BUTTON_WIDTH);
		if (blockRight > screenWidth - RIGHT_MARGIN) {
			textX = Math.max(LEFT_MARGIN, screenWidth - RIGHT_MARGIN - Math.max(textWidth, SPONSOR_BUTTON_WIDTH));
			buttonX = textX;
		}

		return new SponsorPlacement(buttonX, buttonY, textX, textY, Placement.TOP_LEFT);
	}

	private static boolean bottomCenterFits(int screenHeight, Font font, int menuBottom) {
		int buttonY = screenHeight - FOOTER_RESERVE - SPONSOR_BUTTON_HEIGHT - BOTTOM_SPACING;
		int textY = buttonY - font.lineHeight - SPONSOR_TEXT_GAP;
		return textY >= menuBottom + MENU_SPONSOR_GAP
				&& buttonY >= TOP_MARGIN
				&& buttonY + SPONSOR_BUTTON_HEIGHT <= screenHeight - FOOTER_RESERVE;
	}

	private static boolean placementValid(
			Font font,
			int screenWidth,
			int screenHeight,
			int textX,
			int textY,
			int textWidth,
			int buttonX,
			int buttonY,
			int menuTop,
			int menuBottom
	) {
		if (!fitsOnScreen(textX, textY, textWidth, buttonX, buttonY, screenWidth, screenHeight)) {
			return false;
		}
		int blockTop = textY;
		int blockBottom = buttonY + SPONSOR_BUTTON_HEIGHT;
		int blockLeft = Math.min(textX, buttonX);
		int blockRight = Math.max(textX + textWidth, buttonX + SPONSOR_BUTTON_WIDTH);
		return !rectsOverlap(
				blockLeft,
				blockTop,
				blockRight,
				blockBottom,
				menuLeft(screenWidth),
				menuTop,
				menuRight(screenWidth),
				menuBottom
		);
	}

	private static boolean fitsOnScreen(
			int textX,
			int textY,
			int textWidth,
			int buttonX,
			int buttonY,
			int screenWidth,
			int screenHeight
	) {
		int blockTop = textY;
		int blockBottom = buttonY + SPONSOR_BUTTON_HEIGHT;
		int blockLeft = Math.min(textX, buttonX);
		int blockRight = Math.max(textX + textWidth, buttonX + SPONSOR_BUTTON_WIDTH);
		return blockLeft >= LEFT_MARGIN
				&& blockTop >= TOP_MARGIN
				&& blockRight <= screenWidth - RIGHT_MARGIN
				&& blockBottom <= screenHeight - FOOTER_RESERVE;
	}

	private static boolean rectsOverlap(int leftA, int topA, int rightA, int bottomA, int leftB, int topB, int rightB, int bottomB) {
		return leftA < rightB && rightA > leftB && topA < bottomB && bottomA > topB;
	}

	public enum Placement {
		BOTTOM_CENTER,
		TOP_LEFT
	}

	public record SponsorPlacement(int buttonX, int buttonY, int textX, int textY, Placement placement) {
	}
}
