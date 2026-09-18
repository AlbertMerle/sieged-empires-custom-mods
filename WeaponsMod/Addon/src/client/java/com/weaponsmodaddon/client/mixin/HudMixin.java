package com.weaponsmodaddon.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.weaponsmodaddon.client.hud.AimCrosshair;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * While aiming a gun/spear, draw {@link AimCrosshair} with invert blend instead of the
 * vanilla hud/crosshair sprite.
 */
@Mixin(Hud.class)
public class HudMixin {

	@WrapOperation(
			method = "extractCrosshair",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
					ordinal = 0
			)
	)
	private void weaponsmodaddon$aimCrosshair(
			GuiGraphicsExtractor graphics,
			RenderPipeline pipeline,
			Identifier sprite,
			int x,
			int y,
			int width,
			int height,
			Operation<Void> original
	) {
		if (AimCrosshair.shouldReplaceForClient()) {
			AimCrosshair.blit(graphics, x, y, width, height);
		} else {
			original.call(graphics, pipeline, sprite, x, y, width, height);
		}
	}
}
