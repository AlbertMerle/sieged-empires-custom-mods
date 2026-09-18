package com.siegedempires.client.banner;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * Sideways ship flag cloth (from {@code flag.gltf}): same mesh as the vanilla
 * standing banner flag, rotated −90° on Z so it streams sideways from the mast,
 * with {@code flag.wave} — a 2s loop that sways {@code yRot} by ±5°.
 */
public class ShipBannerFlagModel extends Model<Float> {
	/** Peak sway from {@code flag.wave} mid keyframe (degrees). */
	private static final float WAVE_Y_DEGREES = 5.0F;

	private final ModelPart flag;

	public ShipBannerFlagModel(ModelPart root) {
		super(root, RenderTypes::entitySolid);
		this.flag = root.getChild("flag");
	}

	/**
	 * @param phase 0..1 over one {@code flag.wave} cycle (2 seconds)
	 */
	@Override
	public void setupAnim(Float phase) {
		super.setupAnim(phase);
		this.flag.zRot = -((float) Math.PI / 2.0F);
		float p = phase == null ? 0.0F : phase;
		this.flag.yRot = (float) Math.toRadians(-WAVE_Y_DEGREES) * Mth.sin((float) Math.PI * p);
	}
}
