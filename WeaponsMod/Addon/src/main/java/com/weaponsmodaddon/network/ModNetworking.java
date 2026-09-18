package com.weaponsmodaddon.network;

import ckathode.weaponmod.ReloadHelper;
import ckathode.weaponmod.item.IItemWeapon;
import ckathode.weaponmod.item.RangedComponent;
import com.weaponsmodaddon.gun.GunAimState;
import com.weaponsmodaddon.gun.GunHitscanFire;
import com.weaponsmodaddon.sound.GunFireSoundMarkers;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class ModNetworking {
	private ModNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(GunFirePayload.TYPE, GunFirePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(GunFirePayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> handleFire(player, payload));
		});
	}

	private static void handleFire(ServerPlayer player, GunFirePayload payload) {
		ItemStack stack = player.isUsingItem() ? player.getUseItem() : player.getMainHandItem();
		if (!GunAimState.isReadyGun(stack)) {
			stack = player.getMainHandItem();
		}
		if (!GunAimState.isReadyGun(stack)) {
			return;
		}
		if (!(stack.getItem() instanceof IItemWeapon weapon)) {
			return;
		}
		RangedComponent ranged = weapon.getRangedComponent();
		if (ranged == null) {
			return;
		}
		int timeLeft = player.isUsingItem() ? player.getUseItemRemainingTicks() : 0;
		if (!ranged.hasAmmoAndConsume(stack, player.level(), player)) {
			return;
		}

		GunFireSoundMarkers.setExceptPlayer(player);
		try {
			if (GunHitscanFire.usesHitscan(stack)) {
				GunHitscanFire.fire(player, stack, ranged, payload.yaw(), payload.pitch(), timeLeft);
			} else {
				ranged.fire(stack, player.level(), player, timeLeft);
			}
		} finally {
			GunFireSoundMarkers.clearExceptPlayer();
		}

		RangedComponent.setReloadState(stack, ReloadHelper.ReloadState.STATE_NONE);
		if (player.isUsingItem()) {
			player.stopUsingItem();
		}
	}
}
