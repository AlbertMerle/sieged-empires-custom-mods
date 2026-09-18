package com.voxmapsync.client.claims;

import com.voxmapsync.claims.SiegedEmpiresClaimsBridge.ClaimTownDto;
import com.voxmapsync.client.MapViewportMath.RegionBounds;
import com.voxmapsync.config.SyncConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Draws merged claim fills (outer border only) + town flag + scaled labels
 * in VoxelMap world-map block space.
 */
public final class ClaimOverlayRenderer {
	private ClaimOverlayRenderer() {
	}

	public static void drawInMapSpace(GuiGraphicsExtractor graphics, Font font, String currentDimension) {
		drawInMapSpace(graphics, font, currentDimension, null);
	}

	public static void drawInMapSpace(
			GuiGraphicsExtractor graphics,
			Font font,
			String currentDimension,
			RegionBounds viewport) {
		List<ClaimTownDto> towns = ClientClaimsStore.towns();
		if (towns.isEmpty()) {
			return;
		}
		float opacity = SyncConfig.claimFillOpacity;
		int alpha = Math.clamp(Math.round(opacity * 255), 20, 230) << 24;

		for (ClaimTownDto town : towns) {
			if (town.chunks == null || town.chunks.isEmpty()) {
				continue;
			}
			if (currentDimension != null && town.dimension != null
					&& !dimensionMatches(currentDimension, town.dimension)) {
				continue;
			}

			Set<Long> claimed = new HashSet<>(town.chunks.size() * 2);
			for (int[] chunk : town.chunks) {
				if (chunk != null && chunk.length >= 2) {
					if (viewport != null && !viewport.intersectsChunk(chunk[0], chunk[1])) {
						continue;
					}
					claimed.add(pack(chunk[0], chunk[1]));
				}
			}
			if (claimed.isEmpty()) {
				continue;
			}

			int fill = alpha | (town.color & 0xFFFFFF);
			int border = 0xEE000000 | (town.color & 0xFFFFFF);

			int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
			long sumX = 0, sumZ = 0;
			int count = 0;

			for (long packed : claimed) {
				int cx = (int) (packed >> 32);
				int cz = (int) packed;
				int x1 = cx << 4;
				int z1 = cz << 4;
				int x2 = x1 + 16;
				int z2 = z1 + 16;
				graphics.fill(x1, z1, x2, z2, fill);

				if (!claimed.contains(pack(cx, cz - 1))) {
					graphics.fill(x1, z1, x2, z1 + 1, border);
				}
				if (!claimed.contains(pack(cx, cz + 1))) {
					graphics.fill(x1, z2 - 1, x2, z2, border);
				}
				if (!claimed.contains(pack(cx - 1, cz))) {
					graphics.fill(x1, z1, x1 + 1, z2, border);
				}
				if (!claimed.contains(pack(cx + 1, cz))) {
					graphics.fill(x2 - 1, z1, x2, z2, border);
				}

				minX = Math.min(minX, x1);
				minZ = Math.min(minZ, z1);
				maxX = Math.max(maxX, x2);
				maxZ = Math.max(maxZ, z2);
				sumX += x1 + 8;
				sumZ += z1 + 8;
				count++;
			}

			if (minX == Integer.MAX_VALUE || count == 0) {
				continue;
			}

			int width = maxX - minX;
			int height = maxZ - minZ;
			int span = Math.max(width, height);
			float scale = Math.clamp(span / 160f, 0.5f, 6.0f);
			int cx = (int) (sumX / count);
			int cz = (int) (sumZ / count);

			boolean hasFlag = SyncConfig.showClaimFlags
					&& ((town.bannerBase != null && !town.bannerBase.isBlank())
					|| (town.bannerPixels != null && town.bannerPixels.length() == 800));
			if (hasFlag) {
				int flagW = Math.max(8, Math.round(10 * scale));
				int flagH = Math.max(16, Math.round(20 * scale));
				int flagX = cx - flagW / 2;
				int flagY = cz - flagH - Math.round(6 * scale);
				FlagPainter.draw(graphics, town.bannerPatterns, town.bannerBase, town.bannerPixels, flagX, flagY, flagW, flagH);
				graphics.fill(flagX - 1, flagY - 1, flagX + flagW + 1, flagY, 0xCC000000);
				graphics.fill(flagX - 1, flagY + flagH, flagX + flagW + 1, flagY + flagH + 1, 0xCC000000);
				graphics.fill(flagX - 1, flagY, flagX, flagY + flagH, 0xCC000000);
				graphics.fill(flagX + flagW, flagY, flagX + flagW + 1, flagY + flagH, 0xCC000000);
			}

			if (!SyncConfig.showClaimLabels) {
				continue;
			}
			String label = town.name != null ? town.name : "?";
			if (town.empire != null && !town.empire.isBlank()) {
				label = label + " [" + town.empire + "]";
			}
			int labelY = hasFlag ? Math.round(2 * scale) : -4;
			graphics.pose().pushMatrix();
			graphics.pose().translate(cx, cz);
			graphics.pose().scale(scale, scale);
			graphics.centeredText(font, label, 0, labelY, 0xFFFFFFFF);
			graphics.pose().popMatrix();
		}
	}

	private static long pack(int cx, int cz) {
		return (((long) cx) << 32) ^ (cz & 0xFFFFFFFFL);
	}

	private static boolean dimensionMatches(String current, String claimDim) {
		if (current.equals(claimDim)) {
			return true;
		}
		String a = current.contains(":") ? current.substring(current.indexOf(':') + 1) : current;
		String b = claimDim.contains(":") ? claimDim.substring(claimDim.indexOf(':') + 1) : claimDim;
		return a.equalsIgnoreCase(b);
	}
}
