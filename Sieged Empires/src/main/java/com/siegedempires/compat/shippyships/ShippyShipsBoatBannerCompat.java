package com.siegedempires.compat.shippyships;

import com.caesiusleo.shippyships.AbstractShipEntity;
import com.siegedempires.banner.BoatBannerManager;

/**
 * Soft-loaded when Shippy Ships is present. Allows banners only on finished
 * Shippy Ships vessels — never on vanilla {@code minecraft:boat}.
 */
public final class ShippyShipsBoatBannerCompat {
	private ShippyShipsBoatBannerCompat() {
	}

	public static void register() {
		BoatBannerManager.setClaimAllowed(boat -> {
			if (boat instanceof AbstractShipEntity ship) {
				return !ship.getConstructionStatus();
			}
			return false;
		});
	}
}
