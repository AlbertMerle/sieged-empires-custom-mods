package com.distantnoise.client.sound;

import com.distantnoise.config.DistantNoiseConfig;
import com.distantnoise.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/** Tracks active grass-rustle loops per entity on the client. */
public final class GrassRustleSounds {
	private static final Map<Integer, GrassRustleSoundInstance> ACTIVE = new HashMap<>();

	private GrassRustleSounds() {
	}

	public static void start(int entityId, float startOffsetSeconds, boolean fleeing) {
		DistantNoiseConfig cfg = DistantNoiseConfig.get();
		if (!cfg.enabled || !cfg.grassRussling) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}

		Entity entity = mc.level.getEntity(entityId);
		if (entity == null) {
			return;
		}

		stop(entityId);
		GrassRustleSoundInstance instance = new GrassRustleSoundInstance(
				ModSounds.GRASS_RUSSLING.value(),
				entity,
				startOffsetSeconds,
				fleeing
		);
		ACTIVE.put(entityId, instance);
		mc.getSoundManager().play(instance);
	}

	public static void stop(int entityId) {
		GrassRustleSoundInstance instance = ACTIVE.remove(entityId);
		if (instance != null) {
			Minecraft.getInstance().getSoundManager().stop(instance);
		}
	}

	public static void clear() {
		for (int entityId : ACTIVE.keySet().toArray(Integer[]::new)) {
			stop(entityId);
		}
	}
}
