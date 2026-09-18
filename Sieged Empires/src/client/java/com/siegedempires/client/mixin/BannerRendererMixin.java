package com.siegedempires.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.siegedempires.banner.BannerHelper;
import com.siegedempires.banner.CustomBannerDesign;
import com.siegedempires.client.banner.BannerPixelsHolder;
import com.siegedempires.client.banner.BannerVisibility;
import com.siegedempires.client.banner.CustomBannerClothTextures;
import com.siegedempires.client.banner.CustomBannerItemSubmit;
import net.minecraft.client.model.object.banner.BannerFlagModel;
import net.minecraft.client.model.object.banner.BannerModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BannerRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.level.block.BannerBlock.AttachmentType;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Extends banner view distance and, when the block entity carries freeform
 * {@code bannerPixels}, cancels vanilla pattern layers and draws the flag cloth
 * with a DynamicTexture that literally replaces the banner sheet (pole stays
 * vanilla {@link Sheets#BANNER_BASE}). Same cloth path is reused for inventory
 * / hand special-models via {@link CustomBannerItemSubmit}.
 */
@Mixin(BannerRenderer.class)
public abstract class BannerRendererMixin
		implements BlockEntityRenderer<BannerBlockEntity, BannerRenderState>, CustomBannerItemSubmit {
	@Shadow
	@Final
	private SpriteGetter sprites;

	@Shadow
	@Final
	private BannerModel standingModel;

	@Shadow
	@Final
	private BannerModel wallModel;

	@Shadow
	@Final
	private BannerFlagModel standingFlagModel;

	@Shadow
	@Final
	private BannerFlagModel wallFlagModel;

	@Override
	public int getViewDistance() {
		return BannerVisibility.maxDistanceBlocks();
	}

	@Override
	public void siegedempires$submitItemPixelCloth(
			AttachmentType type,
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			int lightCoords,
			int overlayCoords,
			int outlineColor,
			Identifier clothTexture) {
		BannerModel pole = type == AttachmentType.WALL ? this.wallModel : this.standingModel;
		BannerFlagModel flag = type == AttachmentType.WALL ? this.wallFlagModel : this.standingFlagModel;
		submitNodeCollector.submitModel(
				pole,
				Unit.INSTANCE,
				poseStack,
				lightCoords,
				overlayCoords,
				-1,
				Sheets.BANNER_BASE,
				this.sprites,
				outlineColor,
				null);
		// Flag cloth: model.renderType(texture) → entitySolid — full RGB replace.
		submitNodeCollector.submitModel(
				flag,
				0.0F,
				poseStack,
				clothTexture,
				lightCoords,
				overlayCoords,
				outlineColor,
				null);
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void siegedempires$extractBannerPixels(
			BannerBlockEntity blockEntity,
			BannerRenderState state,
			float partialTicks,
			Vec3 cameraPosition,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress,
			CallbackInfo ci) {
		String pixels = null;
		if (blockEntity instanceof BannerHelper.BannerBlockEntityAccess access) {
			pixels = access.siegedempires$getBannerPixels();
		}
		((BannerPixelsHolder) state).siegedempires$setBannerPixels(pixels);
	}

	@Inject(method = "submit", at = @At("HEAD"), cancellable = true)
	private void siegedempires$submitPixelCloth(
			BannerRenderState state,
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			CameraRenderState camera,
			CallbackInfo ci) {
		String pixels = ((BannerPixelsHolder) state).siegedempires$getBannerPixels();
		if (!CustomBannerDesign.isValidEncoded(pixels)) {
			return;
		}
		Identifier texture = CustomBannerClothTextures.getOrCreate(pixels);
		if (texture == null) {
			return;
		}

		BannerModel pole = state.attachmentType == AttachmentType.WALL ? this.wallModel : this.standingModel;
		BannerFlagModel flag = state.attachmentType == AttachmentType.WALL
				? this.wallFlagModel
				: this.standingFlagModel;

		poseStack.pushPose();
		poseStack.mulPose(state.transformation);
		// Pole / crossbar — unchanged vanilla cloth sheet.
		submitNodeCollector.submitModel(
				pole,
				Unit.INSTANCE,
				poseStack,
				state.lightCoords,
				OverlayTexture.NO_OVERLAY,
				-1,
				Sheets.BANNER_BASE,
				this.sprites,
				0,
				state.breakProgress);
		// Flag cloth: model.renderType(texture) → entitySolid — full RGB replace,
		// not a bannerPattern multiply overlay.
		submitNodeCollector.submitModel(
				flag,
				state.phase,
				poseStack,
				texture,
				state.lightCoords,
				OverlayTexture.NO_OVERLAY,
				0,
				state.breakProgress);
		poseStack.popPose();
		ci.cancel();
	}
}
