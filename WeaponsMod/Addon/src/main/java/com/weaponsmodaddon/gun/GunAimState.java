package com.weaponsmodaddon.gun;

import ckathode.weaponmod.item.IItemWeapon;
import ckathode.weaponmod.item.RangedComponent;
import com.weaponsmodaddon.ModItemTags;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Cross-side gun ADS helpers: fire-on-command (not on release), sticky-aim bookkeeping.
 */
public final class GunAimState {
	private static final Map<UUID, Boolean> FIRE_REQUEST = new ConcurrentHashMap<>();
	private static final Map<UUID, Boolean> SKIP_FIRE_ANIM = new ConcurrentHashMap<>();

	private GunAimState() {
	}

	public static boolean isGunStack(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		return stack.is(ModItemTags.TWO_HANDED_GUNS) || stack.is(ModItemTags.PISTOLS);
	}

	public static boolean isReadyGun(ItemStack stack) {
		return isGunStack(stack) && RangedComponent.isReadyToFire(stack);
	}

	public static boolean isAimingReadyGun(LivingEntity entity) {
		if (entity == null || !entity.isUsingItem()) {
			return false;
		}
		return isReadyGun(entity.getUseItem());
	}

	public static boolean isChargingSpearOrJavelin(LivingEntity entity) {
		if (entity == null || !entity.isUsingItem()) {
			return false;
		}
		ItemStack stack = entity.getUseItem();
		return stack.is(ModItemTags.SPEARS) || stack.is(ModItemTags.JAVELINS);
	}

	/** Body should face look / crosshair (gun ADS or spear charge). */
	public static boolean shouldLockBodyToLook(LivingEntity entity) {
		return isAimingReadyGun(entity) || isChargingSpearOrJavelin(entity);
	}

	public static boolean isWeaponModGunItem(ItemStack stack) {
		if (stack.isEmpty() || !(stack.getItem() instanceof IItemWeapon weapon)) {
			return false;
		}
		return weapon.getRangedComponent() != null && isGunStack(stack);
	}

	public static void requestFire(Player player) {
		FIRE_REQUEST.put(player.getUUID(), Boolean.TRUE);
	}

	public static boolean consumeFireRequest(LivingEntity entity) {
		return Boolean.TRUE.equals(FIRE_REQUEST.remove(entity.getUUID()));
	}

	public static void markSkipFireAnim(LivingEntity entity) {
		SKIP_FIRE_ANIM.put(entity.getUUID(), Boolean.TRUE);
	}

	public static boolean consumeSkipFireAnim(LivingEntity entity) {
		if (entity == null) {
			return false;
		}
		return Boolean.TRUE.equals(SKIP_FIRE_ANIM.remove(entity.getUUID()));
	}

	public static void clear(Player player) {
		UUID id = player.getUUID();
		FIRE_REQUEST.remove(id);
		SKIP_FIRE_ANIM.remove(id);
	}
}
