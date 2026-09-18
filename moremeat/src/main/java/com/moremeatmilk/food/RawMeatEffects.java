package com.moremeatmilk.food;

import com.moremeatmilk.Moremeatmilk;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class RawMeatEffects {

	private static final int HUNGER_DURATION = 600;

	private RawMeatEffects() {
	}

	public static void onConsumed(Level level, LivingEntity entity, ItemStack stack) {
		if (level.isClientSide() || !RawMeatHelper.isPenalizedRawMeat(stack)) {
			return;
		}

		if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
			return;
		}

		entity.addEffect(new MobEffectInstance(MobEffects.HUNGER, HUNGER_DURATION, 0));

		if (entity instanceof Player player && FabricLoader.getInstance().isModLoaded("toughasnails")) {
			applyToughAsNailsDehydration(player);
		}
	}

	private static void applyToughAsNailsDehydration(Player player) {
		try {
			Class.forName("com.moremeatmilk.compat.ToughAsNailsCompat")
				.getMethod("applyDehydration", Player.class)
				.invoke(null, player);
		} catch (ReflectiveOperationException exception) {
			Moremeatmilk.LOGGER.warn("Failed to apply Tough As Nails dehydration for raw meat", exception);
		}
	}

}
