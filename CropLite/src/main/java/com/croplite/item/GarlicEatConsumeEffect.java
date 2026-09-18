package com.croplite.item;

import com.mojang.serialization.MapCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.level.Level;

/**
 * Raw garlic: 1 HP damage plus Strength I (10s) and Regeneration I (2s).
 */
public record GarlicEatConsumeEffect() implements ConsumeEffect {
	public static final GarlicEatConsumeEffect INSTANCE = new GarlicEatConsumeEffect();
	public static final MapCodec<GarlicEatConsumeEffect> CODEC = MapCodec.unit(INSTANCE);
	public static final StreamCodec<RegistryFriendlyByteBuf, GarlicEatConsumeEffect> STREAM_CODEC =
			StreamCodec.unit(INSTANCE);

	@Override
	public ConsumeEffect.Type<? extends ConsumeEffect> getType() {
		return ModConsumeEffects.GARLIC_EAT;
	}

	@Override
	public boolean apply(Level level, ItemStack stack, LivingEntity user) {
		if (level instanceof ServerLevel serverLevel) {
			user.hurtServer(serverLevel, user.damageSources().generic(), 1.0F);
			HasGarlicComponent.applyBuffs(user);
			return true;
		}
		return false;
	}
}
