package com.siegedempires.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.siegedempires.banner.BannerHelper;
import com.siegedempires.banner.BoatBannerManager;
import com.siegedempires.client.banner.ShipBannerRenderData;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * War banners ride the armor-stand head slot, which renders ~2 blocks above the
 * entity feet/hitbox. Shift the model (and nametag) down so the banner sits flush
 * with placement and the hittable AABB.
 * <p>
 * Shippy Ships mast flags capture design data here so {@link CustomHeadLayerMixin}
 * can render the waving {@code flag.gltf} model instead of the vanilla banner.
 */
@Mixin(ArmorStandRenderer.class)
public class ArmorStandRendererMixin {
	private static final double WAR_BANNER_RENDER_OFFSET_Y = -2.0;

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void siegedempires$captureShipBannerAndOffsetWarNameTag(
			ArmorStand entity, ArmorStandRenderState state, float partialTicks, CallbackInfo ci) {
		ShipBannerRenderData.Holder holder = (ShipBannerRenderData.Holder) state;
		if (BoatBannerManager.isShipBoatBanner(entity)) {
			holder.siegedempires$setShipBanner(ShipBannerRenderData.fromHeadStack(state.headEquipment));
		} else {
			holder.siegedempires$setShipBanner(null);
		}

		if (!BannerHelper.isWarBanner(state.headEquipment) || state.nameTagAttachment == null) {
			return;
		}
		state.nameTagAttachment = state.nameTagAttachment.add(0.0, WAR_BANNER_RENDER_OFFSET_Y, 0.0);
	}

	@Inject(method = "setupRotations", at = @At("TAIL"))
	private void siegedempires$offsetWarBannerModel(
			ArmorStandRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		if (!BannerHelper.isWarBanner(state.headEquipment)) {
			return;
		}
		poseStack.translate(0.0F, (float) WAR_BANNER_RENDER_OFFSET_Y, 0.0F);
	}
}
