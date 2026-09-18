package com.distantnoise.sound;

/** Which footstep / cave-echo relay profile applies on the client. */
public enum FootstepRelayKind {
	STOMP(0),
	RUNNING(1),
	CAVE_WALK(2),
	CAVE_SPRINT(3),
	CAVE_FALL(4),
	/** Above-ground player sprint steps (block surface sound). */
	SURFACE_SPRINT(5),
	/** Plant grass-walk: normal walking (vanilla-ish range). */
	PLANT_WALK(6),
	/** Plant grass-walk: sprinting (28 blocks). */
	PLANT_SPRINT(7),
	/** Plant grass-walk: sneak/crawl (6 blocks, quieter). */
	PLANT_SNEAK(8),
	/** Snake movement (rattlesnake, anaconda) — grass-walk sound (15 blocks). */
	SNAKE(9),
	/** Custom horse gallop / step clips (replaces vanilla run sounds). */
	HORSE_RUN(10),
	/** Custom horse body-armor jingle played alongside {@link #HORSE_RUN}. */
	HORSE_ARMOR(11);

	private final byte id;

	FootstepRelayKind(int id) {
		this.id = (byte) id;
	}

	public byte id() {
		return id;
	}

	public static FootstepRelayKind fromId(byte id) {
		for (FootstepRelayKind kind : values()) {
			if (kind.id == id) {
				return kind;
			}
		}
		return STOMP;
	}
}
