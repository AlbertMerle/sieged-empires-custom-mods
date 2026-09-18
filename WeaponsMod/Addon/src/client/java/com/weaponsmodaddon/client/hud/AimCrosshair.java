package com.weaponsmodaddon.client.hud;

import com.weaponsmodaddon.WeaponsModAddon;
import com.weaponsmodaddon.client.scope.ScopedMusketAimClient;
import com.weaponsmodaddon.gun.GunAimState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * Custom aim crosshair ({@code textures/gui/crosshair.png}) using vanilla
 * {@link RenderPipelines#CROSSHAIR} invert blend so it contrasts the scene.
 * <p>
 * Invert blend ignores texture alpha for color
 * ({@code ONE_MINUS_DST_COLOR}/{@code ONE_MINUS_SRC_COLOR}): only RGB matters.
 * Visible pixels must be white; transparent pixels must be {@code (0,0,0,0)}.
 * <p>
 * The PNG’s aim point is the center of the 2×2 transparent hole at texels
 * (7,7)–(8,8), i.e. texture coordinate {@code (8, 8)}. That point is placed on
 * the same screen aim point as the vanilla 15×15 crosshair sprite.
 */
public final class AimCrosshair {
	public static final Identifier TEXTURE = WeaponsModAddon.id("textures/gui/crosshair.png");
	public static final int TEX_SIZE = 16;
	/** Center of the 2×2 transparent hole (between texels 7 and 8). */
	public static final int HOLE_CENTER = 8;

	private AimCrosshair() {
	}

	public static boolean shouldReplace(LocalPlayer player) {
		if (player == null || !GunAimState.shouldLockBodyToLook(player)) {
			return false;
		}
		// Scoped muskets use spyglass FP overlay, not the gun aim reticle.
		return !ScopedMusketAimClient.isAimingScoped(player);
	}

	/**
	 * @param vanillaX vanilla crosshair blit X (already centered for {@code vanillaW})
	 * @param vanillaY vanilla crosshair blit Y
	 * @param vanillaW vanilla sprite width (15)
	 * @param vanillaH vanilla sprite height (15)
	 */
	public static void blit(GuiGraphicsExtractor graphics, int vanillaX, int vanillaY, int vanillaW, int vanillaH) {
		// Match vanilla’s aim point (center of the 15×15 sprite), including the .5 for odd sizes.
		float aimX = vanillaX + vanillaW / 2.0F;
		float aimY = vanillaY + vanillaH / 2.0F;
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(aimX, aimY);
		graphics.blit(
				RenderPipelines.CROSSHAIR,
				TEXTURE,
				-HOLE_CENTER,
				-HOLE_CENTER,
				0.0F,
				0.0F,
				TEX_SIZE,
				TEX_SIZE,
				TEX_SIZE,
				TEX_SIZE);
		pose.popMatrix();
	}

	public static boolean shouldReplaceForClient() {
		return shouldReplace(Minecraft.getInstance().player);
	}
}
