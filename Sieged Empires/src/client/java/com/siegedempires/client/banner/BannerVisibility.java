package com.siegedempires.client.banner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Extends banner visibility out to the effective simulation distance (chunks × 16).
 * Vanilla block-entity banners hard-cap at 64 blocks; armor-stand mast/war flags
 * also fall short of high simulation distances (e.g. 17 chunks).
 */
public final class BannerVisibility {
	/** Vanilla {@link net.minecraft.client.renderer.blockentity.BlockEntityRenderer#getViewDistance()}. */
	private static final int VANILLA_BLOCKS = 64;

	private BannerVisibility() {
	}

	/** Max center-to-camera distance in blocks for banner rendering. */
	public static int maxDistanceBlocks() {
		int chunks = simulationDistanceChunks();
		return Math.max(VANILLA_BLOCKS, chunks * 16);
	}

	public static double maxDistanceSqr() {
		double max = maxDistanceBlocks();
		return max * max;
	}

	private static int simulationDistanceChunks() {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level != null) {
			return Math.max(1, level.getServerSimulationDistance());
		}
		if (mc.options != null) {
			return Math.max(1, mc.options.simulationDistance().get());
		}
		return 4;
	}
}
