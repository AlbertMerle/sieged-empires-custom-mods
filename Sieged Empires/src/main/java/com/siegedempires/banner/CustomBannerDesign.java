package com.siegedempires.banner;

import net.minecraft.world.item.DyeColor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Fully custom town/empire banner: a loom-sized {@value #WIDTH}×{@value #HEIGHT}
 * grid of dye colours (one of the 16 vanilla dyes per pixel).
 *
 * <p>Persisted as an 800-char hex string (one nibble per pixel, dye ordinal 0–15)
 * on {@code TownData}/{@code EmpireData} as {@code bannerPixels}, and synced to
 * clients / VoxelMapSync for exact flag rendering without vanilla patterns.
 */
public final class CustomBannerDesign {
	public static final int WIDTH = 20;
	public static final int HEIGHT = 40;
	public static final int SIZE = WIDTH * HEIGHT;

	private static final String[] DYE_NAMES = {
			"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
			"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
	};

	private final byte[] dyes;

	private CustomBannerDesign(byte[] dyes) {
		if (dyes == null || dyes.length != SIZE) {
			throw new IllegalArgumentException("banner design must be " + SIZE + " pixels");
		}
		this.dyes = dyes;
	}

	public static CustomBannerDesign solid(String colorName) {
		byte fill = (byte) dyeIndex(colorName);
		byte[] dyes = new byte[SIZE];
		Arrays.fill(dyes, fill);
		return new CustomBannerDesign(dyes);
	}

	public static CustomBannerDesign solid(DyeColor color) {
		return solid(BannerHelper.getColorName(color));
	}

	/**
	 * Decode a persisted hex string. Returns {@code null} if missing/invalid
	 * (callers should fall back to legacy pattern rendering).
	 */
	public static CustomBannerDesign decode(String encoded) {
		if (!isValidEncoded(encoded)) {
			return null;
		}
		byte[] dyes = new byte[SIZE];
		for (int i = 0; i < SIZE; i++) {
			char c = encoded.charAt(i);
			int v = Character.digit(c, 16);
			if (v < 0) {
				return null;
			}
			dyes[i] = (byte) v;
		}
		return new CustomBannerDesign(dyes);
	}

	public static boolean isValidEncoded(String encoded) {
		if (encoded == null || encoded.length() != SIZE) {
			return false;
		}
		for (int i = 0; i < SIZE; i++) {
			if (Character.digit(encoded.charAt(i), 16) < 0) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Prefer custom pixels when present; otherwise a solid fill of the base dye
	 * (used when creating towns before the paint UI exists).
	 */
	public static CustomBannerDesign fromStoredOrSolid(String encoded, String baseColorName) {
		CustomBannerDesign decoded = decode(encoded);
		if (decoded != null) {
			return decoded;
		}
		return solid(baseColorName == null || baseColorName.isEmpty() ? "white" : baseColorName);
	}

	public String encode() {
		StringBuilder sb = new StringBuilder(SIZE);
		for (byte dye : dyes) {
			sb.append(Character.forDigit(dye & 0xF, 16));
		}
		return sb.toString();
	}

	public CustomBannerDesign copy() {
		return new CustomBannerDesign(Arrays.copyOf(dyes, dyes.length));
	}

	public int getDyeIndex(int x, int y) {
		if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT) {
			return 0;
		}
		return dyes[y * WIDTH + x] & 0xF;
	}

	public void setDyeIndex(int x, int y, int dyeIndex) {
		if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT) {
			return;
		}
		dyes[y * WIDTH + x] = (byte) (Math.floorMod(dyeIndex, 16));
	}

	public void setDyeName(int x, int y, String colorName) {
		setDyeIndex(x, y, dyeIndex(colorName));
	}

	public String getDyeName(int x, int y) {
		return DYE_NAMES[getDyeIndex(x, y)];
	}

	public int getArgb(int x, int y) {
		return BannerHelper.getColorRgb(getDyeName(x, y));
	}

	/** Most common dye name on this flag (ties → first seen order among max count). */
	public String dominantColorName() {
		Map<Integer, Integer> counts = new HashMap<>();
		int bestIdx = 0;
		int bestCount = -1;
		for (byte dye : dyes) {
			int idx = dye & 0xF;
			int next = counts.merge(idx, 1, Integer::sum);
			if (next > bestCount) {
				bestCount = next;
				bestIdx = idx;
			}
		}
		return DYE_NAMES[bestIdx];
	}

	public static int dyeIndex(String colorName) {
		if (colorName == null || colorName.isEmpty()) {
			return DyeColor.WHITE.getId();
		}
		DyeColor color = BannerHelper.parseColor(colorName);
		return color.getId();
	}

	public static String dyeName(int index) {
		int i = Math.floorMod(index, 16);
		return DYE_NAMES[i];
	}
}
