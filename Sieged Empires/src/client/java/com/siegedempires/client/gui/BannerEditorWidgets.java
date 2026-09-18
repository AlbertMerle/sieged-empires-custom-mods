package com.siegedempires.client.gui;

import com.siegedempires.banner.BannerHelper;
import com.siegedempires.banner.CustomBannerDesign;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatternLayers.Layer;

import java.util.List;

/**
 * Shared Loom-style banner designer widgets used by both the Create Town and
 * Create Empire screens. Centralizing the widgets here keeps the two
 * screens in lock-step (the user spec explicitly says "the Town banner
 * creation is perfect, just copy that layout").
 *
 * <p>Callers must hold mutable lists/strings for {@code bannerPatterns},
 * {@code bannerBaseColor}, {@code selectedPatternColor}, and
 * {@code selectedPattern}. The widgets read these on every click and
 * invoke {@code onChange} so the screen can rebuild itself.
 */
public final class BannerEditorWidgets {
	/** Width of the inner panel (must match CreateTownScreen / CreateEmpireScreen CONTENT_WIDTH). */
	public static final int CONTENT_WIDTH = 320;
	/** Loom-style layout constants. */
	public static final int BANNER_SLOT_SIZE = 18;
	public static final int PATTERN_COLUMNS = 6;
	public static final int PATTERN_BUTTON_SIZE = 18;
	public static final int COLOR_BUTTON_SIZE = 14;
	public static final int COLOR_COLUMNS = 8;
	/**
	 * Preview dimensions (large right-side banner preview).
	 * Must stay clear of the Edit Banner button (180px) on the left —
	 * max width ≈108 with a ≥16px gap inside {@link #CONTENT_WIDTH}.
	 * <p>Drawn with tinted banner-atlas sprites that fill this rect.
	 * Do <em>not</em> use {@code GuiGraphicsExtractor#bannerPattern} here:
	 * its PiP scale is hardcoded to 16 (loom 20×40), so enlarging the
	 * destination only adds empty padding.
	 */
	public static final int PREVIEW_WIDTH = 100;
	public static final int PREVIEW_HEIGHT = 200;

	/** Front-face crop of the 64×64 entity banner texture (same as LoomScreen). */
	private static final float BANNER_FACE_U = 21.0F / 64.0F;
	private static final float BANNER_FACE_V0 = 1.0F / 64.0F;
	private static final float BANNER_FACE_V1 = 41.0F / 64.0F;

	private BannerEditorWidgets() {}

	/** @return banner color names from {@link BannerHelper}. */
	public static String[] getBannerColors() { return BannerHelper.getColorNames(); }

	/**
	 * Banner pattern names for the loom fallback picker: all registered patterns
	 * (vanilla + datapack/mod), with vanilla order preserved first.
	 */
	public static List<String> getBannerPatterns() {
		HolderGetter<BannerPattern> lookup = patternLookup();
		if (lookup == null) {
			return List.of(BannerHelper.getPatternNames());
		}
		return BannerHelper.getSelectablePatternNames(lookup);
	}

	/**
	 * Vertical offset from section top to where {@link #drawBannerPreview} is drawn
	 * (callers set {@code previewY = yStart + 20}, then draw at {@code previewY + 15}).
	 */
	public static final int PREVIEW_DRAW_OFFSET = 35;

	/** Gap below the banner preview before the next section. */
	public static final int SECTION_GAP_AFTER_BANNER = 16;

	/**
	 * Compact banner section: Edit Banner button (opens paint UI), leaving
	 * room on the right for {@link #drawBannerPreview}.
	 *
	 * @return the y position immediately below the section (clears the preview)
	 */
	public static int addEditBannerButton(java.util.function.Consumer<AbstractWidget> widgetAdder,
										  Runnable onEditBanner,
										  int contentLeft,
										  int centerX,
										  int yStart) {
		// Left-aligned under town/empire fields so it does not overlap the right-side preview
		int buttonWidth = 180;
		widgetAdder.accept(Button.builder(
				Component.translatable("gui.siegedempires.edit_banner"),
				button -> onEditBanner.run()
		).bounds(contentLeft, yStart + 16, buttonWidth, 20).build());
		// Clear the full preview height so description / other widgets do not overlap it
		return yStart + PREVIEW_DRAW_OFFSET + PREVIEW_HEIGHT + SECTION_GAP_AFTER_BANNER;
	}

	/**
	 * Add the full Loom-style banner editor panel to a screen (fallback when FZMM is absent).
	 *
	 * <p>The panel layout, top to bottom:
	 * <ol>
	 *   <li>Three pattern slots (Loom-style "applied" column on the left)</li>
	 *   <li>Two-row color palette</li>
	 *   <li>+ and - buttons to add/remove the last pattern</li>
	 *   <li>Pattern selection grid (with "base" first, then every pattern)</li>
	 * </ol>
	 *
	 * <p>The panel does NOT include the right-side preview; the caller is
	 * expected to call {@link #drawBannerPreview} from its
	 * {@code extractRenderState}.
	 *
	 * @return the y position immediately below the panel so the caller can
	 *         continue laying out widgets underneath.
	 */
	public static int addBannerEditorPanel(java.util.function.Consumer<AbstractWidget> widgetAdder,
										   List<String> bannerPatterns,
										   String[] bannerBaseColorRef,
										   String[] selectedPatternColorRef,
										   String[] selectedPatternRef,
										   Runnable onChange,
										   int contentLeft,
										   int centerX,
										   int yStart) {
		int loomTop = yStart;
		String[] BANNER_COLORS = getBannerColors();
		List<String> BANNER_PATTERNS = getBannerPatterns();

		// Pattern slots (3 vertical, like Loom's left side)
		for (int i = 0; i < 3; i++) {
			int slotY = loomTop + i * 24;
			int patternIndex = bannerPatterns.size() - 3 + i;
			final int slotIndex = i;
			widgetAdder.accept(new PatternSlotButton(
				contentLeft, slotY, BANNER_SLOT_SIZE, BANNER_SLOT_SIZE,
				patternIndex >= 0 ? bannerPatterns.get(patternIndex) : null,
				() -> {
					int idx = bannerPatterns.size() - 3 + slotIndex;
					if (idx >= 0) {
						while (bannerPatterns.size() > idx) {
							bannerPatterns.remove(bannerPatterns.size() - 1);
						}
						onChange.run();
					}
				}
			));
		}

		// Color palette (16 colors in 2 rows of 8)
		int paletteY = loomTop + 80;
		for (int i = 0; i < BANNER_COLORS.length; i++) {
			String color = BANNER_COLORS[i];
			int cx = contentLeft + (i % COLOR_COLUMNS) * (COLOR_BUTTON_SIZE + 2);
			int cy = paletteY + (i / COLOR_COLUMNS) * (COLOR_BUTTON_SIZE + 2);
			boolean isSelected = color.equals(selectedPatternColorRef[0]);
			widgetAdder.accept(new ColorPaletteButton(
				cx, cy, COLOR_BUTTON_SIZE, COLOR_BUTTON_SIZE, color, isSelected,
				() -> {
					selectedPatternColorRef[0] = color;
					onChange.run();
				}
			));
		}

		// + and - buttons
		int controlsY = paletteY + 35;
		widgetAdder.accept(Button.builder(
			Component.literal("+"),
			button -> addSelectedPattern(bannerPatterns, bannerBaseColorRef, selectedPatternColorRef[0], selectedPatternRef[0], onChange)
		).bounds(contentLeft, controlsY, 30, 20).build());

		widgetAdder.accept(Button.builder(
			Component.literal("-"),
			button -> removeLastPattern(bannerPatterns, onChange)
		).bounds(contentLeft + 35, controlsY, 30, 20).build());

		// Pattern selection grid
		int patternGridX = contentLeft;
		int patternGridY = loomTop + 150;
		int patternButtonSize = PATTERN_BUTTON_SIZE;

		boolean isBaseSelected = "base".equals(selectedPatternRef[0]);
		widgetAdder.accept(new PatternSelectButton(
			patternGridX, patternGridY, patternButtonSize, patternButtonSize,
			"base", isBaseSelected,
			() -> {
				selectedPatternRef[0] = "base";
				onChange.run();
			}
		));

		for (int i = 0; i < BANNER_PATTERNS.size(); i++) {
			String pattern = BANNER_PATTERNS.get(i);
			int col = (i + 1) % PATTERN_COLUMNS;
			int row = (i + 1) / PATTERN_COLUMNS;
			int px = patternGridX + col * (patternButtonSize + 2);
			int py = patternGridY + row * (patternButtonSize + 2);
			boolean isSelected = pattern.equals(selectedPatternRef[0]);
			widgetAdder.accept(new PatternSelectButton(
				px, py, patternButtonSize, patternButtonSize,
				pattern, isSelected,
				() -> {
					selectedPatternRef[0] = pattern;
					onChange.run();
				}
			));
		}

		int patternRows = (BANNER_PATTERNS.size() + 1 + PATTERN_COLUMNS - 1) / PATTERN_COLUMNS;
		int gridHeight = patternRows * (patternButtonSize + 2);
		return patternGridY + gridHeight + SECTION_GAP_AFTER_BANNER;
	}

	/**
	 * Compute the x coordinate of the large banner preview given the
	 * CONTENT_WIDTH-anchored contentLeft. Mirrors the layout used by
	 * CreateTownScreen.
	 */
	public static int computePreviewX(int contentLeft) {
		return contentLeft + CONTENT_WIDTH - PREVIEW_WIDTH - 16;
	}

	/**
	 * Draw the large banner preview as a flat flag that fills {@code width×height}.
	 * Prefers custom pixel designs when {@code bannerPixels} is valid; otherwise
	 * uses real banner-atlas pattern sprites (same assets as loom / FZMM), tinted.
	 */
	public static void drawBannerPreview(GuiGraphicsExtractor graphics,
										 List<String> bannerPatterns,
										 String bannerBaseColor,
										 int x, int y) {
		drawScaledBanner(graphics, bannerPatterns, bannerBaseColor, null, x, y, PREVIEW_WIDTH, PREVIEW_HEIGHT);
	}

	public static void drawBannerPreview(GuiGraphicsExtractor graphics,
										 List<String> bannerPatterns,
										 String bannerBaseColor,
										 String bannerPixels,
										 int x, int y) {
		drawScaledBanner(graphics, bannerPatterns, bannerBaseColor, bannerPixels, x, y, PREVIEW_WIDTH, PREVIEW_HEIGHT);
	}

	/**
	 * Draw a banner flag for list rows / game menu headers.
	 * Same scalable sprite renderer as {@link #drawBannerPreview}.
	 */
	public static void drawBannerFlag(GuiGraphicsExtractor graphics,
									  List<String> bannerPatterns,
									  String bannerBaseColor,
									  int x, int y, int width, int height) {
		drawScaledBanner(graphics, bannerPatterns, bannerBaseColor, null, x, y, width, height);
	}

	public static void drawBannerFlag(GuiGraphicsExtractor graphics,
									  List<String> bannerPatterns,
									  String bannerBaseColor,
									  String bannerPixels,
									  int x, int y, int width, int height) {
		drawScaledBanner(graphics, bannerPatterns, bannerBaseColor, bannerPixels, x, y, width, height);
	}

	/**
	 * Flat, size-accurate banner: custom pixels when present, else base dye fill
	 * + tinted pattern sprites cropped to the loom front face.
	 */
	private static void drawScaledBanner(GuiGraphicsExtractor graphics,
										 List<String> bannerPatterns,
										 String bannerBaseColor,
										 String bannerPixels,
										 int x, int y, int width, int height) {
		if (width < 2 || height < 2) {
			return;
		}
		CustomBannerDesign design = CustomBannerDesign.decode(bannerPixels);
		if (design != null) {
			drawPixelBanner(graphics, design, x, y, width, height);
			return;
		}
		DyeColor base = BannerHelper.parseColor(bannerBaseColor);
		int baseArgb = base.getTextureDiffuseColor();
		graphics.fill(x, y, x + width, y + height, baseArgb);

		HolderGetter<BannerPattern> lookup = patternLookup();
		if (lookup == null) {
			return;
		}
		try {
			blitBannerFace(graphics, graphics.getSprite(Sheets.BANNER_PATTERN_BASE),
					x, y, width, height, baseArgb);
			List<String> layers = bannerPatterns == null ? List.of() : bannerPatterns;
			BannerPatternLayers patternLayers = BannerHelper.buildPatternLayers(layers, lookup);
			for (Layer layer : patternLayers.layers()) {
				TextureAtlasSprite sprite = graphics.getSprite(Sheets.getBannerSprite(layer.pattern()));
				blitBannerFace(graphics, sprite, x, y, width, height, layer.color().getTextureDiffuseColor());
			}
		} catch (RuntimeException ignored) {
			// Atlas/sprites unavailable (rare early boot); base fill already drawn.
		}
	}

	/** Scale a 20×40 dye grid into {@code width×height} with nearest-neighbour cells. */
	public static void drawPixelBanner(GuiGraphicsExtractor graphics,
									   CustomBannerDesign design,
									   int x, int y, int width, int height) {
		if (design == null || width < 1 || height < 1) {
			return;
		}
		for (int py = 0; py < CustomBannerDesign.HEIGHT; py++) {
			int y0 = y + py * height / CustomBannerDesign.HEIGHT;
			int y1 = y + (py + 1) * height / CustomBannerDesign.HEIGHT;
			if (y1 <= y0) {
				continue;
			}
			for (int px = 0; px < CustomBannerDesign.WIDTH; px++) {
				int x0 = x + px * width / CustomBannerDesign.WIDTH;
				int x1 = x + (px + 1) * width / CustomBannerDesign.WIDTH;
				if (x1 <= x0) {
					continue;
				}
				graphics.fill(x0, y0, x1, y1, design.getArgb(px, py));
			}
		}
	}

	/**
	 * Blit the loom front-face region of a 64×64 banner atlas sprite into
	 * {@code x,y,w,h}, tinted with {@code argb}.
	 */
	private static void blitBannerFace(GuiGraphicsExtractor graphics,
									   TextureAtlasSprite sprite,
									   int x, int y, int width, int height,
									   int argb) {
		int texW = Math.max(1, Math.round(width / BANNER_FACE_U));
		int texH = Math.max(1, Math.round(height / (BANNER_FACE_V1 - BANNER_FACE_V0)));
		int drawX = x;
		int drawY = y - Math.round(height * BANNER_FACE_V0 / (BANNER_FACE_V1 - BANNER_FACE_V0));
		graphics.enableScissor(x, y, x + width, y + height);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, drawX, drawY, texW, texH, argb);
		graphics.disableScissor();
	}

	/** Used by both the large preview and the per-pattern select button. */
	public static void drawPatternShape(GuiGraphicsExtractor graphics,
										int x, int y, int width, int height,
										String pattern, int color) {
		if (pattern.contains("stripe")) {
			if (pattern.contains("bottom")) {
				graphics.fill(x + width / 8, y + height * 5 / 8, x + width * 7 / 8, y + height * 7 / 8, color);
			} else if (pattern.contains("top")) {
				graphics.fill(x + width / 8, y + height / 8, x + width * 7 / 8, y + height * 3 / 8, color);
			} else if (pattern.contains("left")) {
				graphics.fill(x + width / 8, y + height / 8, x + width * 3 / 8, y + height * 7 / 8, color);
			} else if (pattern.contains("right")) {
				graphics.fill(x + width * 5 / 8, y + height / 8, x + width * 7 / 8, y + height * 7 / 8, color);
			} else if (pattern.contains("center")) {
				graphics.fill(x + width * 7 / 16, y + height / 8, x + width * 9 / 16, y + height * 7 / 8, color);
			} else if (pattern.contains("middle")) {
				graphics.fill(x + width / 8, y + height * 7 / 16, x + width * 7 / 8, y + height * 9 / 16, color);
			} else if (pattern.contains("small")) {
				graphics.fill(x + width * 3 / 16, y + height / 4, x + width * 5 / 16, y + height * 3 / 4, color);
				graphics.fill(x + width * 11 / 16, y + height / 4, x + width * 13 / 16, y + height * 3 / 4, color);
			} else if (pattern.contains("downright")) {
				for (int i = 0; i < width; i++) {
					int py = y + (height * i / width);
					graphics.fill(x + i, py, x + i + 2, py + height / 8, color);
				}
			} else if (pattern.contains("downleft")) {
				for (int i = 0; i < width; i++) {
					int py = y + height - (height * i / width) - height / 8;
					graphics.fill(x + i, py, x + i + 2, py + height / 8, color);
				}
			} else {
				graphics.fill(x + width / 8, y + height * 7 / 16, x + width * 7 / 8, y + height * 9 / 16, color);
			}
		} else if (pattern.contains("cross")) {
			graphics.fill(x + width * 7 / 16, y + height / 8, x + width * 9 / 16, y + height * 7 / 8, color);
			graphics.fill(x + width / 8, y + height * 7 / 16, x + width * 7 / 8, y + height * 9 / 16, color);
		} else if (pattern.contains("triangle")) {
			if (pattern.contains("bottom")) {
				graphics.fill(x + width / 8, y + height * 5 / 8, x + width * 7 / 8, y + height * 7 / 8, color);
				graphics.fill(x + width * 7 / 16, y + height / 8, x + width * 9 / 16, y + height * 5 / 8, color);
			} else if (pattern.contains("top")) {
				graphics.fill(x + width / 8, y + height / 8, x + width * 7 / 8, y + height * 3 / 8, color);
				graphics.fill(x + width * 7 / 16, y + height * 3 / 8, x + width * 9 / 16, y + height * 7 / 8, color);
			}
		} else if (pattern.contains("circle")) {
			graphics.fill(x + width * 5 / 16, y + height * 3 / 8, x + width * 11 / 16, y + height * 5 / 8, color);
		} else if (pattern.contains("border")) {
			graphics.outline(x + width / 8, y + height / 8, width * 3 / 4, height * 3 / 4, color);
		} else if (pattern.contains("half_vertical")) {
			if (pattern.contains("right")) {
				graphics.fill(x + width / 2, y + height / 8, x + width * 7 / 8, y + height * 7 / 8, color);
			} else {
				graphics.fill(x + width / 8, y + height / 8, x + width / 2, y + height * 7 / 8, color);
			}
		} else if (pattern.contains("half_horizontal")) {
			if (pattern.contains("bottom")) {
				graphics.fill(x + width / 8, y + height / 2, x + width * 7 / 8, y + height * 7 / 8, color);
			} else {
				graphics.fill(x + width / 8, y + height / 8, x + width * 7 / 8, y + height / 2, color);
			}
		} else if (pattern.contains("diagonal")) {
			if (pattern.contains("up_left") || pattern.contains("left")) {
				for (int i = 0; i < width; i++) {
					int py = y + height - (height * i / width) - height / 8;
					graphics.fill(x + i, py, x + i + 2, py + height / 8, color);
				}
			} else {
				for (int i = 0; i < width; i++) {
					int py = y + (height * i / width);
					graphics.fill(x + i, py, x + i + 2, py + height / 8, color);
				}
			}
		} else if (pattern.contains("rhombus")) {
			int cx = x + width / 2;
			int cy = y + height / 2;
			graphics.fill(cx - width / 8, cy - height * 3 / 16, cx + width / 8, cy + height * 3 / 16, color);
			graphics.fill(cx - width * 3 / 16, cy - height / 8, cx + width * 3 / 16, cy + height / 8, color);
		} else if (pattern.contains("gradient")) {
			for (int i = 0; i < height / 2; i++) {
				int alpha = 0xFF;
				int c = (color & 0x00FFFFFF) | (alpha << 24);
				if (pattern.contains("up")) {
					graphics.fill(x + width / 8, y + height / 8 + i, x + width * 7 / 8, y + height / 8 + i + 1, c);
				} else {
					graphics.fill(x + width / 8, y + height * 7 / 16 + i, x + width * 7 / 8, y + height * 7 / 16 + i + 1, c);
				}
			}
		} else if (pattern.contains("bricks")) {
			for (int row = 0; row < 4; row++) {
				int by = y + height / 4 + row * height / 8;
				int offset = (row % 2) * width / 8;
				for (int col = 0; col < 3; col++) {
					graphics.fill(x + width / 8 + offset + col * width * 5 / 24, by, x + width / 8 + offset + col * width * 5 / 24 + width / 8, by + height / 16, color);
				}
			}
		} else if (pattern.contains("flower")) {
			int cx = x + width / 2;
			int cy = y + height / 2;
			graphics.fill(cx - width / 16, cy - height * 3 / 16, cx + width / 16, cy + height * 3 / 16, color);
			graphics.fill(cx - width * 3 / 16, cy - height / 16, cx + width * 3 / 16, cy + height / 16, color);
			graphics.fill(cx - width / 16, cy - height / 16, cx + width / 16, cy + height / 16, 0xFFFFFFFF);
		} else if (pattern.contains("skull") || pattern.contains("creeper") || pattern.contains("piglin")
				|| pattern.contains("mojang") || pattern.contains("globe") || pattern.contains("flow")
				|| pattern.contains("guster")) {
			graphics.fill(x + width * 5 / 16, y + height * 3 / 8, x + width * 11 / 16, y + height * 5 / 8, color);
		}
	}

	/**
	 * Draw a mini banner-pattern icon inside a picker button (same crop as vanilla Loom).
	 */
	public static void drawPatternButtonSprite(GuiGraphicsExtractor graphics,
											   int x, int y, int width, int height,
											   TextureAtlasSprite bannerPatternSprite) {
		float patternU0 = bannerPatternSprite.getU0();
		float patternU1 = patternU0 + (bannerPatternSprite.getU1() - bannerPatternSprite.getU0()) * BANNER_FACE_U;
		float patternVSpan = bannerPatternSprite.getV1() - bannerPatternSprite.getV0();
		float patternV0 = bannerPatternSprite.getV0() + patternVSpan * BANNER_FACE_V0;
		float patternV1 = patternV0 + patternVSpan * (BANNER_FACE_V1 - BANNER_FACE_V0);
		int iconWidth = Math.max(4, width - 8);
		int iconHeight = Math.max(6, height - 6);
		int iconX = x + (width - iconWidth) / 2;
		int iconY = y + (height - iconHeight) / 2;
		graphics.fill(iconX, iconY, iconX + iconWidth, iconY + iconHeight, DyeColor.GRAY.getTextureDiffuseColor());
		graphics.blit(bannerPatternSprite.atlasLocation(), iconX, iconY, iconWidth, iconHeight,
				patternU0, patternU1, patternV0, patternV1);
	}

	private static HolderGetter<BannerPattern> patternLookup() {
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return null;
		}
		if (client.getConnection() != null) {
			return client.getConnection().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		}
		if (client.level != null) {
			return client.level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		}
		return null;
	}

	private static Holder<BannerPattern> resolvePatternHolder(String storageName) {
		HolderGetter<BannerPattern> lookup = patternLookup();
		if (lookup == null) {
			return null;
		}
		ResourceKey<BannerPattern> key = BannerHelper.parsePatternKey(storageName);
		return lookup.get(key).map(holder -> (Holder<BannerPattern>) holder).orElse(null);
	}

	/** Add the currently selected pattern (or set the base color). */
	public static void addSelectedPattern(List<String> bannerPatterns,
										  String[] bannerBaseColorRef,
										  String selectedPatternColor,
										  String selectedPattern,
										  Runnable onChange) {
		if (bannerPatterns.size() >= 6) {
			return;
		}
		if ("base".equals(selectedPattern)) {
			bannerBaseColorRef[0] = selectedPatternColor;
		} else {
			bannerPatterns.add(selectedPatternColor + ":" + selectedPattern);
		}
		onChange.run();
	}

	/** Remove the most recently added pattern. */
	public static void removeLastPattern(List<String> bannerPatterns, Runnable onChange) {
		if (!bannerPatterns.isEmpty()) {
			bannerPatterns.remove(bannerPatterns.size() - 1);
			onChange.run();
		}
	}

	// ========== inner widget classes ==========

	/** A single applied-pattern slot (Loom's left column). */
	public static class PatternSlotButton extends AbstractWidget {
		private final String patternData;
		private final Runnable onClick;

		public PatternSlotButton(int x, int y, int width, int height, String patternData, Runnable onClick) {
			super(x, y, width, height, Component.empty());
			this.patternData = patternData;
			this.onClick = onClick;
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			onClick.run();
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF8B8B8B);
			graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0xFFC6C6C6);

			if (patternData != null && !patternData.isEmpty()) {
				String[] parts = patternData.split(":", 2);
				String color = parts[0];
				String pattern = parts.length > 1 ? parts[1] : "base";
				int colorRgb = BannerHelper.getColorRgb(color);
				graphics.fill(getX() + 3, getY() + 3, getX() + width - 3, getY() + height - 3, colorRgb);
				if ("base".equals(pattern)) {
					graphics.fill(getX() + 6, getY() + 6, getX() + width - 6, getY() + height - 6, 0xFFFFFFFF);
				}
			}
			if (isHovered()) {
				graphics.outline(getX(), getY(), width, height, 0xFFFFFFFF);
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}

	/** A single color swatch in the palette. */
	public static class ColorPaletteButton extends AbstractWidget {
		private final String colorName;
		private final boolean isSelected;
		private final Runnable onSelect;

		public ColorPaletteButton(int x, int y, int width, int height,
								 String colorName, boolean isSelected, Runnable onSelect) {
			super(x, y, width, height, Component.empty());
			this.colorName = colorName;
			this.isSelected = isSelected;
			this.onSelect = onSelect;
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			onSelect.run();
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			int color = BannerHelper.getColorRgb(colorName);
			if (isSelected) {
				graphics.fill(getX() - 2, getY() - 2, getX() + width + 2, getY() + height + 2, 0xFF00FFFF);
			}
			graphics.fill(getX(), getY(), getX() + width, getY() + height, color);
			graphics.outline(getX(), getY(), width, height, isHovered() ? 0xFFFFFFFF : 0xFF000000);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}

	/** A button in the pattern selection grid (with mini-icon). */
	public static class PatternSelectButton extends AbstractWidget {
		private final String patternName;
		private final boolean isSelected;
		private final Runnable onSelect;

		public PatternSelectButton(int x, int y, int width, int height,
								   String patternName, boolean isSelected, Runnable onSelect) {
			super(x, y, width, height, Component.empty());
			this.patternName = patternName;
			this.isSelected = isSelected;
			this.onSelect = onSelect;
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			onSelect.run();
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			int bgColor = isSelected ? 0xFF6B6B6B : 0xFF8B8B8B;
			graphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
			graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0xFFC6C6C6);

			if ("base".equals(patternName)) {
				graphics.fill(getX() + 4, getY() + 4, getX() + width - 4, getY() + height - 4, 0xFF555555);
				Component text = Component.literal("B");
				int textWidth = Minecraft.getInstance().font.width(text);
				graphics.text(Minecraft.getInstance().font, text,
					getX() + (width - textWidth) / 2, getY() + height / 2 - 4, 0xFFFFFF);
			} else {
				Holder<BannerPattern> holder = resolvePatternHolder(patternName);
				if (holder != null) {
					try {
						TextureAtlasSprite sprite = graphics.getSprite(Sheets.getBannerSprite(holder));
						drawPatternButtonSprite(graphics, getX(), getY(), width, height, sprite);
					} catch (RuntimeException ignored) {
						drawPatternShape(graphics, getX(), getY(), width, height, patternName, 0xFF444444);
					}
				} else {
					drawPatternShape(graphics, getX(), getY(), width, height, patternName, 0xFF444444);
				}
			}

			if (isSelected) {
				graphics.outline(getX(), getY(), width, height, 0xFF00AA00);
			} else if (isHovered()) {
				graphics.outline(getX(), getY(), width, height, 0xFFFFFFFF);
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}
}
