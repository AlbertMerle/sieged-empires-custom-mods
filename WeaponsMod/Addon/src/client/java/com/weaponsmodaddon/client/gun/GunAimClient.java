package com.weaponsmodaddon.client.gun;

import com.weaponsmodaddon.client.anim.GunAnimController;
import com.weaponsmodaddon.client.sound.GunEarRingClient;
import com.weaponsmodaddon.client.sound.GunFireSoundClient;
import com.weaponsmodaddon.gun.GunAimState;
import com.weaponsmodaddon.network.GunFirePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Sticky ADS for WeaponMod guns:
 * right-click aim, release keeps ADS, left-click fires,
 * right-click again untoggles aim without firing.
 * <p>
 * Sticky reload is handled by {@link com.weaponsmodaddon.gun.GunReload} +
 * {@link com.weaponsmodaddon.client.mixin.MultiPlayerGameModeMixin}: RMB starts
 * reload; releasing does not cancel; hotbar swap does. Reload finish is
 * server-authoritative (no client {@code releaseUsingItem} on {@code STATE_RELOADED}).
 * <p>
 * Fire input runs on {@code START_CLIENT_TICK} via {@link net.minecraft.client.KeyMapping#consumeClick()}
 * so short LMB presses are not eaten by vanilla before {@code END_CLIENT_TICK}.
 */
public final class GunAimClient {
	private static boolean wasUseDown;
	private static boolean releasedWhileAds;
	private static boolean holdAim;
	/** After RMB cancel, block use until RMB is released so aim does not restart. */
	private static boolean suppressUseUntilRelease;

	private GunAimClient() {
	}

	public static boolean shouldHoldAim(LocalPlayer player) {
		return holdAim && player != null && GunAimState.isAimingReadyGun(player);
	}

	public static boolean shouldSuppressUseItem() {
		return suppressUseUntilRelease;
	}

	/**
	 * Input + ADS logic — register on {@code ClientTickEvents.START_CLIENT_TICK}
	 * so attack clicks are consumed before vanilla discards them while using an item.
	 */
	public static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.gui.screen() != null) {
			wasUseDown = false;
			releasedWhileAds = false;
			holdAim = false;
			suppressUseUntilRelease = false;
			return;
		}

		boolean useDown = client.options.keyUse.isDown();
		boolean useRising = useDown && !wasUseDown;
		boolean useFalling = !useDown && wasUseDown;

		if (suppressUseUntilRelease && !useDown) {
			suppressUseUntilRelease = false;
		}

		if (GunAimState.isAimingReadyGun(player)) {
			holdAim = true;
			if (useFalling) {
				releasedWhileAds = true;
			}
			// consumeClick: every LMB press while ADS fires (including sub-tick clicks).
			if (client.options.keyAttack.consumeClick()) {
				releasedWhileAds = false;
				holdAim = false;
				ItemStack aimed = player.getUseItem();
				// BANG first (instant, full volume) — before ear-ring muffling / server RTTs.
				GunFireSoundClient.playInstantBang(aimed);
				GunAimState.requestFire(player); // local release path / fire anim
				ClientPlayNetworking.send(new GunFirePayload(player.getYRot(), player.getXRot()));
				// Ring starts 500ms after the bang so the shot stays shocking.
				GunEarRingClient.trigger();
				if (client.gameMode != null) {
					client.gameMode.releaseUsingItem(player);
				}
			} else if (useRising && releasedWhileAds) {
				releasedWhileAds = false;
				holdAim = false;
				suppressUseUntilRelease = true;
				GunAimState.markSkipFireAnim(player);
				GunAnimController.notifyAimCancelled(player.getId());
				if (client.gameMode != null) {
					client.gameMode.releaseUsingItem(player);
				}
			}
		} else {
			holdAim = false;
			releasedWhileAds = false;
			ItemStack main = player.getMainHandItem();
			if (!GunAimState.isReadyGun(main) && !player.isUsingItem()) {
				GunAimState.clear(player);
			}
		}

		wasUseDown = useDown;

		if (GunAimState.shouldLockBodyToLook(player)) {
			player.yBodyRot = player.getYRot();
			player.yBodyRotO = player.yBodyRot;
		}
	}
}
