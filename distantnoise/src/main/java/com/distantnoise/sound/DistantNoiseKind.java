package com.distantnoise.sound;

import net.minecraft.resources.Identifier;

/**
 * Which distant event to play on the client (muffle tier chosen from distance).
 */
public enum DistantNoiseKind {
	/** Musket / blunderbuss / mortar — {@code weaponsmodaddon:musket_fire}. */
	MUSKET((byte) 0),
	/** Vanilla TNT or WeaponsMod mortar shell crater (explosion boom). */
	TNT((byte) 1),
	/** Flintlock pistol — {@code weaponsmodaddon:flintlock_fire}. */
	GUNSHOT((byte) 2);

	private static final Identifier MUSKET_FIRE_ID =
			Identifier.fromNamespaceAndPath("weaponsmodaddon", "musket_fire");
	private static final Identifier FLINTLOCK_FIRE_ID =
			Identifier.fromNamespaceAndPath("weaponsmodaddon", "flintlock_fire");

	private final byte id;

	DistantNoiseKind(byte id) {
		this.id = id;
	}

	public byte id() {
		return id;
	}

	public static DistantNoiseKind byId(byte id) {
		for (DistantNoiseKind kind : values()) {
			if (kind.id == id) {
				return kind;
			}
		}
		return TNT;
	}

	/** Explosion-style (TNT / mortar impact) gets a slight volume bump. */
	public boolean isExplosion() {
		return this == TNT;
	}

	/** Loud gun relay (not crossbow / blowgun). */
	public boolean isGun() {
		return this == MUSKET || this == GUNSHOT;
	}

	/** WeaponsMod Addon bang asset for this gun kind. */
	public Identifier gunSoundId() {
		return switch (this) {
			case MUSKET -> MUSKET_FIRE_ID;
			case GUNSHOT -> FLINTLOCK_FIRE_ID;
			case TNT -> throw new IllegalStateException("not a gun kind");
		};
	}
}
