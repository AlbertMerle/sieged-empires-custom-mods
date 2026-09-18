package com.tertonbiome;

import com.tertonbiome.command.TerrabiomeCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Terratonicbiomes implements ModInitializer {
	public static final String MOD_ID = "terratonicbiomes";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		TerrabiomeCommand.register();
		TerratonicbiomesConfig.load();
		TerratonicbiomesConfig c = TerratonicbiomesConfig.get();

		boolean tectonic = FabricLoader.getInstance().isModLoaded("tectonic");
		boolean terralith = FabricLoader.getInstance().isModLoaded("terralith");
		boolean litho = FabricLoader.getInstance().isModLoaded("lithostitched");
		if (!tectonic) {
			LOGGER.warn("Tectonic is NOT loaded — land shape will not be Tectonic. Install tectonic for terrain.");
		}
		if (!terralith) {
			LOGGER.warn("Terralith is NOT loaded — zone whitelist Terralith biomes will be skipped when missing.");
		}
		if (!litho) {
			LOGGER.warn("Lithostitched is NOT loaded — Terralith/Tectonic usually require it.");
		}

		FabricLoader.getInstance().getModContainer(MOD_ID).ifPresent(container -> {
			boolean registered = ResourceLoader.registerBuiltinPack(
				id("biomes"),
				container,
				Component.literal("Terrabiome (biome placement)"),
				PackActivationType.ALWAYS_ENABLED
			);
			if (registered) {
				LOGGER.info(
					"Registered datapack '{}' (ALWAYS_ENABLED). Stack order: terralith < {} < {}",
					DatapackStack.TERRABIOME_PACK,
					DatapackStack.TECTONIC_PACK,
					DatapackStack.TERRABIOME_PACK
				);
			} else {
				LOGGER.error("Failed to register builtin datapack '{}'", DatapackStack.TERRABIOME_PACK);
			}
		});

		LOGGER.info(
			"Terrabiome latitude ON={} world-size={} zone={} (Freezing|Cold|Temperate|Warm|Hot) Z[{}|{}|{}|{}|{}+] wobble=±{} scale={} blend={} plains↔meadow@{} land>ocean={} skylands={} hotMix={} coldMtns@{}→hills/stony={} banSwampN={} enforceZone={} absorbLand={} minLand={}chunks surfaceY=[{}..{}] enabled={} allowed-structures={} deps[tectonic={} terralith={} lithostitched={}]",
			c.enabled,
			c.worldSize,
			c.zoneWidth(),
			c.zFarNorth(),
			c.zNorth(),
			c.zSouth(),
			c.zFarSouth(),
			c.zHotEdge(),
			c.boundaryNoiseAmplitude,
			c.boundaryNoiseScale,
			c.boundaryBlendWidth,
			c.plainsMeadowSwapZ(),
			c.preferLandOverOcean,
			c.disableSkylands ? "OFF" : "ON",
			c.hotDesertJungleMix,
			c.coldMountainCutoffZ,
			c.overrideColdMountains,
			c.banSwampsNorthOfWarm,
			c.enforceZoneBiomes,
			c.absorbSmallLandBiomes,
			c.minLandBiomeSize,
			SurfaceBiomeBounds.minY(),
			SurfaceBiomeBounds.maxY(),
			SurfaceBiomeBounds.isEnabled(),
			c.allowedStructures,
			tectonic,
			terralith,
			litho
		);

		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			try {
				AllowedStructures.refresh(server.registryAccess());
			} catch (Exception e) {
				LOGGER.debug("Structure registries not ready at SERVER_STARTING; will refresh at worldgen init", e);
			}
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> AllowedStructures.refresh(server.registryAccess()));
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
			if (success) {
				AllowedStructures.refresh(server.registryAccess());
			}
		});
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
