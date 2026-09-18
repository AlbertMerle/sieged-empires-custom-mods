package com.siegedempires.client.banner;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.banner.BannerModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/**
 * Renders the ship-only waving flag (pole + bar + sideways cloth) with the
 * town/empire banner patterns. Geometry matches project-root {@code flag.gltf};
 * {@code flag.wave} loops with weather-scaled playback (clear 40%, rain 100%,
 * thunder 200%).
 */
public final class ShipBannerRenderer {
	/** {@code flag.wave} length in ticks at 100% playback (2.0s at 20 tps). */
	public static final float WAVE_PERIOD_TICKS = 40.0F;

	public static final float SPEED_CLEAR = 0.40F;
	public static final float SPEED_RAIN = 1.00F;
	public static final float SPEED_THUNDER = 2.00F;

	private static BannerModel poleAndBar;
	private static ShipBannerFlagModel flag;

	/** Accumulated wave age at 100%-equivalent ticks (advanced each client tick). */
	private static float waveAge;
	private static float currentSpeed = SPEED_CLEAR;

	private ShipBannerRenderer() {
	}

	/** Advance the shared wave clock; call once per client tick while in a world. */
	public static void tick(Minecraft client) {
		Level level = client.level;
		if (level == null) {
			waveAge = 0.0F;
			currentSpeed = SPEED_CLEAR;
			return;
		}
		currentSpeed = playbackSpeed(level);
		waveAge += currentSpeed;
	}

	public static float playbackSpeed(Level level) {
		if (level.isThundering()) {
			return SPEED_THUNDER;
		}
		if (level.isRaining()) {
			return SPEED_RAIN;
		}
		return SPEED_CLEAR;
	}

	/**
	 * @param partialTick frame partial tick (0..1) for smooth interpolation
	 */
	public static float wavePhase(float partialTick) {
		return Mth.frac((waveAge + partialTick * currentSpeed) / WAVE_PERIOD_TICKS);
	}

	public static void submit(
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			int lightCoords,
			float partialTick,
			DyeColor baseColor,
			BannerPatternLayers patterns,
			int outlineColor) {
		ensureModels();
		SpriteGetter sprites = Minecraft.getInstance().getAtlasManager();
		float phase = wavePhase(partialTick);
		SpriteId base = Sheets.BANNER_BASE;

		submitNodeCollector.submitModel(
				poleAndBar,
				Unit.INSTANCE,
				poseStack,
				lightCoords,
				OverlayTexture.NO_OVERLAY,
				-1,
				base,
				sprites,
				outlineColor,
				null);
		submitNodeCollector.submitModel(
				flag,
				phase,
				poseStack,
				lightCoords,
				OverlayTexture.NO_OVERLAY,
				-1,
				base,
				sprites,
				outlineColor,
				null);
		BannerRenderer.submitPatterns(
				sprites,
				poseStack,
				submitNodeCollector,
				lightCoords,
				OverlayTexture.NO_OVERLAY,
				flag,
				phase,
				true,
				baseColor,
				patterns == null ? BannerPatternLayers.EMPTY : patterns,
				null);
	}

	private static void ensureModels() {
		if (poleAndBar != null && flag != null) {
			return;
		}
		var models = Minecraft.getInstance().getEntityModels();
		poleAndBar = new BannerModel(models.bakeLayer(ModelLayers.STANDING_BANNER));
		flag = new ShipBannerFlagModel(models.bakeLayer(ModelLayers.STANDING_BANNER_FLAG));
	}
}
