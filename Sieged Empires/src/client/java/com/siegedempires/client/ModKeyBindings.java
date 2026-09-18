package com.siegedempires.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.siegedempires.Siegedempires;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class ModKeyBindings {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Siegedempires.id("general"));

	public static final KeyMapping OPEN_GUI = KeyMappingHelper.registerKeyMapping(
			new KeyMapping(
					"key.siegedempires.open_gui",
					InputConstants.Type.KEYSYM,
					GLFW.GLFW_KEY_J,
					CATEGORY
			)
	);

	private ModKeyBindings() {
	}

	/**
	 * Forces class initialization to register key bindings during mod init phase.
	 * Must be called before GameOptions is initialized by Minecraft.
	 */
	public static void initialize() {
		// Class initialization happens automatically when this method is called.
		// The static fields above will be initialized, registering the key bindings.
	}
}
