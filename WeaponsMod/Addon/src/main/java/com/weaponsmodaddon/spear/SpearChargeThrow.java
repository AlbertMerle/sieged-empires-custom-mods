package com.weaponsmodaddon.spear;

import ckathode.weaponmod.entity.projectile.EntitySpear;
import ckathode.weaponmod.item.MeleeComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Bow-style charged throw for WeaponMod spears: draw time → {@link BowItem#getPowerForTime},
 * velocity {@code power * 3.0F} (same as arrows). Projectile spawns at the halfway mark of
 * the throw clip (spear stays in hand until then).
 */
public final class SpearChargeThrow {
	/** Same multiplier as {@link BowItem} arrow launch. */
	private static final float MAX_VELOCITY = 3.0F;
	private static final float UNCERTAINTY = 1.0F;

	private SpearChargeThrow() {
	}

	/**
	 * Charge/throw only while standing or sneaking — not crawling, swimming, elytra, etc.
	 */
	public static boolean canCharge(LivingEntity entity) {
		Pose pose = entity.getPose();
		return pose == Pose.STANDING || pose == Pose.CROUCHING;
	}

	/**
	 * @param useDuration total use duration of the spear item (typically 72000)
	 * @param remainingUseTicks ticks left when released
	 * @return true if a throw was scheduled (or attempted on client)
	 */
	public static boolean release(ItemStack stack, Level level, LivingEntity user, int useDuration, int remainingUseTicks) {
		if (!(user instanceof Player player)) {
			return false;
		}
		if (stack.isEmpty()) {
			return false;
		}
		if (SpearPendingThrows.hasPending(player)) {
			return false;
		}

		int timeHeld = useDuration - remainingUseTicks;
		float power = BowItem.getPowerForTime(timeHeld);
		if (power < 0.1F) {
			return false;
		}

		boolean crit = power == 1.0F;
		InteractionHand hand = player.getMainHandItem() == stack
				? InteractionHand.MAIN_HAND
				: InteractionHand.OFF_HAND;

		// One spear for the pending throw / projectile (hand may hold a stack of up to 8).
		ItemStack one = stack.copy();
		one.setCount(1);

		// Client starts the throw clip; server schedules spawn at clip halfway.
		if (!level.isClientSide()) {
			SpearPendingThrows.schedule(player, one, hand, power, crit);
		}
		return true;
	}

	/** Called at throw-clip halfway — spawn projectile and remove the held spear. */
	public static void spawnThrownSpear(
			Player player,
			ItemStack spearCopy,
			InteractionHand hand,
			float power,
			boolean crit
	) {
		Level level = player.level();
		if (level.isClientSide()) {
			return;
		}

		ItemStack thrown = spearCopy.copy();
		thrown.setCount(1);

		float velocity = power * MAX_VELOCITY;
		EntitySpear spear = new EntitySpear(level, player, thrown);
		spear.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, velocity, UNCERTAINTY);
		MeleeComponent.applyProjectileEnchantments(spear, spearCopy);
		spear.setCritArrow(crit);
		level.addFreshEntity(spear);

		level.playSound(
				null,
				player.getX(),
				player.getY(),
				player.getZ(),
				SoundEvents.ARROW_SHOOT,
				SoundSource.PLAYERS,
				1.0F,
				1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + power * 0.5F
		);

		if (!player.hasInfiniteMaterials()) {
			ItemStack held = player.getItemInHand(hand);
			if (!held.isEmpty() && held.is(spearCopy.getItem())) {
				held.shrink(1);
			}
		}
	}
}
