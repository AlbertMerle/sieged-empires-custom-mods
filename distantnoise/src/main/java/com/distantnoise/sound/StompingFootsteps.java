package com.distantnoise.sound;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.Optional;

/** Stomp sound profiles for config-listed entities. */
public final class StompingFootsteps {
	private static final Identifier BEAR_STEP = Identifier.fromNamespaceAndPath("distantnoise", "bear_step");

	private StompingFootsteps() {
	}

	public static Optional<FootstepProfile> forEntity(LivingEntity entity, BlockState steppedOn) {
		if (entity.isBaby()) {
			return Optional.empty();
		}
		return Optional.of(new FootstepProfile(BEAR_STEP, 1.0f, 1.0f));
	}
}
