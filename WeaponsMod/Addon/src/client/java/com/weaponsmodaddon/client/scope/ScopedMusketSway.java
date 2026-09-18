package com.weaponsmodaddon.client.scope;

import net.minecraft.world.entity.LivingEntity;

/**
 * Multi-sine view drift while scoped-aiming (arm / breathing wobble).
 * Speed (sine frequencies / time scale) is fixed; amplitude grows with zoom stage.
 * Applied to real look angles by {@link ScopedMusketSwayApplier} so spyglass center and
 * bullet direction stay matched.
 */
public final class ScopedMusketSway {
	/** Degrees at 4x; multiplied by {@link #zoomIntensity()}. (~1.5× prior travel) */
	private static final float YAW_PRIMARY = 0.72F;
	private static final float YAW_SECONDARY = 0.30F;
	private static final float PITCH_PRIMARY = 0.51F;
	private static final float PITCH_SECONDARY = 0.225F;

	private ScopedMusketSway() {
	}

	public static float yawOffset(LivingEntity entity, float partialTick) {
		float t = time(entity, partialTick);
		float a = zoomIntensity();
		return (float) (Math.sin(t * 0.55) * YAW_PRIMARY + Math.sin(t * 1.15 + 1.7) * YAW_SECONDARY) * a;
	}

	public static float pitchOffset(LivingEntity entity, float partialTick) {
		float t = time(entity, partialTick);
		float a = zoomIntensity();
		return (float) (Math.sin(t * 0.72 + 0.4) * PITCH_PRIMARY + Math.sin(t * 1.05 + 2.3) * PITCH_SECONDARY) * a;
	}

	/** 2x ≈ 0.71, 4x = 1.0, 8x ≈ 1.41 — stronger sway at higher magnification. */
	private static float zoomIntensity() {
		return (float) Math.sqrt(ScopedMusketAimClient.zoomMultiplier() / 4.0F);
	}

	private static float time(LivingEntity entity, float partialTick) {
		float phase = (entity.getId() & 255) * 0.1F;
		return (entity.tickCount + partialTick) * 0.045F + phase;
	}
}
