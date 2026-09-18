package com.weaponsmodaddon.gun;

import ckathode.weaponmod.item.ItemMusket;
import ckathode.weaponmod.item.RangedComponent;
import com.weaponsmodaddon.ModItemTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Suppresses musket/flintlock entity spawn while {@link GunHitscan} runs {@code ranged.fire()}. */
public final class GunHitscanFire {
	private static final ThreadLocal<Boolean> SUPPRESS_PROJECTILE = ThreadLocal.withInitial(() -> false);

	private GunHitscanFire() {
	}

	public static boolean usesHitscan(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		return stack.is(ModItemTags.PISTOLS)
				|| stack.is(ModItemTags.SCOPED_MUSKETS)
				|| stack.getItem() instanceof ItemMusket;
	}

	public static boolean suppressProjectileSpawn() {
		return Boolean.TRUE.equals(SUPPRESS_PROJECTILE.get());
	}

	public static void fire(ServerPlayer player, ItemStack stack, RangedComponent ranged, float yaw, float pitch, int timeLeft) {
		withSuppressedProjectile(() -> ranged.fire(stack, player.level(), player, timeLeft));
		GunHitscan.schedule(player, stack.copy(), yaw, pitch);
	}

	public static boolean redirectAddEntity(Level level, Entity entity) {
		if (suppressProjectileSpawn()) {
			return true;
		}
		return level.addFreshEntity(entity);
	}

	private static void withSuppressedProjectile(Runnable action) {
		SUPPRESS_PROJECTILE.set(true);
		try {
			action.run();
		} finally {
			SUPPRESS_PROJECTILE.remove();
		}
	}
}
