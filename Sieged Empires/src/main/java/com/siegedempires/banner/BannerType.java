package com.siegedempires.banner;

public enum BannerType {
	CLAIM("claim"),
	/**
	 * Banner placed by a Lord or Monarch that grants the chunk where it is
	 * placed to a specific citizen. The banner removes itself after granting.
	 */
	CITIZEN_GIVE_LAND("citizen_give_land"),
	/**
	 * Banner placed by a Lord or Monarch that revokes whichever citizen
	 * currently owns the chunk where it is placed. The banner removes itself
	 * after the revocation and sends a private chat message to the placer
	 * reporting which chunk was revoked and who previously owned it.
	 */
	LAND_REVOKE("land_revoke"),
	/**
	 * Banner placed by a Monarch that marks an already-claimed town chunk as a
	 * Restricted Zone. The banner removes itself after registering the chunk.
	 */
	RESTRICTED("restricted"),
	/** War banner placed by invaders during an active invasion. */
	WAR("war");

	private final String id;

	BannerType(String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}

	public static BannerType fromId(String id) {
		if (id == null) {
			return null;
		}
		for (BannerType type : values()) {
			if (type.id.equalsIgnoreCase(id)) {
				return type;
			}
		}
		return null;
	}
}
