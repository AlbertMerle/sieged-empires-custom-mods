package com.weaponsmodaddon.gun;

import ckathode.weaponmod.ReloadHelper;
import ckathode.weaponmod.ReloadHelper.ReloadState;
import ckathode.weaponmod.item.IItemWeapon;
import ckathode.weaponmod.item.RangedComponent;
import com.weaponsmodaddon.WeaponsModAddon;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Sticky gun reload: right-click starts loading; releasing RMB does not cancel.
 * Hotbar / item swap interrupts and leaves the gun unloaded ({@link ReloadState#STATE_NONE}).
 */
public final class GunReload {
	/** ~40% move speed while reloading (sneak-like). */
	public static final Identifier SLOWDOWN_ID = WeaponsModAddon.id("reload_slowdown");
	private static final AttributeModifier SLOWDOWN = new AttributeModifier(
			SLOWDOWN_ID,
			-0.60,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

	private GunReload() {
	}

	/**
	 * Actively chambering — keep sticky use through {@code STATE_NONE} and the brief
	 * {@code STATE_RELOADED} finish so the client does not send {@code RELEASE_USE_ITEM}
	 * before the server promotes to {@code STATE_READY} (that race unloaded the gun).
	 */
	public static boolean isReloading(LivingEntity entity) {
		if (entity == null || !entity.isUsingItem()) {
			return false;
		}
		ItemStack stack = entity.getUseItem();
		if (!GunAimState.isGunStack(stack)) {
			return false;
		}
		ReloadState state = ReloadHelper.getReloadState(stack);
		return state == ReloadState.STATE_NONE || state == ReloadState.STATE_RELOADED;
	}

	/** True while the reload HUD should show (including the finished-but-not-ready frame). */
	public static boolean shouldShowReloadHud(LivingEntity entity) {
		if (entity == null || !entity.isUsingItem()) {
			return false;
		}
		ItemStack stack = entity.getUseItem();
		if (!GunAimState.isGunStack(stack)) {
			return false;
		}
		return !RangedComponent.isReadyToFire(stack);
	}

	/** 0..1 progress for the reload bar. */
	public static float reloadProgress(LivingEntity entity) {
		if (!shouldShowReloadHud(entity)) {
			return 0.0F;
		}
		ItemStack stack = entity.getUseItem();
		ReloadState state = ReloadHelper.getReloadState(stack);
		if (state != ReloadState.STATE_NONE) {
			return 1.0F;
		}
		int duration = reloadDurationTicks(stack);
		if (duration <= 0) {
			return 0.0F;
		}
		return Mth.clamp(entity.getTicksUsingItem() / (float) duration, 0.0F, 1.0F);
	}

	public static int reloadDurationTicks(ItemStack stack) {
		if (stack.isEmpty() || !(stack.getItem() instanceof IItemWeapon weapon)) {
			return 0;
		}
		RangedComponent ranged = weapon.getRangedComponent();
		return ranged == null ? 0 : Math.max(1, ranged.getReloadDuration(stack));
	}

	/**
	 * Apply / remove movement slowdown while actively reloading.
	 * Safe on client and server (client for prediction).
	 */
	public static void tickSlowdown(Player player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		boolean reloading = isReloading(player);
		boolean has = speed.hasModifier(SLOWDOWN_ID);
		if (reloading && !has) {
			speed.addTransientModifier(SLOWDOWN);
		} else if (!reloading && has) {
			speed.removeModifier(SLOWDOWN_ID);
		}
	}

	/**
	 * Server: hotbar/item interrupt before ready → unloaded. If WeaponMod already reached
	 * {@code STATE_RELOADED}, promote to ready instead of wiping (avoids double-reload).
	 * Called from {@code LivingEntity.stopUsingItem} before the use item is cleared.
	 */
	public static void onStopUsing(LivingEntity entity, ItemStack useItem) {
		if (entity.level().isClientSide() || useItem.isEmpty()) {
			return;
		}
		if (!GunAimState.isGunStack(useItem)) {
			return;
		}
		ReloadState state = ReloadHelper.getReloadState(useItem);
		if (state == ReloadState.STATE_READY) {
			return;
		}
		if (state == ReloadState.STATE_RELOADED) {
			RangedComponent.setReloadState(useItem, ReloadState.STATE_READY);
			return;
		}
		RangedComponent.setReloadState(useItem, ReloadState.STATE_NONE);
	}

	/**
	 * Server only: after WeaponMod marks {@code STATE_RELOADED}, promote to ready and end use
	 * so the player does not need to release RMB (and does not enter ADS).
	 * Client must not {@code releaseUsingItem} here — that raced the server and could unload.
	 */
	public static void finishReloadIfNeeded(LivingEntity entity, ItemStack stack) {
		if (entity.level().isClientSide()) {
			return;
		}
		if (!GunAimState.isGunStack(stack)) {
			return;
		}
		if (ReloadHelper.getReloadState(stack) != ReloadState.STATE_RELOADED) {
			return;
		}
		RangedComponent.setReloadState(stack, ReloadState.STATE_READY);
		entity.stopUsingItem();
	}
}
