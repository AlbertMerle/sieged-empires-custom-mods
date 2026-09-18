package com.siegedempires.client.mixin;

import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.special.BannerSpecialRenderer;
import net.minecraft.world.level.block.BannerBlock.AttachmentType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BannerSpecialRenderer.class)
public interface BannerSpecialRendererAccessor {
	@Accessor("bannerRenderer")
	BannerRenderer siegedempires$getBannerRenderer();

	@Accessor("attachment")
	AttachmentType siegedempires$getAttachment();
}
