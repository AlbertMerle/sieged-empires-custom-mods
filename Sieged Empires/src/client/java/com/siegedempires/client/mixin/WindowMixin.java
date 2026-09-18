package com.siegedempires.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.mojang.blaze3d.platform.Window;

/**
 * Keep the OS window title (and GLFW WM_CLASS source) as Sieged Empires
 * so the desktop/taskbar entry matches our branded .desktop + icon.
 */
@Mixin(Window.class)
public class WindowMixin {
	@ModifyArg(
			method = "setTitle",
			at = @At(
					value = "INVOKE",
					target = "Lorg/lwjgl/glfw/GLFW;glfwSetWindowTitle(JLjava/lang/CharSequence;)V"),
			index = 1)
	private CharSequence siegedempires$brandTitle(CharSequence title) {
		return "Sieged Empires";
	}
}
