package com.siegedempires.client.gui;

import com.siegedempires.client.tutorial.ClientTutorialState;
import com.siegedempires.client.session.SessionJoinCinematic;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Scrollable How to Play text opened from the Game Menu Info button. */
public class GameMenuHowToPlayScreen extends Screen {
	private static final int PANEL_WIDTH = 340;
	private static final int PANEL_TOP = 28;
	private static final int BOTTOM_MARGIN = 48;
	private static final int PANEL_PADDING = 10;
	private static final int SCROLLBAR_WIDTH = 6;
	private static final int LINE_GAP = 2;
	private static final int BACK_WIDTH = 200;
	private static final int BACK_HEIGHT = 20;

	private final Screen parent;
	private final List<FormattedCharSequence> wrappedLines = new ArrayList<>();
	private int scrollOffset;
	private int maxScroll;
	private int panelX;
	private int panelY;
	private int panelHeight;
	private int listTop;
	private int listBottom;
	private int listInnerHeight;

	public GameMenuHowToPlayScreen(Screen parent) {
		super(Component.translatable("gui.siegedempires.game_menu.info_title"));
		this.parent = parent;
		ClientTutorialState.markHowToPlaySeen();
	}

	@Override
	protected void init() {
		super.init();
		layoutPanel();
		rebuildWrappedLines();

		int backX = this.width / 2 - BACK_WIDTH / 2;
		int backY = this.height - BOTTOM_MARGIN + 8;
		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.back"),
				button -> onClose()
		).bounds(backX, backY, BACK_WIDTH, BACK_HEIGHT).build());
	}

	private void layoutPanel() {
		panelX = this.width / 2 - PANEL_WIDTH / 2;
		panelY = PANEL_TOP;
		panelHeight = Math.max(80, this.height - PANEL_TOP - BOTTOM_MARGIN);
		listTop = panelY + PANEL_PADDING + this.font.lineHeight + 6;
		listBottom = panelY + panelHeight - PANEL_PADDING;
		listInnerHeight = Math.max(1, listBottom - listTop);
	}

	private void rebuildWrappedLines() {
		wrappedLines.clear();
		int textWidth = PANEL_WIDTH - PANEL_PADDING * 2 - SCROLLBAR_WIDTH - 4;
		for (String line : HowToPlayText.lines()) {
			if (line.isEmpty()) {
				wrappedLines.add(FormattedCharSequence.EMPTY);
				continue;
			}
			wrappedLines.addAll(this.font.split(Component.literal(line), textWidth));
		}
		int contentHeight = wrappedLines.size() * (this.font.lineHeight + LINE_GAP);
		if (!wrappedLines.isEmpty()) {
			contentHeight -= LINE_GAP;
		}
		maxScroll = Math.max(0, contentHeight - listInnerHeight);
		scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= panelX && mouseX < panelX + PANEL_WIDTH
				&& mouseY >= panelY && mouseY < panelY + panelHeight) {
			scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) scrollY * 12));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		drawBackground(graphics);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		drawBackground(graphics);
		layoutPanel();

		graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelHeight, 0xCC000000);
		graphics.outline(panelX, panelY, PANEL_WIDTH, panelHeight, 0xFFAAAAAA);

		Component title = this.getTitle();
		graphics.text(this.font, title,
				panelX + (PANEL_WIDTH - this.font.width(title)) / 2,
				panelY + 6, 0xFFFFFFFF);

		int y = listTop - scrollOffset;
		for (FormattedCharSequence line : wrappedLines) {
			if (y + this.font.lineHeight >= listTop && y <= listBottom) {
				graphics.text(this.font, line, panelX + PANEL_PADDING, y, 0xFFFFFFFF);
			}
			y += this.font.lineHeight + LINE_GAP;
		}

		drawScrollbar(graphics);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void drawScrollbar(GuiGraphicsExtractor graphics) {
		if (maxScroll <= 0) {
			return;
		}
		int barX = panelX + PANEL_WIDTH - PANEL_PADDING - SCROLLBAR_WIDTH;
		graphics.fill(barX, listTop, barX + SCROLLBAR_WIDTH, listBottom, 0xFF333333);
		int thumbHeight = Math.max(12, listInnerHeight * listInnerHeight / (listInnerHeight + maxScroll));
		int thumbTravel = listInnerHeight - thumbHeight;
		int thumbY = listTop + (scrollOffset * thumbTravel / maxScroll);
		graphics.fill(barX, thumbY, barX + SCROLLBAR_WIDTH, thumbY + thumbHeight, 0xFF888888);
	}

	private void drawBackground(GuiGraphicsExtractor graphics) {
		GameMenuBackground.draw(graphics, this.width, this.height);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.minecraft != null && SessionJoinCinematic.tryBreakGateOnKey(this.minecraft, event)) {
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(parent);
		}
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
