package com.siegedempires.client.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.SplashRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the yellow rotating splash texts next to the title logo.
 */
@Mixin(SplashRenderer.class)
public class SplashRendererMixin {
	@Inject(
			method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/client/gui/Font;F)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private void siegedempires$hideSplash(
			GuiGraphicsExtractor graphics, int screenWidth, Font font, float alpha, CallbackInfo ci) {
		ci.cancel();
	}
}
