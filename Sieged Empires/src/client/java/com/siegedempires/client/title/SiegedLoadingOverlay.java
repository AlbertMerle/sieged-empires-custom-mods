package com.siegedempires.client.title;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Vector4f;

/**
 * Custom startup reload overlay: dark grey background, white Mojang icon and Sieged
 * Empires logo side-by-side (same height, aspect-correct), white progress bar.
 */
public final class SiegedLoadingOverlay {
	private static final int BACKGROUND_COLOR = ARGB.color(255, 45, 45, 45);
	private static final float FADE_OUT_SECONDS = 2.0F;
	private static final float FADE_IN_SECONDS = 0.5F;
	private static final float PROGRESS_BAR_HEIGHT_RATIO = 0.8325F;
	/** Horizontal gap between the Mojang and Sieged Empires logos. */
	private static final int LOGO_GAP = 20;
	private static final int MIN_LOGO_HEIGHT = 56;
	private static final int MAX_LOGO_HEIGHT = 128;
	private static final int SCREEN_MARGIN = 32;

	private SiegedLoadingOverlay() {
	}

	public static void render(
			Minecraft minecraft,
			ReloadInstance reload,
			boolean fadeIn,
			long fadeInStart,
			long fadeOutStart,
			float currentProgress,
			ProgressSink progressSink,
			GuiGraphicsExtractor graphics,
			int mouseX,
			int mouseY,
			float partialTick) {
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		long now = Util.getMillis();

		if (fadeIn && fadeInStart == -1L) {
			progressSink.setFadeInStart(now);
			fadeInStart = now;
		}

		float fadeOutProgress = fadeOutStart > -1L ? (now - fadeOutStart) / 1000.0F : -1.0F;
		float fadeInProgress = fadeInStart > -1L ? (now - fadeInStart) / (FADE_IN_SECONDS * 1000.0F) : -1.0F;
		float logoAlpha;

		if (fadeOutProgress >= 1.0F) {
			renderUnderlay(minecraft, graphics, mouseX, mouseY, partialTick);
			int bgAlpha = Mth.ceil(255.0F * (1.0F - Mth.clamp(fadeOutProgress - 1.0F, 0.0F, 1.0F)));
			graphics.nextStratum();
			graphics.fill(0, 0, width, height, replaceAlpha(BACKGROUND_COLOR, bgAlpha));
			logoAlpha = 1.0F - Mth.clamp(fadeOutProgress - 1.0F, 0.0F, 1.0F);
		} else if (fadeIn) {
			renderUnderlay(minecraft, graphics, mouseX, mouseY, partialTick);
			int bgAlpha = Mth.ceil(255.0D * Mth.clamp(fadeInProgress, 0.0D, 1.0D));
			graphics.nextStratum();
			graphics.fill(0, 0, width, height, replaceAlpha(BACKGROUND_COLOR, bgAlpha));
			logoAlpha = Mth.clamp(fadeInProgress, 0.0F, 1.0F);
		} else {
			minecraft.gameRenderer.gameRenderState().guiRenderState.clearColorOverride =
					ARGB.setVector4fFromARGB32(new Vector4f(), BACKGROUND_COLOR);
			logoAlpha = 1.0F;
		}

		drawLogos(graphics, width, height, logoAlpha);

		float actualProgress = reload.getActualProgress();
		float smoothed = Mth.clamp(currentProgress * 0.95F + actualProgress * 0.050000012F, 0.0F, 1.0F);
		progressSink.setCurrentProgress(smoothed);

		if (fadeOutProgress < 1.0F) {
			int barY = (int) (height * PROGRESS_BAR_HEIGHT_RATIO);
			LogoPair logos = logoPair(width, height);
			int barHalfWidth = logos.totalWidth() / 2 + 16;
			drawProgressBar(
					graphics,
					width / 2 - barHalfWidth,
					barY - 5,
					width / 2 + barHalfWidth,
					barY + 5,
					smoothed,
					logoAlpha);
		}

		if (fadeOutProgress >= FADE_OUT_SECONDS) {
			minecraft.gui.setOverlay(null);
		}
	}

	private static void renderUnderlay(
			Minecraft minecraft, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		Screen screen = minecraft.gui.screen();
		if (screen != null) {
			screen.extractRenderStateWithTooltipAndSubtitles(graphics, mouseX, mouseY, partialTick);
		} else {
			minecraft.gui.hud.extractDeferredSubtitles();
		}
	}

	private static void drawLogos(GuiGraphicsExtractor graphics, int width, int height, float alpha) {
		int color = ARGB.white(alpha);
		LogoPair logos = logoPair(width, height);
		int centerY = height / 2;
		int top = centerY - logos.height() / 2;

		graphics.blit(
				RenderPipelines.GUI_TEXTURED,
				MojangLogoLayout.TEXTURE,
				logos.mojangX(),
				top,
				0.0F,
				0.0F,
				logos.mojangWidth(),
				logos.height(),
				MojangLogoLayout.TEX_WIDTH,
				MojangLogoLayout.TEX_HEIGHT,
				MojangLogoLayout.TEX_WIDTH,
				MojangLogoLayout.TEX_HEIGHT,
				color
		);

		graphics.blit(
				RenderPipelines.GUI_TEXTURED,
				SiegedLogoLayout.TEXTURE,
				logos.seX(),
				top,
				0.0F,
				0.0F,
				logos.seWidth(),
				logos.height(),
				SiegedLogoLayout.TEX_WIDTH,
				SiegedLogoLayout.TEX_HEIGHT,
				SiegedLogoLayout.TEX_WIDTH,
				SiegedLogoLayout.TEX_HEIGHT,
				color
		);
	}

	/**
	 * Shared display height; the combined logo group (Mojang left edge through SE right edge)
	 * is centered on screen so it aligns with the progress bar.
	 */
	private static LogoPair logoPair(int screenWidth, int screenHeight) {
		int center = screenWidth / 2;
		int preferredHeight = (int) (Math.min(screenWidth * 0.75D, screenHeight) * 0.22D);
		int height = Mth.clamp(preferredHeight, MIN_LOGO_HEIGHT, MAX_LOGO_HEIGHT);

		int mojangWidth = MojangLogoLayout.widthForHeight(height);
		int seWidth = SiegedLogoLayout.widthForHeight(height);
		int totalWidth = mojangWidth + LOGO_GAP + seWidth;
		int mojangX = center - totalWidth / 2;
		int seX = mojangX + mojangWidth + LOGO_GAP;

		int maxTotalWidth = Math.max(160, screenWidth - SCREEN_MARGIN * 2);
		if (totalWidth > maxTotalWidth || mojangX < SCREEN_MARGIN) {
			height = Math.max(MIN_LOGO_HEIGHT, height * maxTotalWidth / totalWidth);
			mojangWidth = MojangLogoLayout.widthForHeight(height);
			seWidth = SiegedLogoLayout.widthForHeight(height);
			totalWidth = mojangWidth + LOGO_GAP + seWidth;
			mojangX = center - totalWidth / 2;
			seX = mojangX + mojangWidth + LOGO_GAP;
		}

		return new LogoPair(mojangX, seX, mojangWidth, seWidth, height, totalWidth);
	}

	private record LogoPair(int mojangX, int seX, int mojangWidth, int seWidth, int height, int totalWidth) {
	}

	private static void drawProgressBar(
			GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, float progress, float alpha) {
		int fillWidth = Mth.ceil((x2 - x1 - 2) * progress);
		int barColor = ARGB.color(Math.round(alpha * 255.0F), 255, 255, 255);

		graphics.fill(x1 + 2, y1 + 2, x1 + fillWidth, y2 - 2, barColor);
		graphics.fill(x1 + 1, y1, x2 - 1, y1 + 1, barColor);
		graphics.fill(x1 + 1, y2, x2 - 1, y2 - 1, barColor);
		graphics.fill(x1, y1, x1 + 1, y2, barColor);
		graphics.fill(x2 - 1, y1, x2, y2, barColor);
	}

	private static int replaceAlpha(int color, int alpha) {
		return (color & 0x00FFFFFF) | (alpha << 24);
	}

	public interface ProgressSink {
		void setCurrentProgress(float progress);

		void setFadeInStart(long fadeInStart);
	}

	public interface OverlayView extends ProgressSink {
		Minecraft siegedempires$getMinecraft();

		ReloadInstance siegedempires$getReload();

		boolean siegedempires$getFadeIn();

		long siegedempires$getFadeInStart();

		long siegedempires$getFadeOutStart();

		float siegedempires$getCurrentProgress();
	}

	public static void render(OverlayView overlay, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		render(
				overlay.siegedempires$getMinecraft(),
				overlay.siegedempires$getReload(),
				overlay.siegedempires$getFadeIn(),
				overlay.siegedempires$getFadeInStart(),
				overlay.siegedempires$getFadeOutStart(),
				overlay.siegedempires$getCurrentProgress(),
				overlay,
				graphics,
				mouseX,
				mouseY,
				partialTick
		);
	}
}
