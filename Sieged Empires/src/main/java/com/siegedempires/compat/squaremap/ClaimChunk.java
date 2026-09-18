package com.siegedempires.compat.squaremap;

/**
 * A single claimed chunk on the map, keyed by town rather than player.
 */
record ClaimChunk(int x, int z, String townId) {
	boolean isTouching(ClaimChunk other) {
		if (!townId.equals(other.townId)) {
			return false;
		}
		if (other.x == x && other.z == z - 1) {
			return true;
		}
		if (other.x == x && other.z == z + 1) {
			return true;
		}
		if (other.x == x - 1 && other.z == z) {
			return true;
		}
		return other.x == x + 1 && other.z == z;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ClaimChunk other)) {
			return false;
		}
		return x == other.x && z == other.z;
	}

	@Override
	public int hashCode() {
		return 31 * x + z;
	}
}
