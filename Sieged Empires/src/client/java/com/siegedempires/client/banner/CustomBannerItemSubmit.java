package com.siegedempires.client.banner;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.BannerBlock.AttachmentType;

/**
 * Implemented by {@link net.minecraft.client.renderer.blockentity.BannerRenderer}
 * via mixin so item special-models can draw painted cloth the same way as world banners.
 */
public interface CustomBannerItemSubmit {
	void siegedempires$submitItemPixelCloth(
			AttachmentType type,
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			int lightCoords,
			int overlayCoords,
			int outlineColor,
			Identifier clothTexture);
}
