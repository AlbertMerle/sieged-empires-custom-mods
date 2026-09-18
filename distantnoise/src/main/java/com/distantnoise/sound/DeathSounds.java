package com.distantnoise.sound;

import com.distantnoise.Distantnoise;
import com.distantnoise.config.DistantNoiseConfig;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Custom death clip ({@code distantnoise:death}) for players and mobs. */
public final class DeathSounds {
	public static final Identifier DEATH_ID = Distantnoise.id("death");

	private DeathSounds() {
	}

	public static boolean enabled(DistantNoiseConfig cfg) {
		return cfg.enabled && cfg.deathSoundsEnabled;
	}

	public static boolean isCustomDeath(SoundEvent event) {
		return event != null && DEATH_ID.equals(event.location());
	}

	public static SoundEvent deathEvent() {
		return ModSounds.DEATH.value();
	}

	public static Identifier deathSoundId() {
		return DEATH_ID;
	}
}
