package com.siegedmusic.client.music;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

/**
 * Client-side check for an active Sieged Empires war banner within range.
 * War banners are armor stands tagged {@code siegedempires_war_banner} while an invasion is active.
 */
public final class WarBannerProximity {
	public static final String WAR_BANNER_TAG = "siegedempires_war_banner";
	public static final double RADIUS_BLOCKS = 80.0;

	private WarBannerProximity() {
	}

	public static boolean isNearActiveWarBanner(Minecraft minecraft) {
		if (minecraft.player == null || minecraft.level == null) {
			return false;
		}
		double radius = RADIUS_BLOCKS;
		AABB box = minecraft.player.getBoundingBox().inflate(radius);
		double radiusSq = radius * radius;
		for (Entity entity : minecraft.level.getEntities(minecraft.player, box)) {
			if (entity instanceof ArmorStand && entity.entityTags().contains(WAR_BANNER_TAG)) {
				if (entity.distanceToSqr(minecraft.player) <= radiusSq) {
					return true;
				}
			}
		}
		return false;
	}
}
