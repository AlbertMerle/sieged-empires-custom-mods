package com.distantnoise.sound;

import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

import java.util.Set;

/** Which living entities get distance-scaled vocal relay. */
public final class VocalAnimals {
	/**
	 * Wing/buzz loops — not “vocals”. Keep vanilla attenuation (do not relay far).
	 * Soft-compat ids: Alex's Mobs hummingbird loop; vanilla bee loops.
	 * Hummingbird idle (tweet) stays relayed.
	 */
	private static final Set<Identifier> BUZZ_LOOP_SOUNDS = Set.of(
			Identifier.parse("minecraft:entity.bee.loop"),
			Identifier.parse("minecraft:entity.bee.loop_aggressive"),
			Identifier.parse("alexsmobs:hummingbird_loop")
	);

	private VocalAnimals() {
	}

	/** All mob vocals — passive, neutral, and hostile (vanilla + Alex's, etc.). */
	public static boolean isVocalAnimal(LivingEntity entity) {
		return !(entity instanceof Player || entity instanceof ArmorStand);
	}

	/** True for wing-buzz loops that must not use distant vocal relay. */
	public static boolean isBuzzLoop(SoundEvent event) {
		return event != null && BUZZ_LOOP_SOUNDS.contains(event.location());
	}

	/** Whether this sound should be cancelled and distance-relayed as a vocal. */
	public static boolean shouldRelayVocal(LivingEntity entity, SoundEvent event) {
		return isVocalAnimal(entity) && !isBuzzLoop(event);
	}
}
