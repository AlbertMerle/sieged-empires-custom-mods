package com.voxmapsync.client.claims;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatterns;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws town claim flags using the real Minecraft banner atlas sprites
 * (same front-face crop as Loom / Sieged Empires {@code BannerEditorWidgets}),
 * so VoxelMap flags match the exact town banner design.
 *
 * <p>Kept local so voxelmapsync does not hard-depend on the SE client jar.
 * Falls back to a solid base-color fill if the atlas/registry is unavailable.
 */
public final class FlagPainter {
	/** Front-face crop of the 64×64 entity banner texture (same as LoomScreen). */
	private static final float BANNER_FACE_U = 21.0F / 64.0F;
	private static final float BANNER_FACE_V0 = 1.0F / 64.0F;
	private static final float BANNER_FACE_V1 = 41.0F / 64.0F;

	/** SE storage names → vanilla {@link BannerPatterns} keys (mirrors BannerHelper). */
	private static final Map<String, ResourceKey<BannerPattern>> PATTERN_KEYS = new HashMap<>();

	static {
		put("square_bottom_left", BannerPatterns.SQUARE_BOTTOM_LEFT);
		put("square_bottom_right", BannerPatterns.SQUARE_BOTTOM_RIGHT);
		put("square_top_left", BannerPatterns.SQUARE_TOP_LEFT);
		put("square_top_right", BannerPatterns.SQUARE_TOP_RIGHT);
		put("stripe_bottom", BannerPatterns.STRIPE_BOTTOM);
		put("stripe_top", BannerPatterns.STRIPE_TOP);
		put("stripe_left", BannerPatterns.STRIPE_LEFT);
		put("stripe_right", BannerPatterns.STRIPE_RIGHT);
		put("stripe_center", BannerPatterns.STRIPE_CENTER);
		put("stripe_middle", BannerPatterns.STRIPE_MIDDLE);
		put("stripe_downright", BannerPatterns.STRIPE_DOWNRIGHT);
		put("stripe_downleft", BannerPatterns.STRIPE_DOWNLEFT);
		put("small_stripes", BannerPatterns.STRIPE_SMALL);
		put("cross", BannerPatterns.CROSS);
		put("straight_cross", BannerPatterns.STRAIGHT_CROSS);
		put("triangle_bottom", BannerPatterns.TRIANGLE_BOTTOM);
		put("triangle_top", BannerPatterns.TRIANGLE_TOP);
		put("triangles_bottom", BannerPatterns.TRIANGLES_BOTTOM);
		put("triangles_top", BannerPatterns.TRIANGLES_TOP);
		put("diagonal_left", BannerPatterns.DIAGONAL_LEFT);
		put("diagonal_right", BannerPatterns.DIAGONAL_RIGHT);
		put("diagonal_up_left", BannerPatterns.DIAGONAL_LEFT_MIRROR);
		put("diagonal_up_right", BannerPatterns.DIAGONAL_RIGHT_MIRROR);
		put("circle", BannerPatterns.CIRCLE_MIDDLE);
		put("rhombus", BannerPatterns.RHOMBUS_MIDDLE);
		put("half_vertical", BannerPatterns.HALF_VERTICAL);
		put("half_horizontal", BannerPatterns.HALF_HORIZONTAL);
		put("half_vertical_right", BannerPatterns.HALF_VERTICAL_MIRROR);
		put("half_horizontal_bottom", BannerPatterns.HALF_HORIZONTAL_MIRROR);
		put("border", BannerPatterns.BORDER);
		put("curly_border", BannerPatterns.CURLY_BORDER);
		put("creeper", BannerPatterns.CREEPER);
		put("gradient", BannerPatterns.GRADIENT);
		put("gradient_up", BannerPatterns.GRADIENT_UP);
		put("bricks", BannerPatterns.BRICKS);
		put("skull", BannerPatterns.SKULL);
		put("flower", BannerPatterns.FLOWER);
		put("mojang", BannerPatterns.MOJANG);
		put("piglin", BannerPatterns.PIGLIN);
		put("globe", BannerPatterns.GLOBE);
		put("flow", BannerPatterns.FLOW);
		put("guster", BannerPatterns.GUSTER);
	}

	private FlagPainter() {
	}

	public static int dyeRgb(String colorName) {
		return parseColor(colorName).getTextureDiffuseColor();
	}

	public static void draw(
			GuiGraphicsExtractor graphics,
			List<String> bannerPatterns,
			String bannerBaseColor,
			int x, int y, int width, int height
	) {
		draw(graphics, bannerPatterns, bannerBaseColor, null, x, y, width, height);
	}

	public static void draw(
			GuiGraphicsExtractor graphics,
			List<String> bannerPatterns,
			String bannerBaseColor,
			String bannerPixels,
			int x, int y, int width, int height
	) {
		if (width < 2 || height < 2) {
			return;
		}
		if (drawPixelBanner(graphics, bannerPixels, x, y, width, height)) {
			return;
		}
		DyeColor base = parseColor(bannerBaseColor);
		int baseArgb = base.getTextureDiffuseColor();
		graphics.fill(x, y, x + width, y + height, baseArgb);

		HolderGetter<BannerPattern> lookup = patternLookup();
		if (lookup == null) {
			return;
		}
		try {
			blitBannerFace(graphics, graphics.getSprite(Sheets.BANNER_PATTERN_BASE),
					x, y, width, height, baseArgb);
			if (bannerPatterns == null) {
				return;
			}
			for (String patternData : bannerPatterns) {
				if (patternData == null || patternData.isBlank()) {
					continue;
				}
				String[] parts = patternData.split(":", 2);
				if (parts.length != 2) {
					continue;
				}
				String patternName = parts[1];
				if ("base".equals(patternName)) {
					continue;
				}
				ResourceKey<BannerPattern> key = parsePatternKey(patternName);
				var holderOpt = lookup.get(key);
				if (holderOpt.isEmpty()) {
					continue;
				}
				Holder<BannerPattern> holder = holderOpt.get();
				TextureAtlasSprite sprite = graphics.getSprite(Sheets.getBannerSprite(holder));
				blitBannerFace(graphics, sprite, x, y, width, height,
						parseColor(parts[0]).getTextureDiffuseColor());
			}
		} catch (RuntimeException ignored) {
			// Atlas/sprites unavailable (rare early boot); base fill already drawn.
		}
	}

	/**
	 * Draw SE custom 20×40 dye-index hex pixels. Returns true when drawn.
	 * Encoding matches {@code com.siegedempires.banner.CustomBannerDesign}.
	 */
	private static boolean drawPixelBanner(
			GuiGraphicsExtractor graphics,
			String bannerPixels,
			int x, int y, int width, int height
	) {
		if (bannerPixels == null || bannerPixels.length() != 800) {
			return false;
		}
		final int bw = 20;
		final int bh = 40;
		for (int py = 0; py < bh; py++) {
			int y0 = y + py * height / bh;
			int y1 = y + (py + 1) * height / bh;
			if (y1 <= y0) {
				continue;
			}
			for (int px = 0; px < bw; px++) {
				int digit = Character.digit(bannerPixels.charAt(py * bw + px), 16);
				if (digit < 0) {
					return false;
				}
				int x0 = x + px * width / bw;
				int x1 = x + (px + 1) * width / bw;
				if (x1 <= x0) {
					continue;
				}
				graphics.fill(x0, y0, x1, y1, dyeArgb(digit));
			}
		}
		return true;
	}

	private static int dyeArgb(int dyeId) {
		DyeColor color = DyeColor.byId(dyeId);
		if (color == null) {
			color = DyeColor.WHITE;
		}
		return color.getTextureDiffuseColor();
	}

	/**
	 * Blit the loom front-face region of a 64×64 banner atlas sprite into
	 * {@code x,y,w,h}, tinted with {@code argb}.
	 */
	private static void blitBannerFace(
			GuiGraphicsExtractor graphics,
			TextureAtlasSprite sprite,
			int x, int y, int width, int height,
			int argb
	) {
		int texW = Math.max(1, Math.round(width / BANNER_FACE_U));
		int texH = Math.max(1, Math.round(height / (BANNER_FACE_V1 - BANNER_FACE_V0)));
		int drawX = x;
		int drawY = y - Math.round(height * BANNER_FACE_V0 / (BANNER_FACE_V1 - BANNER_FACE_V0));
		graphics.enableScissor(x, y, x + width, y + height);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, drawX, drawY, texW, texH, argb);
		graphics.disableScissor();
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

	private static DyeColor parseColor(String colorName) {
		if (colorName == null || colorName.isBlank()) {
			return DyeColor.WHITE;
		}
		DyeColor color = DyeColor.byName(colorName.toLowerCase(), null);
		return color != null ? color : DyeColor.WHITE;
	}

	private static ResourceKey<BannerPattern> parsePatternKey(String patternName) {
		if (patternName == null || patternName.isEmpty()) {
			return BannerPatterns.STRIPE_BOTTOM;
		}
		ResourceKey<BannerPattern> known = PATTERN_KEYS.get(patternName);
		if (known != null) {
			return known;
		}
		Identifier id = patternName.indexOf(':') >= 0
				? Identifier.tryParse(patternName)
				: Identifier.withDefaultNamespace(patternName);
		if (id != null) {
			return ResourceKey.create(Registries.BANNER_PATTERN, id);
		}
		return BannerPatterns.STRIPE_BOTTOM;
	}

	private static void put(String name, ResourceKey<BannerPattern> key) {
		PATTERN_KEYS.put(name, key);
	}
}
