package com.siegedempires.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Two-column action button grid with a fixed panel height.
 * Extra rows scroll — buttons never draw or click outside the panel bounds.
 */
public final class ScrollableButtonGridPanel {
	public static final int COLS = 2;
	/** Slightly under vanilla 200 so side footers keep a gap at mid GUI scales. */
	public static final int BUTTON_WIDTH = 180;
	public static final int BUTTON_HEIGHT = 18;
	public static final int COLUMN_GAP = 6;
	public static final int ROW_GAP = 3;
	public static final int ROW_STRIDE = BUTTON_HEIGHT + ROW_GAP;
	private static final int PANEL_PADDING = 4;
	private static final int SCROLLBAR_WIDTH = 6;
	private static final int SCROLLBAR_GAP = 3;
	private static final int MIN_PANEL_HEIGHT = PANEL_PADDING * 2 + BUTTON_HEIGHT;

	private enum LeftSlotKind {
		BUTTON,
		SPACER
	}

	private record LeftSlot(LeftSlotKind kind, Button button) {}

	private final Consumer<Button> addWidget;
	private final List<Button> buttons = new ArrayList<>();
	private final List<LeftSlot> leftColumn = new ArrayList<>();
	private final List<Button> rightColumn = new ArrayList<>();
	private boolean columnMode;
	private int panelLeft;
	private int panelTop;
	private int panelWidth;
	private int panelHeight;
	private int contentHeight;
	private int scrollOffset;
	private boolean draggingScrollbar;
	private int dragGrabY;

	public ScrollableButtonGridPanel(Consumer<Button> addWidget) {
		this.addWidget = addWidget;
	}

	public Button add(Button button) {
		buttons.add(button);
		addWidget.accept(button);
		return button;
	}

	public Button addLeft(Button button) {
		columnMode = true;
		leftColumn.add(new LeftSlot(LeftSlotKind.BUTTON, button));
		addWidget.accept(button);
		return button;
	}

	public void addLeftSpacer() {
		columnMode = true;
		leftColumn.add(new LeftSlot(LeftSlotKind.SPACER, null));
	}

	public Button addRight(Button button) {
		columnMode = true;
		rightColumn.add(button);
		addWidget.accept(button);
		return button;
	}

	/**
	 * Places the panel centered on {@code centerX}, filling {@code [topY, bottomY)}.
	 * Content taller than that range becomes scrollable — nothing is laid out past {@code bottomY}.
	 */
	public void layout(int centerX, int topY, int bottomY) {
		int trackExtra = SCROLLBAR_WIDTH + SCROLLBAR_GAP;
		panelWidth = COLS * BUTTON_WIDTH + (COLS - 1) * COLUMN_GAP + PANEL_PADDING * 2 + trackExtra;
		panelLeft = centerX - panelWidth / 2;
		panelTop = topY;
		panelHeight = Math.max(MIN_PANEL_HEIGHT, bottomY - topY);
		contentHeight = measureContentHeight();
		clampScroll();
		repositionButtons();
	}

	public void relayout() {
		contentHeight = measureContentHeight();
		clampScroll();
		repositionButtons();
	}

	public int getPanelBottom() {
		return panelTop + panelHeight;
	}

	public int getPanelLeft() {
		return panelLeft;
	}

	public int getPanelTop() {
		return panelTop;
	}

	public int getPanelWidth() {
		return panelWidth;
	}

	public int getPanelHeight() {
		return panelHeight;
	}

	public boolean needsScrollbar() {
		return maxScroll() > 0;
	}

	public void renderBackground(GuiGraphicsExtractor graphics) {
		int x0 = panelLeft;
		int y0 = panelTop;
		graphics.fill(x0, y0, x0 + panelWidth, y0 + panelHeight, 0x44000000);
		graphics.outline(x0, y0, panelWidth, panelHeight, 0xFF666666);
		if (needsScrollbar()) {
			renderScrollbar(graphics);
		}
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (!needsScrollbar() || !isMouseOverPanel(mouseX, mouseY)) {
			return false;
		}
		int delta = scrollY > 0 ? -ROW_STRIDE : ROW_STRIDE;
		int before = scrollOffset;
		scrollOffset += delta;
		clampScroll();
		if (scrollOffset != before) {
			repositionButtons();
			return true;
		}
		return false;
	}

	public boolean mouseClicked(MouseButtonEvent event) {
		if (event.button() != 0 || !needsScrollbar()) {
			return false;
		}
		if (!isMouseOverScrollbar(event.x(), event.y())) {
			return false;
		}
		draggingScrollbar = true;
		dragGrabY = (int) event.y() - scrollbarThumbY();
		return true;
	}

	public boolean mouseDragged(MouseButtonEvent event) {
		if (!draggingScrollbar || event.button() != 0 || !needsScrollbar()) {
			return false;
		}
		int trackTop = scrollbarTrackTop();
		int trackHeight = scrollbarTrackHeight();
		int thumbHeight = scrollbarThumbHeight();
		int travel = Math.max(1, trackHeight - thumbHeight);
		int thumbY = (int) event.y() - dragGrabY;
		thumbY = Math.max(trackTop, Math.min(trackTop + travel, thumbY));
		float t = (float) (thumbY - trackTop) / (float) travel;
		scrollOffset = Math.round(t * maxScroll());
		clampScroll();
		repositionButtons();
		return true;
	}

	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == 0 && draggingScrollbar) {
			draggingScrollbar = false;
			return true;
		}
		return false;
	}

	public boolean isMouseOverPanel(double mouseX, double mouseY) {
		return mouseX >= panelLeft && mouseX < panelLeft + panelWidth
				&& mouseY >= panelTop && mouseY < panelTop + panelHeight;
	}

	private void repositionButtons() {
		if (columnMode) {
			repositionColumnLayout();
		} else {
			repositionRowMajorLayout();
		}
	}

	private void repositionColumnLayout() {
		int contentLeft = panelLeft + PANEL_PADDING;
		int contentTop = panelTop + PANEL_PADDING;
		int rightX = contentLeft + BUTTON_WIDTH + COLUMN_GAP;
		layoutLeftColumn(contentLeft, contentTop - scrollOffset);
		layoutRightColumn(rightX, contentTop - scrollOffset);
	}

	private int layoutLeftColumn(int x, int startY) {
		int y = startY;
		for (LeftSlot slot : leftColumn) {
			if (slot.kind() == LeftSlotKind.SPACER) {
				y += ROW_STRIDE;
				continue;
			}
			Button button = slot.button();
			if (button != null) {
				placeButton(button, x, y);
				y += ROW_STRIDE;
			}
		}
		return Math.max(0, y - startY - (y > startY ? ROW_GAP : 0));
	}

	private int layoutRightColumn(int x, int startY) {
		int y = startY;
		for (Button button : rightColumn) {
			placeButton(button, x, y);
			y += ROW_STRIDE;
		}
		return Math.max(0, y - startY - (y > startY ? ROW_GAP : 0));
	}

	private void repositionRowMajorLayout() {
		int contentLeft = panelLeft + PANEL_PADDING;
		int contentTop = panelTop + PANEL_PADDING - scrollOffset;
		int index = 0;
		for (Button button : buttons) {
			int row = index / COLS;
			int col = index % COLS;
			index++;
			int x = contentLeft + col * (BUTTON_WIDTH + COLUMN_GAP);
			int y = contentTop + row * ROW_STRIDE;
			placeButton(button, x, y);
		}
	}

	private void placeButton(Button button, int x, int y) {
		button.setPosition(x, y);
		button.setSize(BUTTON_WIDTH, BUTTON_HEIGHT);
		int viewTop = panelTop + PANEL_PADDING;
		int viewBottom = viewTop + usableContentHeight();
		// Fully inside the panel only — never draw/click over footer or diplomacy.
		button.visible = y >= viewTop && (y + BUTTON_HEIGHT) <= viewBottom;
	}

	private int measureContentHeight() {
		if (columnMode) {
			int leftSlots = 0;
			for (LeftSlot slot : leftColumn) {
				if (slot.kind() == LeftSlotKind.SPACER || slot.button() != null) {
					leftSlots++;
				}
			}
			int rightSlots = rightColumn.size();
			int rows = Math.max(leftSlots, rightSlots);
			if (rows <= 0) {
				return 0;
			}
			return rows * BUTTON_HEIGHT + (rows - 1) * ROW_GAP;
		}
		int count = buttons.size();
		if (count <= 0) {
			return 0;
		}
		int rows = (count + COLS - 1) / COLS;
		return rows * BUTTON_HEIGHT + (rows - 1) * ROW_GAP;
	}

	private int visibleContentHeight() {
		return Math.max(0, panelHeight - PANEL_PADDING * 2);
	}

	/** Whole rows only — leftover panel pixels stay empty so buttons never clip. */
	private int visibleRows() {
		int raw = visibleContentHeight();
		if (raw < BUTTON_HEIGHT) {
			return 1;
		}
		return Math.max(1, (raw + ROW_GAP) / ROW_STRIDE);
	}

	private int usableContentHeight() {
		int rows = visibleRows();
		return rows * BUTTON_HEIGHT + (rows - 1) * ROW_GAP;
	}

	private int maxScroll() {
		return Math.max(0, contentHeight - usableContentHeight());
	}

	private void clampScroll() {
		int max = maxScroll();
		scrollOffset = Math.max(0, Math.min(scrollOffset, max));
		if (ROW_STRIDE > 0) {
			scrollOffset = (scrollOffset / ROW_STRIDE) * ROW_STRIDE;
			scrollOffset = Math.max(0, Math.min(scrollOffset, max));
		}
	}

	private int scrollbarTrackLeft() {
		return panelLeft + panelWidth - PANEL_PADDING - SCROLLBAR_WIDTH;
	}

	private int scrollbarTrackTop() {
		return panelTop + PANEL_PADDING;
	}

	private int scrollbarTrackHeight() {
		return Math.max(1, visibleContentHeight());
	}

	private int scrollbarThumbHeight() {
		int track = scrollbarTrackHeight();
		int usable = usableContentHeight();
		if (contentHeight <= 0 || usable <= 0) {
			return track;
		}
		int thumb = Math.round(track * (usable / (float) contentHeight));
		return Math.max(12, Math.min(track, thumb));
	}

	private int scrollbarThumbY() {
		int trackTop = scrollbarTrackTop();
		int travel = Math.max(0, scrollbarTrackHeight() - scrollbarThumbHeight());
		if (maxScroll() <= 0 || travel <= 0) {
			return trackTop;
		}
		return trackTop + Math.round(travel * (scrollOffset / (float) maxScroll()));
	}

	private boolean isMouseOverScrollbar(double mouseX, double mouseY) {
		int x0 = scrollbarTrackLeft();
		int y0 = scrollbarTrackTop();
		return mouseX >= x0 && mouseX < x0 + SCROLLBAR_WIDTH
				&& mouseY >= y0 && mouseY < y0 + scrollbarTrackHeight();
	}

	private void renderScrollbar(GuiGraphicsExtractor graphics) {
		int x0 = scrollbarTrackLeft();
		int y0 = scrollbarTrackTop();
		int trackH = scrollbarTrackHeight();
		graphics.fill(x0, y0, x0 + SCROLLBAR_WIDTH, y0 + trackH, 0x66000000);
		int thumbY = scrollbarThumbY();
		int thumbH = scrollbarThumbHeight();
		graphics.fill(x0, thumbY, x0 + SCROLLBAR_WIDTH, thumbY + thumbH, 0xFFAAAAAA);
	}
}
