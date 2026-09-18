package com.siegedempires.client;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.siegedempires.Siegedempires;

import net.minecraft.client.Minecraft;

/**
 * Applies the Sieged Empires logo as the Minecraft window / taskbar icon (X11/Windows),
 * maximizes the window on launch, and requests OS focus so the game opens above other apps.
 */
public final class WindowBranding {
	private static final String[] ICON_PATHS = {
			"/assets/siegedempires/icons/icon_16x16.png",
			"/assets/siegedempires/icons/icon_32x32.png",
			"/assets/siegedempires/icons/icon_48x48.png",
			"/assets/siegedempires/icons/icon_128x128.png",
			"/assets/siegedempires/icons/icon_256x256.png",
	};

	private WindowBranding() {
	}

	public static void apply() {
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return;
		}
		long handle = client.getWindow().handle();
		if (handle == 0L) {
			return;
		}

		client.getWindow().setTitle("Sieged Empires");

		RenderSystem.assertOnRenderThread();
		List<ByteBuffer> allocated = new ArrayList<>();
		try (MemoryStack stack = MemoryStack.stackPush()) {
			List<NativeImage> images = new ArrayList<>();
			for (String path : ICON_PATHS) {
				try (InputStream in = WindowBranding.class.getResourceAsStream(path)) {
					if (in == null) {
						continue;
					}
					images.add(NativeImage.read(in));
				} catch (Exception e) {
					Siegedempires.LOGGER.warn("Failed loading window icon {}", path, e);
				}
			}
			if (images.isEmpty()) {
				Siegedempires.LOGGER.warn("No window icons loaded; skipping icon apply");
			} else {
				GLFWImage.Buffer icons = GLFWImage.malloc(images.size(), stack);
				for (int i = 0; i < images.size(); i++) {
					NativeImage image = images.get(i);
					ByteBuffer pixels = MemoryUtil.memAlloc(image.getWidth() * image.getHeight() * 4);
					allocated.add(pixels);
					pixels.asIntBuffer().put(image.getPixelsABGR());
					icons.position(i);
					icons.width(image.getWidth());
					icons.height(image.getHeight());
					icons.pixels(pixels);
				}
				GLFW.glfwSetWindowIcon(handle, icons.position(0));
				for (NativeImage image : images) {
					image.close();
				}
			}
		} catch (Exception e) {
			Siegedempires.LOGGER.warn("Failed applying Sieged Empires window icon", e);
		} finally {
			allocated.forEach(MemoryUtil::memFree);
		}

		applyWindowPlacement(handle);
	}

	/**
	 * Maximize (windowed) and raise/focus the game window. Safe to call repeatedly while starting.
	 */
	public static void applyWindowPlacement(long handle) {
		if (handle == 0L) {
			return;
		}
		RenderSystem.assertOnRenderThread();
		GLFW.glfwShowWindow(handle);
		if (GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_FALSE) {
			GLFW.glfwMaximizeWindow(handle);
		}
		GLFW.glfwFocusWindow(handle);
		GLFW.glfwRequestWindowAttention(handle);
	}

	public static void applyWindowPlacement(Minecraft client) {
		if (client == null || client.getWindow() == null) {
			return;
		}
		applyWindowPlacement(client.getWindow().handle());
	}
}
