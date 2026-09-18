package com.siegedempires.client.gui;

/**
 * Shared placement for Manage Town and Manage Empire action button grids
 * and pinned footer buttons (Back, Step Down, Delete, Rename, Manage Wartowns).
 * <p>
 * Layout is reserved from the bottom up so the action grid, diplomacy strip,
 * and footer never overlap — even at high GUI scales / short screen heights.
 */
public final class ManageScreenLayout {
	public static final int ACTION_GRID_TOP = 28;
	/** Gap between the scroll panel bottom and the Diplomacy / Crown strip. */
	public static final int GRID_ABOVE_DIPLOMACY = 8;
	/** Gap between Diplomacy / Crown bottom and the Back row. */
	public static final int DIPLOMACY_ABOVE_BACK = 10;
	/** When there is no diplomacy strip, gap between grid and Back. */
	public static final int GRID_ABOVE_BACK = 12;
	public static final int DIPLOMACY_BUTTON_WIDTH = 220;
	public static final int DIPLOMACY_BUTTON_HEIGHT = 20;
	public static final int BOTTOM_SLOT_HEIGHT = 20;
	public static final int BOTTOM_MARGIN_SLOTS = 3;
	public static final int FOOTER_BUTTON_GAP = 6;
	public static final int BOTTOM_RIGHT_MARGIN = 12;
	public static final int BOTTOM_LEFT_MARGIN = 12;
	/** Minimum gap between the center Back button and a side footer button. */
	public static final int SIDE_CENTER_GAP = 8;
	public static final int MIN_SIDE_BUTTON_WIDTH = 100;

	private ManageScreenLayout() {
	}

	public static int deleteButtonY(int screenHeight) {
		return screenHeight - BOTTOM_MARGIN_SLOTS * BOTTOM_SLOT_HEIGHT
				- ScrollableButtonGridPanel.BUTTON_HEIGHT;
	}

	public static int stepDownButtonY(int screenHeight) {
		return deleteButtonY(screenHeight) - FOOTER_BUTTON_GAP
				- ScrollableButtonGridPanel.BUTTON_HEIGHT;
	}

	public static int backButtonY(int screenHeight) {
		return stepDownButtonY(screenHeight) - FOOTER_BUTTON_GAP
				- ScrollableButtonGridPanel.BUTTON_HEIGHT;
	}

	/**
	 * Bottom edge of the scrollable action panel. Leaves room for an optional
	 * diplomacy/crown strip above {@code backButtonY} with fixed gaps — no overlap.
	 */
	public static int actionGridBottom(int backButtonY, boolean hasDiplomacyStrip) {
		if (hasDiplomacyStrip) {
			return backButtonY - DIPLOMACY_ABOVE_BACK - DIPLOMACY_BUTTON_HEIGHT - GRID_ABOVE_DIPLOMACY;
		}
		return backButtonY - GRID_ABOVE_BACK;
	}

	public static int diplomacyButtonY(int actionGridBottom) {
		return actionGridBottom + GRID_ABOVE_DIPLOMACY;
	}

	public static int bottomLeftButtonX() {
		return BOTTOM_LEFT_MARGIN;
	}

	public static int bottomRightButtonX(int screenWidth, int buttonWidth) {
		return screenWidth - BOTTOM_RIGHT_MARGIN - buttonWidth;
	}

	/**
	 * Width for a bottom-left / bottom-right footer button that clears the
	 * centered Back button. Returns 0 when there is not enough horizontal room
	 * (caller should stack that action into the center column instead).
	 */
	public static int sideFooterButtonWidth(int screenWidth) {
		int centerLeft = screenWidth / 2 - ScrollableButtonGridPanel.BUTTON_WIDTH / 2;
		int centerRight = screenWidth / 2 + ScrollableButtonGridPanel.BUTTON_WIDTH / 2;
		int rightSpace = screenWidth - BOTTOM_RIGHT_MARGIN - centerRight - SIDE_CENTER_GAP;
		int leftSpace = centerLeft - BOTTOM_LEFT_MARGIN - SIDE_CENTER_GAP;
		int space = Math.min(leftSpace, rightSpace);
		if (space < MIN_SIDE_BUTTON_WIDTH) {
			return 0;
		}
		return Math.min(ScrollableButtonGridPanel.BUTTON_WIDTH, space);
	}

	public static boolean sideFooterFits(int screenWidth) {
		return sideFooterButtonWidth(screenWidth) >= MIN_SIDE_BUTTON_WIDTH;
	}
}
