package com.distantnoise;

import com.distantnoise.config.DistantNoiseConfig;
import com.distantnoise.network.ModNetworking;
import com.distantnoise.sound.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Distantnoise implements ModInitializer {
	public static final String MOD_ID = "distantnoise";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModSounds.initialize();
		DistantNoiseConfig.load();
		ModNetworking.register();

		boolean weaponmod = FabricLoader.getInstance().isModLoaded("weaponmod");
		boolean alexsmobs = FabricLoader.getInstance().isModLoaded("alexsmobs");
		boolean spr = FabricLoader.getInstance().isModLoaded("sound_physics_remastered");
		LOGGER.info(
				"Distantnoise ready (radius={}, footsteps={}, weaponmod={}, alexsmobs={}, sound_physics_remastered={})",
				DistantNoiseConfig.get().radius,
				DistantNoiseConfig.get().footstepsEnabled,
				weaponmod,
				alexsmobs,
				spr
		);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
