package com.siegedmusic;

import com.siegedmusic.sound.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SiegedMusic implements ModInitializer {
	public static final String MOD_ID = "sieged-music";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModSounds.initialize();
		boolean siegedEmpires = FabricLoader.getInstance().isModLoaded("siegedempires");
		LOGGER.info(
				"Sieged Music ready (ambient + cave Y<40 + war; siegedempires={})",
				siegedEmpires
		);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
