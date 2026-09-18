package com.moremeatmilk.milking;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Lets players milk extra adult livestock with an empty bucket, matching vanilla cows.
 * Goats are omitted — they already milk in vanilla.
 */
public final class ModMilking {

	private static final Set<EntityType<?>> MILKABLE = Set.of(
		EntityTypes.LLAMA,
		EntityTypes.TRADER_LLAMA,
		EntityTypes.CAMEL,
		EntityTypes.SHEEP
	);

	private ModMilking() {
	}

	public static void initialize() {
		UseEntityCallback.EVENT.register(ModMilking::onUseEntity);
	}

	private static InteractionResult onUseEntity(
		Player player,
		Level level,
		InteractionHand hand,
		Entity entity,
		@Nullable EntityHitResult hitResult
	) {
		if (player.isSpectator()) {
			return InteractionResult.PASS;
		}

		ItemStack held = player.getItemInHand(hand);
		if (!held.is(Items.BUCKET)
			|| !(entity instanceof LivingEntity living)
			|| living.isBaby()
			|| !MILKABLE.contains(entity.getType())) {
			return InteractionResult.PASS;
		}

		player.playSound(SoundEvents.COW_MILK, 1.0F, 1.0F);
		ItemStack filled = ItemUtils.createFilledResult(held, player, Items.MILK_BUCKET.getDefaultInstance());
		player.setItemInHand(hand, filled);
		return InteractionResult.SUCCESS;
	}
}
