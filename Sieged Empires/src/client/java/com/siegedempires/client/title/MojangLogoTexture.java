package com.siegedempires.client.title;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/** Eager-loads the custom Mojang icon during startup (before mod packs attach). */
public final class MojangLogoTexture extends ReloadableTexture {
	private static final String CLASSPATH = "/assets/siegedempires/textures/gui/mojang_icon.png";
	private static final TextureMetadataSection METADATA =
			new TextureMetadataSection(true, true, MipmapStrategy.MEAN, 0.0F);

	public MojangLogoTexture() {
		super(MojangLogoLayout.TEXTURE);
	}

	@Override
	public TextureContents loadContents(ResourceManager resourceManager) throws IOException {
		Optional<Resource> resource = resourceManager.getResource(MojangLogoLayout.TEXTURE);
		if (resource.isPresent()) {
			try (InputStream in = resource.get().open()) {
				return new TextureContents(NativeImage.read(in), METADATA);
			}
		}

		try (InputStream in = MojangLogoTexture.class.getResourceAsStream(CLASSPATH)) {
			if (in == null) {
				throw new IOException("Missing classpath asset: " + CLASSPATH);
			}
			return new TextureContents(NativeImage.read(in), METADATA);
		}
	}
}
