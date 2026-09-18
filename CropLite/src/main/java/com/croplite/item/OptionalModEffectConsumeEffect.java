package com.croplite.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.level.Level;

/**
 * Applies a status effect when the effect id is registered (e.g. Tough as Nails or
 * Homeostatic thirst). No-ops safely when the other mod is absent.
 */
public record OptionalModEffectConsumeEffect(Identifier effectId, int duration, int amplifier) implements ConsumeEffect {
	public static final MapCodec<OptionalModEffectConsumeEffect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Identifier.CODEC.fieldOf("effect").forGetter(OptionalModEffectConsumeEffect::effectId),
			Codec.INT.fieldOf("duration").forGetter(OptionalModEffectConsumeEffect::duration),
			Codec.INT.optionalFieldOf("amplifier", 0).forGetter(OptionalModEffectConsumeEffect::amplifier)
	).apply(instance, OptionalModEffectConsumeEffect::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, OptionalModEffectConsumeEffect> STREAM_CODEC = StreamCodec.composite(
			Identifier.STREAM_CODEC, OptionalModEffectConsumeEffect::effectId,
			ByteBufCodecs.VAR_INT, OptionalModEffectConsumeEffect::duration,
			ByteBufCodecs.VAR_INT, OptionalModEffectConsumeEffect::amplifier,
			OptionalModEffectConsumeEffect::new);

	/** Tough as Nails dehydration (thirst) effect — 30 seconds at amplifier 0 = level I. */
	public static final Identifier TAN_THIRST = Identifier.fromNamespaceAndPath("toughasnails", "thirst");

	/** Homeostatic thirst (dehydration) effect — same soft-dep pattern as TAN. */
	public static final Identifier HOMEOSTATIC_THIRST = Identifier.fromNamespaceAndPath("homeostatic", "thirst");

	public static OptionalModEffectConsumeEffect tanDehydration(int durationTicks) {
		return new OptionalModEffectConsumeEffect(TAN_THIRST, durationTicks, 0);
	}

	public static OptionalModEffectConsumeEffect homeostaticThirst(int durationTicks) {
		return new OptionalModEffectConsumeEffect(HOMEOSTATIC_THIRST, durationTicks, 0);
	}

	@Override
	public ConsumeEffect.Type<? extends ConsumeEffect> getType() {
		return ModConsumeEffects.OPTIONAL_MOD_EFFECT;
	}

	@Override
	public boolean apply(Level level, ItemStack stack, LivingEntity user) {
		Holder.Reference<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.get(this.effectId).orElse(null);
		if (effect == null) {
			return false;
		}
		return user.addEffect(new MobEffectInstance(effect, this.duration, this.amplifier));
	}
}
