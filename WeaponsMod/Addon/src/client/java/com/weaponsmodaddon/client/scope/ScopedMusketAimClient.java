package com.weaponsmodaddon.client.scope;

import ckathode.weaponmod.item.RangedComponent;
import com.mojang.blaze3d.platform.InputConstants;
import com.weaponsmodaddon.ModItemTags;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * Client zoom state for scoped muskets: 2x / 4x / 8x via arrow keys while aiming.
 */
public final class ScopedMusketAimClient {

	/** Zoom stages matching on-screen labels. Default index 1 = 4x. */
	public static final float[] ZOOM_STAGES = {2.0F, 4.0F, 8.0F};
	public static final String[] ZOOM_LABELS = {"2x", "4x", "8x"};

	private static int zoomIndex = 1;
	private static boolean upWasDown;
	private static boolean downWasDown;

	private ScopedMusketAimClient() {
	}

	public static int zoomIndex() {
		return zoomIndex;
	}

	public static float zoomMultiplier() {
		return ZOOM_STAGES[zoomIndex];
	}

	public static String zoomLabel() {
		return ZOOM_LABELS[zoomIndex];
	}

	/** FOV scale = 1 / magnification (2x → 0.5, 4x → 0.25, 8x → 0.125). */
	public static float fovModifier() {
		return 1.0F / zoomMultiplier();
	}

	public static boolean isScopedStack(ItemStack stack) {
		return !stack.isEmpty() && stack.is(ModItemTags.SCOPED_MUSKETS);
	}

	/** Aiming: using a scoped musket that is ready to fire (not reloading). */
	public static boolean isAimingScoped(LivingEntity entity) {
		if (entity == null || !entity.isUsingItem()) {
			return false;
		}
		ItemStack stack = entity.getUseItem();
		return isScopedStack(stack) && RangedComponent.isReadyToFire(stack);
	}

	public static void tick(Minecraft client) {
		if (client.player == null || client.gui.screen() != null) {
			upWasDown = false;
			downWasDown = false;
			return;
		}
		if (!isAimingScoped(client.player)) {
			upWasDown = false;
			downWasDown = false;
			return;
		}

		var window = client.getWindow();
		boolean up = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_UP);
		boolean down = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_DOWN);

		if (up && !upWasDown) {
			zoomIndex = Math.min(ZOOM_STAGES.length - 1, zoomIndex + 1);
		}
		if (down && !downWasDown) {
			zoomIndex = Math.max(0, zoomIndex - 1);
		}

		upWasDown = up;
		downWasDown = down;
	}
}
