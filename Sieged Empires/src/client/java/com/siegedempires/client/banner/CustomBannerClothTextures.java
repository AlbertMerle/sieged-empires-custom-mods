package com.siegedempires.client.banner;

import com.mojang.blaze3d.platform.NativeImage;
import com.siegedempires.Siegedempires;
import com.siegedempires.banner.CustomBannerDesign;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Builds 64×64 DynamicTextures matching vanilla banner-flag UV layout so a
 * freeform {@link CustomBannerDesign} fully replaces the waving cloth texture
 * (same UV map as the {@code BannerFlagModel} 20×40×1 cube on a 64×64 sheet).
 */
public final class CustomBannerClothTextures {
	/** Matches {@link net.minecraft.client.model.object.banner.BannerFlagModel} tex size. */
	private static final int TEX = 64;
	private static final int OPAQUE_WHITE = ARGB.opaque(0xFFFFFF);

	private static final Map<String, Identifier> CACHE = new HashMap<>();
	private static int nextId;

	private CustomBannerClothTextures() {
	}

	/**
	 * Returns a registered texture id for this design (cached by encoded pixels).
	 */
	public static Identifier getOrCreate(String encodedPixels) {
		CustomBannerDesign design = CustomBannerDesign.decode(encodedPixels);
		if (design == null) {
			return null;
		}
		Identifier existing = CACHE.get(encodedPixels);
		if (existing != null) {
			return existing;
		}
		Identifier id = Identifier.fromNamespaceAndPath(
				Siegedempires.MOD_ID, "dynamic/banner_cloth_" + (nextId++));
		NativeImage image = buildImage(design);
		DynamicTexture texture = new DynamicTexture(
				() -> "SE banner cloth " + id.getPath(),
				image);
		Minecraft.getInstance().getTextureManager().register(id, texture);
		CACHE.put(encodedPixels, id);
		return id;
	}

	public static void clear() {
		TextureManager textures = Minecraft.getInstance().getTextureManager();
		Iterator<Map.Entry<String, Identifier>> it = CACHE.entrySet().iterator();
		while (it.hasNext()) {
			textures.release(it.next().getValue());
			it.remove();
		}
		nextId = 0;
	}

	/**
	 * Paint front + back faces (and thin edges) of the 20×40×1 flag cube.
	 * UV layout for {@code texOffs(0,0)} / size 20×40×1 on a 64×64 sheet:
	 * <ul>
	 *   <li>west  (0,1)–(1,41)</li>
	 *   <li>down  (1,0)–(21,1)</li>
	 *   <li>up    (21,0)–(41,1)</li>
	 *   <li>north (1,1)–(21,41) — primary face</li>
	 *   <li>east  (21,1)–(22,41)</li>
	 *   <li>south (22,1)–(42,41) — back (horizontally mirrored)</li>
	 * </ul>
	 */
	private static NativeImage buildImage(CustomBannerDesign design) {
		NativeImage image = new NativeImage(TEX, TEX, false);
		// Fully opaque sheet so entitySolid / cutout never punches holes.
		for (int y = 0; y < TEX; y++) {
			for (int x = 0; x < TEX; x++) {
				image.setPixel(x, y, OPAQUE_WHITE);
			}
		}
		for (int py = 0; py < CustomBannerDesign.HEIGHT; py++) {
			int left = design.getArgb(0, py);
			int right = design.getArgb(CustomBannerDesign.WIDTH - 1, py);
			image.setPixel(0, 1 + py, left);
			image.setPixel(21, 1 + py, right);
			for (int px = 0; px < CustomBannerDesign.WIDTH; px++) {
				int argb = design.getArgb(px, py);
				image.setPixel(1 + px, 1 + py, argb);
				image.setPixel(22 + (CustomBannerDesign.WIDTH - 1 - px), 1 + py, argb);
			}
		}
		for (int px = 0; px < CustomBannerDesign.WIDTH; px++) {
			int top = design.getArgb(px, 0);
			int bottom = design.getArgb(px, CustomBannerDesign.HEIGHT - 1);
			image.setPixel(1 + px, 0, bottom);
			image.setPixel(21 + px, 0, top);
		}
		return image;
	}
}
