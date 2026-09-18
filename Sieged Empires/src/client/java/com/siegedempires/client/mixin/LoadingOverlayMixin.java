package com.siegedempires.client.mixin;

import com.siegedempires.client.title.MojangLogoLayout;
import com.siegedempires.client.title.SiegedLoadingOverlay;
import com.siegedempires.client.title.SiegedLogoLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the red Mojang startup reload screen with a dark grey dual-logo layout.
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin implements SiegedLoadingOverlay.OverlayView {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	@Final
	private ReloadInstance reload;

	@Shadow
	@Final
	private boolean fadeIn;

	@Shadow
	private float currentProgress;

	@Shadow
	private long fadeOutStart;

	@Shadow
	private long fadeInStart;

	@Inject(method = "registerTextures", at = @At("TAIL"))
	private static void siegedempires$registerLogo(TextureManager textureManager, CallbackInfo ci) {
		MojangLogoLayout.registerStartupTextures(textureManager);
		SiegedLogoLayout.registerStartupTextures(textureManager);
	}

	@Inject(
			method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private void siegedempires$customLoadingScreen(
			GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		SiegedLoadingOverlay.render(this, graphics, mouseX, mouseY, partialTick);
		ci.cancel();
	}

	@Override
	public Minecraft siegedempires$getMinecraft() {
		return this.minecraft;
	}

	@Override
	public ReloadInstance siegedempires$getReload() {
		return this.reload;
	}

	@Override
	public boolean siegedempires$getFadeIn() {
		return this.fadeIn;
	}

	@Override
	public long siegedempires$getFadeInStart() {
		return this.fadeInStart;
	}

	@Override
	public long siegedempires$getFadeOutStart() {
		return this.fadeOutStart;
	}

	@Override
	public float siegedempires$getCurrentProgress() {
		return this.currentProgress;
	}

	@Override
	public void setCurrentProgress(float progress) {
		this.currentProgress = progress;
	}

	@Override
	public void setFadeInStart(long fadeInStart) {
		this.fadeInStart = fadeInStart;
	}
}
