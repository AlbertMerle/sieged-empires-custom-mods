package com.siegedempires.client.gui;

import com.siegedempires.banner.BannerHelper;
import com.siegedempires.banner.CustomBannerDesign;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Consumer;

/**
 * Paint-style 20×40 banner editor. Left: 16-dye palette + pencil/bucket tools.
 * Right: pixel canvas with hairline grid. Save writes the design back to the
 * Create Town/Empire parent cache.
 */
public class BannerPaintScreen extends Screen {
	private enum Tool {
		PENCIL,
		BUCKET
	}

	/** Preferred cell size; scaled down only if the screen is too short/narrow. */
	private static final int TARGET_CELL = 9;
	private static final int MIN_CELL = 6;
	private static final int SWATCH = 18;
	private static final int SWATCH_GAP = 4;
	private static final int PALETTE_COLS = 4;
	private static final int PALETTE_ROWS = 4;
	private static final int TOOL_SIZE = 22;
	private static final int TOOL_GAP = 6;
	/** Semi-transparent 1px hairlines so dye fills dominate. */
	private static final int GRID_LINE = 0x66000000;
	private static final int SELECT_OUTLINE = 0xFFFFFFFF;
	private static final int TOOL_BG = 0xFF2A2A2A;
	private static final int TOOL_ICON = 0xFFE8E8E8;

	private final Screen parent;
	private final Consumer<CustomBannerDesign> onSave;
	private final CustomBannerDesign design;
	private int selectedDye;
	private Tool tool = Tool.PENCIL;
	private boolean painting;

	private int cell;
	private int canvasW;
	private int canvasH;
	private int canvasX;
	private int canvasY;
	private int paletteX;
	private int paletteY;
	private int toolsX;
	private int toolsY;

	public BannerPaintScreen(Screen parent, CustomBannerDesign initial, Consumer<CustomBannerDesign> onSave) {
		super(Component.translatable("gui.siegedempires.edit_banner"));
		this.parent = parent;
		this.onSave = onSave;
		this.design = initial == null
				? CustomBannerDesign.solid("white")
				: initial.copy();
		this.selectedDye = CustomBannerDesign.dyeIndex(this.design.dominantColorName());
	}

	@Override
	protected void init() {
		super.init();
		clearWidgets();

		int paletteW = PALETTE_COLS * SWATCH + (PALETTE_COLS - 1) * SWATCH_GAP;
		int paletteH = PALETTE_ROWS * SWATCH + (PALETTE_ROWS - 1) * SWATCH_GAP;
		int toolsH = TOOL_SIZE + this.font.lineHeight + 8;
		int leftStackH = paletteH + 10 + toolsH;

		int maxByH = Math.max(MIN_CELL, (this.height - 80) / CustomBannerDesign.HEIGHT);
		int maxByW = Math.max(MIN_CELL, (this.width - paletteW - 60) / CustomBannerDesign.WIDTH);
		cell = Math.min(TARGET_CELL, Math.min(maxByH, maxByW));
		canvasW = CustomBannerDesign.WIDTH * cell;
		canvasH = CustomBannerDesign.HEIGHT * cell;

		int contentW = paletteW + 28 + canvasW;
		int contentLeft = this.width / 2 - contentW / 2;
		int contentTop = Math.max(36, (this.height - Math.max(leftStackH, canvasH) - 56) / 2);

		paletteX = contentLeft;
		paletteY = contentTop + Math.max(0, (canvasH - leftStackH) / 2);
		toolsX = paletteX;
		toolsY = paletteY + paletteH + 10;
		canvasX = contentLeft + paletteW + 28;
		canvasY = contentTop;

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.back"),
				button -> onClose()
		).bounds(12, this.height - 28, 100, 20).build());

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.save"),
				button -> onSaveClicked()
		).bounds(this.width - 112, this.height - 28, 100, 20).build());
	}

	private void onSaveClicked() {
		if (onSave != null) {
			onSave.accept(design.copy());
		}
		if (minecraft != null) {
			minecraft.gui.setScreen(parent);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, this.width, this.height, 0xC0101010);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		graphics.text(this.font, this.title,
				this.width / 2 - this.font.width(this.title) / 2, 12, 0xFFFFFF);

		Component paletteLabel = Component.translatable("gui.siegedempires.banner_palette");
		graphics.text(this.font, paletteLabel, paletteX, paletteY - this.font.lineHeight - 4, 0xCCCCCC);

		drawPalette(graphics);
		drawTools(graphics);
		drawCanvas(graphics);
	}

	private void drawPalette(GuiGraphicsExtractor graphics) {
		String[] colors = BannerHelper.getColorNames();
		for (int i = 0; i < colors.length; i++) {
			int col = i % PALETTE_COLS;
			int row = i / PALETTE_COLS;
			int x = paletteX + col * (SWATCH + SWATCH_GAP);
			int y = paletteY + row * (SWATCH + SWATCH_GAP);
			int rgb = BannerHelper.getColorRgb(colors[i]);
			graphics.fill(x, y, x + SWATCH, y + SWATCH, rgb);
			graphics.outline(x, y, SWATCH, SWATCH, i == selectedDye ? SELECT_OUTLINE : 0xFF000000);
		}
	}

	private void drawTools(GuiGraphicsExtractor graphics) {
		Component toolsLabel = Component.translatable("gui.siegedempires.banner_tools");
		graphics.text(this.font, toolsLabel, toolsX, toolsY, 0xCCCCCC);
		int iconY = toolsY + this.font.lineHeight + 2;

		drawToolButton(graphics, toolsX, iconY, Tool.PENCIL);
		drawToolButton(graphics, toolsX + TOOL_SIZE + TOOL_GAP, iconY, Tool.BUCKET);
	}

	private void drawToolButton(GuiGraphicsExtractor graphics, int x, int y, Tool which) {
		boolean selected = tool == which;
		graphics.fill(x, y, x + TOOL_SIZE, y + TOOL_SIZE, TOOL_BG);
		graphics.outline(x, y, TOOL_SIZE, TOOL_SIZE, selected ? SELECT_OUTLINE : 0xFF666666);
		if (which == Tool.PENCIL) {
			drawPencilIcon(graphics, x, y);
		} else {
			drawBucketIcon(graphics, x, y);
		}
	}

	/** Simple diagonal pencil glyph inside the tool button. */
	private static void drawPencilIcon(GuiGraphicsExtractor graphics, int x, int y) {
		int ox = x + 4;
		int oy = y + 4;
		// shaft
		for (int i = 0; i < 10; i++) {
			graphics.fill(ox + i, oy + 10 - i, ox + i + 3, oy + 12 - i, TOOL_ICON);
		}
		// tip (darker point)
		graphics.fill(ox + 10, oy + 1, ox + 13, oy + 4, 0xFF888888);
		graphics.fill(ox + 12, oy, ox + 14, oy + 2, 0xFF444444);
	}

	/** Simple paint-bucket glyph: handle + body + drip. */
	private static void drawBucketIcon(GuiGraphicsExtractor graphics, int x, int y) {
		int ox = x + 4;
		int oy = y + 3;
		// handle
		graphics.fill(ox + 4, oy, ox + 10, oy + 2, TOOL_ICON);
		graphics.fill(ox + 9, oy + 2, ox + 11, oy + 5, TOOL_ICON);
		// body
		graphics.fill(ox + 2, oy + 5, ox + 12, oy + 7, TOOL_ICON);
		graphics.fill(ox + 1, oy + 7, ox + 13, oy + 14, TOOL_ICON);
		graphics.fill(ox + 2, oy + 14, ox + 12, oy + 16, TOOL_ICON);
		// drip (uses a mid grey so it reads on the dark button)
		graphics.fill(ox + 6, oy + 15, ox + 8, oy + 18, 0xFF6EC8FF);
	}

	private void drawCanvas(GuiGraphicsExtractor graphics) {
		graphics.fill(canvasX - 2, canvasY - 2,
				canvasX + canvasW + 2, canvasY + canvasH + 2, 0xFF000000);
		for (int py = 0; py < CustomBannerDesign.HEIGHT; py++) {
			for (int px = 0; px < CustomBannerDesign.WIDTH; px++) {
				int x0 = canvasX + px * cell;
				int y0 = canvasY + py * cell;
				graphics.fill(x0, y0, x0 + cell, y0 + cell, design.getArgb(px, py));
			}
		}
		for (int px = 1; px < CustomBannerDesign.WIDTH; px++) {
			int x = canvasX + px * cell;
			graphics.fill(x, canvasY, x + 1, canvasY + canvasH, GRID_LINE);
		}
		for (int py = 1; py < CustomBannerDesign.HEIGHT; py++) {
			int y = canvasY + py * cell;
			graphics.fill(canvasX, y, canvasX + canvasW, y + 1, GRID_LINE);
		}
		graphics.outline(canvasX - 1, canvasY - 1, canvasW + 2, canvasH + 2, 0xFF888888);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			if (trySelectTool(event.x(), event.y())) {
				return true;
			}
			if (trySelectPalette(event.x(), event.y())) {
				return true;
			}
			if (applyToolAt(event.x(), event.y(), true)) {
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (painting && event.button() == 0 && tool == Tool.PENCIL) {
			applyToolAt(event.x(), event.y(), false);
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == 0) {
			painting = false;
		}
		return super.mouseReleased(event);
	}

	private boolean trySelectTool(double mouseX, double mouseY) {
		int iconY = toolsY + this.font.lineHeight + 2;
		if (inRect(mouseX, mouseY, toolsX, iconY, TOOL_SIZE, TOOL_SIZE)) {
			tool = Tool.PENCIL;
			return true;
		}
		if (inRect(mouseX, mouseY, toolsX + TOOL_SIZE + TOOL_GAP, iconY, TOOL_SIZE, TOOL_SIZE)) {
			tool = Tool.BUCKET;
			return true;
		}
		return false;
	}

	private boolean trySelectPalette(double mouseX, double mouseY) {
		String[] colors = BannerHelper.getColorNames();
		for (int i = 0; i < colors.length; i++) {
			int col = i % PALETTE_COLS;
			int row = i / PALETTE_COLS;
			int x = paletteX + col * (SWATCH + SWATCH_GAP);
			int y = paletteY + row * (SWATCH + SWATCH_GAP);
			if (inRect(mouseX, mouseY, x, y, SWATCH, SWATCH)) {
				selectedDye = i;
				return true;
			}
		}
		return false;
	}

	/**
	 * @param fromClick true on mouse down (bucket fills here); drag only paints with pencil
	 */
	private boolean applyToolAt(double mouseX, double mouseY, boolean fromClick) {
		int[] pixel = canvasPixel(mouseX, mouseY);
		if (pixel == null) {
			return false;
		}
		int px = pixel[0];
		int py = pixel[1];
		if (tool == Tool.BUCKET) {
			if (fromClick) {
				floodFill(px, py, selectedDye);
			}
			return true;
		}
		design.setDyeIndex(px, py, selectedDye);
		painting = true;
		return true;
	}

	private int[] canvasPixel(double mouseX, double mouseY) {
		if (cell < 1 || mouseX < canvasX || mouseY < canvasY
				|| mouseX >= canvasX + canvasW || mouseY >= canvasY + canvasH) {
			return null;
		}
		int px = (int) ((mouseX - canvasX) / cell);
		int py = (int) ((mouseY - canvasY) / cell);
		if (px < 0 || px >= CustomBannerDesign.WIDTH || py < 0 || py >= CustomBannerDesign.HEIGHT) {
			return null;
		}
		return new int[]{px, py};
	}

	/**
	 * 4-connected flood fill: replaces the contiguous region matching the seed
	 * pixel's dye with {@code fillDye}. Outside a closed outline (different dye)
	 * is untouched — e.g. black circle on white → fill inside blue stays outside white.
	 */
	private void floodFill(int startX, int startY, int fillDye) {
		int target = design.getDyeIndex(startX, startY);
		if (target == fillDye) {
			return;
		}
		boolean[] visited = new boolean[CustomBannerDesign.SIZE];
		Queue<int[]> queue = new ArrayDeque<>();
		queue.add(new int[]{startX, startY});
		visited[startY * CustomBannerDesign.WIDTH + startX] = true;

		while (!queue.isEmpty()) {
			int[] p = queue.poll();
			int x = p[0];
			int y = p[1];
			if (design.getDyeIndex(x, y) != target) {
				continue;
			}
			design.setDyeIndex(x, y, fillDye);
			offer(queue, visited, x + 1, y);
			offer(queue, visited, x - 1, y);
			offer(queue, visited, x, y + 1);
			offer(queue, visited, x, y - 1);
		}
	}

	private static void offer(Queue<int[]> queue, boolean[] visited, int x, int y) {
		if (x < 0 || x >= CustomBannerDesign.WIDTH || y < 0 || y >= CustomBannerDesign.HEIGHT) {
			return;
		}
		int idx = y * CustomBannerDesign.WIDTH + x;
		if (visited[idx]) {
			return;
		}
		visited[idx] = true;
		queue.add(new int[]{x, y});
	}

	private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	@Override
	public void onClose() {
		if (minecraft != null) {
			minecraft.gui.setScreen(parent);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
