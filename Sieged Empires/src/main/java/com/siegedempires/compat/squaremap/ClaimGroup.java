package com.siegedempires.compat.squaremap;

import java.util.ArrayList;
import java.util.List;

final class ClaimGroup {
	private final List<ClaimChunk> claims = new ArrayList<>();
	private final String townId;

	ClaimGroup(ClaimChunk claim, String townId) {
		this.townId = townId;
		add(claim);
	}

	boolean isTouching(ClaimChunk claim) {
		for (ClaimChunk existing : claims) {
			if (existing.isTouching(claim)) {
				return true;
			}
		}
		return false;
	}

	boolean isTouching(ClaimGroup group) {
		for (ClaimChunk claim : group.claims) {
			if (isTouching(claim)) {
				return true;
			}
		}
		return false;
	}

	void add(ClaimChunk claim) {
		claims.add(claim);
	}

	void add(ClaimGroup group) {
		claims.addAll(group.claims);
	}

	List<ClaimChunk> claims() {
		return claims;
	}

	String id() {
		if (claims.isEmpty()) {
			return "empty";
		}
		ClaimChunk first = claims.getFirst();
		return first.x() + "_" + first.z();
	}
}
