package com.shiftyocean;

import com.shiftyocean.config.ShiftyoceanConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Shiftyocean implements ModInitializer {
	public static final String MOD_ID = "shiftyocean";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ShiftyoceanConfig.load();
		ShiftyoceanConfig cfg = ShiftyoceanConfig.get();
		ServerTickEvents.END_SERVER_TICK.register(OceanCurrentHandler::onServerTick);
		LOGGER.info(
				"Shiftyocean ready (clear={} rain={} thunder={} bps, entityScale={}, yawDrift rain={} thunder={}°/s, general every {} min, gust every {} ticks, bias={}, entities={})",
				cfg.clearSpeedBps,
				cfg.rainSpeedBps,
				cfg.thunderSpeedBps,
				cfg.entitySpeedScale,
				cfg.entityYawDriftRainDegreesPerSecond,
				cfg.entityYawDriftThunderDegreesPerSecond,
				cfg.generalDirectionChangeMinutes,
				cfg.directionChangeTicks,
				cfg.generalDirectionBias,
				cfg.enabledEntities.size());
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
