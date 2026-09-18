package com.croplite.item;

import com.croplite.CropLite;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.consume_effects.ConsumeEffect;

public final class ModConsumeEffects {
	private ModConsumeEffects() {
	}

	public static final ConsumeEffect.Type<OptionalModEffectConsumeEffect> OPTIONAL_MOD_EFFECT =
			Registry.register(
					BuiltInRegistries.CONSUME_EFFECT_TYPE,
					CropLite.id("optional_mod_effect"),
					new ConsumeEffect.Type<>(OptionalModEffectConsumeEffect.CODEC, OptionalModEffectConsumeEffect.STREAM_CODEC));

	public static final ConsumeEffect.Type<GarlicEatConsumeEffect> GARLIC_EAT =
			Registry.register(
					BuiltInRegistries.CONSUME_EFFECT_TYPE,
					CropLite.id("garlic_eat"),
					new ConsumeEffect.Type<>(GarlicEatConsumeEffect.CODEC, GarlicEatConsumeEffect.STREAM_CODEC));

	public static void initialize() {
		// Registration happens via static field init; touch the class to ensure load order.
	}
}
