package com.siegedempires.client.gui;

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
 * Shared base of the unified town / empire selection screens
 * ({@link TownSelectionScreen} and {@link EmpireSelectionScreen}).
 *
 * <p>Centered search bar at the top, then a centered single-column list: each row is a
 * flag icon plus a button, evenly spaced with no overlap.
 */
public abstract class SelectionListScreen extends Screen {
	protected static final int BUTTON_WIDTH = 200;
	protected static final int ROW_HEIGHT = 24;
	protected static final int ROW_GAP = 6;
	protected static final int FLAG_WIDTH = 16;
	protected static final int FLAG_HEIGHT = 24;
	private static final int FLAG_GAP = 8;
	private static final int TITLE_Y = 8;
	private static final int SEARCH_LABEL_Y = 26;
	private static final int SEARCH_BOX_Y = 40;
	private static final int SEARCH_BOX_HEIGHT = 20;
	private static final int SEARCH_TO_LIST_GAP = 14;
	private static final int LIST_TOP = SEARCH_BOX_Y + SEARCH_BOX_HEIGHT + SEARCH_TO_LIST_GAP;
	private static final int COLUMN_PADDING = 10;
	private static final int FOOTER_BUTTON_GAP = 8;
	private static final int BOTTOM_MARGIN = 8;
	private static final int LIST_ABOVE_FOOTER_GAP = 12;
	private static final int MESSAGE_ABOVE_ACTION_GAP = 10;
	private static final int MESSAGE_TICKS = 100;

	protected final Screen parent;
	protected EditBox searchBox;
	private int scrollOffset = 0;
	private int maxScroll = 0;
	private int tabIndex = 0;
	private boolean tabCompleting = false;
	private String message = "";
	private int messageTicks = 0;
	private boolean messageSuccess = false;
	private final List<Row> rows = new ArrayList<>();
	private final List<Entry> filteredEntries = new ArrayList<>();

	protected SelectionListScreen(Component title, Screen parent) {
		super(title);
		this.parent = parent;
	}

	protected static final class Entry {
		public final String id;
		public final String name;
		public final Component label;
		public final List<String> bannerPatterns;
		public final String bannerBaseColor;
		public final String bannerPixels;
		public final Runnable onClick;

		public Entry(String id, String name, Component label,
		             List<String> bannerPatterns, String bannerBaseColor,
		             Runnable onClick) {
			this(id, name, label, bannerPatterns, bannerBaseColor, null, onClick);
		}

		public Entry(String id, String name, Component label,
		             List<String> bannerPatterns, String bannerBaseColor,
		             String bannerPixels, Runnable onClick) {
			this.id = id;
			this.name = name;
			this.label = label;
			this.bannerPatterns = bannerPatterns;
			this.bannerBaseColor = bannerBaseColor;
			this.bannerPixels = bannerPixels;
			this.onClick = onClick;
		}
	}

	private static final class Row {
		final Button button;
		final Entry entry;
		final int flagX;
		final int flagY;

		Row(Button button, Entry entry, int flagX, int flagY) {
			this.button = button;
			this.entry = entry;
			this.flagX = flagX;
			this.flagY = flagY;
		}
	}

	protected abstract Component searchLabel();

	protected abstract void requestData();

	protected abstract void clearDataListener();

	protected abstract boolean isDataLoaded();

	protected abstract List<Entry> buildEntries();

	/** Optional action beside the search box (e.g. Find Town / Find Empire). */
	protected static final class HeaderAction {
		public final Component label;
		public final Runnable action;

		public HeaderAction(Component label, Runnable action) {
			this.label = label;
			this.action = action;
		}
	}

	protected HeaderAction headerAction() {
		return null;
	}

	protected void addExtraButtons(int centerX) {
	}

	protected boolean hasExtraButtons() {
		return false;
	}

	private int headerActionWidth(HeaderAction action) {
		return Math.max(72, this.font.width(action.label) + 12);
	}

	private int rowWidth() {
		return FLAG_WIDTH + FLAG_GAP + BUTTON_WIDTH;
	}

	private int rowLeft(int centerX) {
		return centerX - rowWidth() / 2;
	}

	protected int backButtonY() {
		return this.height - BOTTOM_MARGIN - ROW_HEIGHT;
	}

	protected int actionButtonY() {
		return backButtonY() - FOOTER_BUTTON_GAP - ROW_HEIGHT;
	}

	private int listBottomPadding() {
		int footer = BOTTOM_MARGIN + ROW_HEIGHT;
		if (hasExtraButtons()) {
			footer += FOOTER_BUTTON_GAP + ROW_HEIGHT;
		}
		return footer + LIST_ABOVE_FOOTER_GAP;
	}

	private int messageY() {
		int anchorY = hasExtraButtons() ? actionButtonY() : backButtonY();
		return anchorY - MESSAGE_ABOVE_ACTION_GAP - this.font.lineHeight;
	}

	@Override
	protected void init() {
		super.init();
		clearWidgets();
		rows.clear();
		filteredEntries.clear();

		int centerX = this.width / 2;
		int searchBoxX = centerX - BUTTON_WIDTH / 2;

		Component searchLabel = searchLabel();
		int searchLabelWidth = this.font.width(searchLabel);
		this.addRenderableWidget(new StringWidget(
				centerX - searchLabelWidth / 2, SEARCH_LABEL_Y, searchLabelWidth, this.font.lineHeight,
				searchLabel, this.font));

		searchBox = new EditBox(this.font, searchBoxX, SEARCH_BOX_Y, BUTTON_WIDTH, SEARCH_BOX_HEIGHT, searchLabel);
		searchBox.setMaxLength(32);
		searchBox.setResponder(value -> {
			if (!tabCompleting) {
				tabIndex = 0;
				scrollOffset = 0;
			}
			rebuildList();
		});
		this.addRenderableWidget(searchBox);

		HeaderAction header = headerAction();
		if (header != null) {
			int headerWidth = headerActionWidth(header);
			int headerX = searchBoxX + BUTTON_WIDTH + 8;
			this.addRenderableWidget(Button.builder(header.label, button -> header.action.run())
					.bounds(headerX, SEARCH_BOX_Y, headerWidth, SEARCH_BOX_HEIGHT).build());
		}

		setInitialFocus(searchBox);

		requestData();
		rebuildList();

		addExtraButtons(centerX);

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.back"),
				button -> onClose()
		).bounds(centerX - BUTTON_WIDTH / 2, backButtonY(), BUTTON_WIDTH, ROW_HEIGHT).build());
	}

	protected void rebuildList() {
		for (Row row : rows) {
			removeWidget(row.button);
		}
		rows.clear();
		filteredEntries.clear();

		List<Entry> entries = buildEntries();
		if (entries == null) {
			maxScroll = 0;
			return;
		}

		String search = getSearchQuery();
		for (Entry entry : entries) {
			String name = entry.name != null ? entry.name : "";
			if (name.toLowerCase(Locale.ROOT).startsWith(search)) {
				filteredEntries.add(entry);
			}
		}

		int centerX = this.width / 2;
		int buttonX = rowLeft(centerX) + FLAG_WIDTH + FLAG_GAP;
		int flagX = rowLeft(centerX);
		int rowStep = ROW_HEIGHT + ROW_GAP;
		int totalListHeight = filteredEntries.isEmpty() ? 0 : filteredEntries.size() * rowStep - ROW_GAP;
		maxScroll = Math.max(0, totalListHeight - listAreaHeight());
		scrollOffset = Math.min(scrollOffset, maxScroll);
		int y = LIST_TOP - scrollOffset;

		for (Entry entry : filteredEntries) {
			if (!isVisible(y, ROW_HEIGHT)) {
				y += rowStep;
				continue;
			}

			int flagY = y + (ROW_HEIGHT - FLAG_HEIGHT) / 2;
			Button button = Button.builder(entry.label, btn -> {
				if (entry.onClick != null) {
					entry.onClick.run();
				}
			}).bounds(buttonX, y, BUTTON_WIDTH, ROW_HEIGHT).build();
			this.addRenderableWidget(button);
			rows.add(new Row(button, entry, flagX, flagY));
			y += rowStep;
		}
	}

	private String getSearchQuery() {
		return searchBox != null ? searchBox.getValue().trim().toLowerCase(Locale.ROOT) : "";
	}

	private int listAreaHeight() {
		return Math.max(0, this.height - LIST_TOP - listBottomPadding());
	}

	private boolean isVisible(int y, int height) {
		return y + height >= LIST_TOP && y <= this.height - listBottomPadding();
	}

	protected void showMessage(String text, boolean success) {
		message = text;
		messageTicks = MESSAGE_TICKS;
		messageSuccess = success;
	}

	protected void showError(String text) {
		showMessage(text, false);
	}

	@Override
	public void tick() {
		super.tick();
		if (messageTicks > 0) {
			messageTicks--;
			if (messageTicks == 0) {
				message = "";
			}
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (searchBox != null && searchBox.isFocused() && event.key() == GLFW.GLFW_KEY_TAB
				&& !filteredEntries.isEmpty()) {
			if (event.hasShiftDown()) {
				tabIndex = (tabIndex - 1 + filteredEntries.size()) % filteredEntries.size();
			} else {
				tabIndex = (tabIndex + 1) % filteredEntries.size();
			}
			tabCompleting = true;
			searchBox.setValue(filteredEntries.get(tabIndex).name);
			tabCompleting = false;
			searchBox.moveCursorToEnd(false);
			rebuildList();
			return true;
		}

		if (searchBox != null && searchBox.keyPressed(event)) {
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (searchBox != null && searchBox.charTyped(event)) {
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (maxScroll > 0) {
			int rowStep = ROW_HEIGHT + ROW_GAP;
			scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) scrollY * rowStep));
			rebuildList();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int centerX = this.width / 2;
		int panelLeft = rowLeft(centerX) - COLUMN_PADDING;
		int panelWidth = rowWidth() + COLUMN_PADDING * 2;
		HeaderAction header = headerAction();
		if (header != null) {
			int searchBoxX = centerX - BUTTON_WIDTH / 2;
			int headerRight = searchBoxX + BUTTON_WIDTH + 8 + headerActionWidth(header);
			panelWidth = Math.max(panelWidth, headerRight - panelLeft + COLUMN_PADDING);
		}
		int panelTop = SEARCH_LABEL_Y - COLUMN_PADDING;
		int panelBottom = this.height - listBottomPadding() + COLUMN_PADDING;
		if (panelBottom > panelTop) {
			graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelBottom, 0x44000000);
			graphics.outline(panelLeft, panelTop, panelWidth, panelBottom - panelTop, 0xFF666666);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		int centerX = this.width / 2;
		graphics.text(this.font, this.title, centerX - this.font.width(this.title) / 2, TITLE_Y, 0xFFFFFFFF);

		for (Row row : rows) {
			if ((row.entry.bannerBaseColor == null || row.entry.bannerBaseColor.isEmpty())
					&& (row.entry.bannerPixels == null || row.entry.bannerPixels.isEmpty())) {
				continue;
			}
			BannerEditorWidgets.drawBannerFlag(graphics,
					row.entry.bannerPatterns, row.entry.bannerBaseColor, row.entry.bannerPixels,
					row.flagX, row.flagY, FLAG_WIDTH, FLAG_HEIGHT);
		}

		if (!isDataLoaded()) {
			Component loading = Component.translatable("gui.siegedempires.loading");
			graphics.text(this.font, loading, centerX - this.font.width(loading) / 2, LIST_TOP + 4, 0xFFAAAAAA);
		}

		if (!message.isEmpty()) {
			int color = messageSuccess ? 0xFF55FF55 : 0xFFFF5555;
			int messageWidth = this.font.width(message);
			graphics.text(this.font, Component.literal(message),
					centerX - messageWidth / 2, messageY(), color);
		}
	}

	@Override
	public void onClose() {
		clearDataListener();
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
