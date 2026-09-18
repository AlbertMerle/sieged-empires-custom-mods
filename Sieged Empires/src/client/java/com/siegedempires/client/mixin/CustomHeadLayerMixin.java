package com.siegedempires.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.siegedempires.client.banner.ShipBannerRenderData;
import com.siegedempires.client.banner.ShipBannerRenderer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shippy Ships mast flags: replace the vanilla upright head-banner with the
 * sideways waving flag model ({@code flag.wave} looped while rendered).
 */
@Mixin(CustomHeadLayer.class)
public abstract class CustomHeadLayerMixin<S extends LivingEntityRenderState, M extends EntityModel<S> & HeadedModel>
		extends RenderLayer<S, M> {
	@Shadow
	@org.spongepowered.asm.mixin.Final
	private CustomHeadLayer.Transforms transforms;

	private CustomHeadLayerMixin() {
		super(null);
		throw new AssertionError();
	}

	@Inject(
			method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;FF)V",
			at = @At("HEAD"),
			cancellable = true)
	private void siegedempires$submitShipFlag(
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			int lightCoords,
			S state,
			float yRot,
			float xRot,
			CallbackInfo ci) {
		if (!(state instanceof ArmorStandRenderState armorState)) {
			return;
		}
		ShipBannerRenderData data = ((ShipBannerRenderData.Holder) armorState).siegedempires$getShipBanner();
		if (data == null) {
			return;
		}

		poseStack.pushPose();
		poseStack.scale(this.transforms.horizontalScale(), 1.0F, this.transforms.horizontalScale());
		M parentModel = this.getParentModel();
		parentModel.root().translateAndRotate(poseStack);
		parentModel.translateToHead(poseStack);
		CustomHeadLayer.translateToHead(poseStack, this.transforms);
		float partialTick = net.minecraft.util.Mth.frac(state.ageInTicks);
		ShipBannerRenderer.submit(
				poseStack,
				submitNodeCollector,
				lightCoords,
				partialTick,
				data.baseColor(),
				data.patterns(),
				state.outlineColor);
		poseStack.popPose();
		ci.cancel();
	}
}
