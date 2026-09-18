package com.siegedempires.client.mixin;

import com.siegedempires.client.performance.PerformanceSettingsState;
import com.siegedempires.client.title.SiegedLogoLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the vanilla Minecraft title logo with the Sieged Empires logo
 * (aspect-preserving, GUI-scale-aware) and skips the "Java Edition" badge.
 * Hidden entirely during the first-launch performance picker.
 */
@Mixin(LogoRenderer.class)
public class LogoRendererMixin {
	@Inject(
			method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IFI)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private void siegedempires$drawCustomLogo(
			GuiGraphicsExtractor graphics, int width, float alpha, int heightOffset, CallbackInfo ci) {
		if (PerformanceSettingsState.needsFirstTimeChoice()) {
			ci.cancel();
			return;
		}

		LogoRenderer self = (LogoRenderer) (Object) this;
		int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		int menuTop = SiegedLogoLayout.titleMenuTop(screenHeight);
		SiegedLogoLayout.TitlePlacement logo = SiegedLogoLayout.titlePlacement(
				width, heightOffset, menuTop);

		float effectiveAlpha = self.keepLogoThroughFade() ? 1.0F : alpha;
		int color = ARGB.white(effectiveAlpha);

		graphics.blit(
				RenderPipelines.GUI_TEXTURED,
				SiegedLogoLayout.TEXTURE,
				logo.x(),
				logo.y(),
				0.0F,
				0.0F,
				logo.width(),
				logo.height(),
				SiegedLogoLayout.TEX_WIDTH,
				SiegedLogoLayout.TEX_HEIGHT,
				SiegedLogoLayout.TEX_WIDTH,
				SiegedLogoLayout.TEX_HEIGHT,
				color
		);
		ci.cancel();
	}
}
