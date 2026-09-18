package com.siegedempires.client.performance;

import net.minecraft.network.chat.Component;

/**
 * Performance preset tiers from Potato (minimum) through Ultra.
 */
public enum PerformanceLevel {
	/** Voxy off; geometry buffer override 0 MB. */
	POTATO("potato", true, 0),
	LOW("low", true, 328),
	MEDIUM("medium", true, 512),
	HIGH("high", true, 768),
	ULTRA("ultra", true, 768);

	/** Pack default when no performance tier has been chosen yet. */
	public static final int DEFAULT_VOXY_GEOMETRY_BUFFER_MB = 328;

	private final String id;
	private final boolean available;
	private final int voxyGeometryBufferMb;

	PerformanceLevel(String id, boolean available, int voxyGeometryBufferMb) {
		this.id = id;
		this.available = available;
		this.voxyGeometryBufferMb = voxyGeometryBufferMb;
	}

	public String id() {
		return this.id;
	}

	public boolean isAvailable() {
		return this.available;
	}

	/** Megabytes for {@code voxy.geometryBufferSizeOverrideMB}. */
	public int voxyGeometryBufferMb() {
		return this.voxyGeometryBufferMb;
	}

	public Component label() {
		return Component.translatable("menu.siegedempires.performance.level." + this.id);
	}

	public static PerformanceLevel byId(String id) {
		if (id == null || id.isBlank()) {
			return MEDIUM;
		}
		for (PerformanceLevel level : values()) {
			if (level.id.equalsIgnoreCase(id)) {
				return level;
			}
		}
		return MEDIUM;
	}
}
