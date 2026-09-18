package com.siegedempires.compat.squaremap;

import com.siegedempires.Siegedempires;
import com.siegedempires.model.TownData;

/**
 * Soft-loaded when Squaremap is present. Renders town claim borders on the web map,
 * colored by each town flag's most common dye color.
 */
public final class SquaremapCompat {
	private static SquaremapClaimRenderer renderer;
	private static boolean registered;

	private SquaremapCompat() {
	}

	public static void register() {
		if (registered && renderer != null) {
			renderer.refreshAll();
			return;
		}
		try {
			renderer = new SquaremapClaimRenderer();
			renderer.registerLayers();
			renderer.refreshAll();
			registered = true;
			Siegedempires.LOGGER.info("Squaremap integration enabled — town claims will appear on the web map");
		} catch (Exception e) {
			Siegedempires.LOGGER.error("Failed to initialize Squaremap integration", e);
			renderer = null;
			registered = false;
		}
	}

	public static void onTownChanged(TownData town) {
		if (renderer == null) {
			return;
		}
		try {
			// Full refresh keeps borders correct when chunks transfer between towns
			// or a town loses its last chunk in a dimension.
			renderer.refreshAll();
		} catch (Exception e) {
			Siegedempires.LOGGER.warn("Squaremap claim refresh failed for town {}", town != null ? town.getId() : "null", e);
		}
	}

	public static void onTownRemoved(String townId) {
		if (renderer == null || townId == null) {
			return;
		}
		try {
			renderer.refreshAll();
		} catch (Exception e) {
			Siegedempires.LOGGER.warn("Squaremap claim refresh failed after removing town {}", townId, e);
		}
	}

	public static void disable() {
		if (renderer != null) {
			renderer.disable();
			renderer = null;
		}
		registered = false;
	}
}
