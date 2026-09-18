package com.croplite.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.ConsumableListener;
import net.minecraft.world.level.Level;

/**
 * Marks a food as garlic-seasoned. Shown as "Has Garlic" in the tooltip and grants
 * Strength I (10s) + Regeneration I (2s) on eat without the raw-garlic damage.
 */
public record HasGarlicComponent() implements ConsumableListener {
	public static final HasGarlicComponent INSTANCE = new HasGarlicComponent();
	public static final Codec<HasGarlicComponent> CODEC = MapCodec.unitCodec(INSTANCE);
	public static final StreamCodec<RegistryFriendlyByteBuf, HasGarlicComponent> STREAM_CODEC =
			StreamCodec.unit(INSTANCE);

	/** Strength I 10 seconds, Regeneration I 2 seconds. */
	public static void applyBuffs(LivingEntity user) {
		user.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 200, 0));
		user.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0));
	}

	@Override
	public void onConsume(Level level, LivingEntity user, ItemStack stack, Consumable consumable) {
		if (!level.isClientSide()) {
			applyBuffs(user);
		}
	}
}
