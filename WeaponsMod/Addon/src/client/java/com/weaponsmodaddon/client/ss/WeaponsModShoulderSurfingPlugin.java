package com.weaponsmodaddon.client.ss;

import com.github.exopandora.shouldersurfing.api.client.event.ComputePlayerAimStateEvent;
import com.github.exopandora.shouldersurfing.api.client.event.ComputeTargetCameraOffsetEvent;
import com.github.exopandora.shouldersurfing.api.client.event.ComputeTemporaryFirstPersonStateEvent;
import com.github.exopandora.shouldersurfing.api.client.event.handler.ComputePlayerAimStateEventHandler;
import com.github.exopandora.shouldersurfing.api.client.event.handler.ComputeTargetCameraOffsetEventHandler;
import com.github.exopandora.shouldersurfing.api.client.event.handler.ComputeTemporaryFirstPersonStateEventHandler;
import com.github.exopandora.shouldersurfing.api.event.IEventBus;
import com.github.exopandora.shouldersurfing.api.plugin.IShoulderSurfingPlugin;
import com.weaponsmodaddon.client.camera.WeaponAimShoulderCamera;
import com.weaponsmodaddon.client.scope.ScopedMusketAimClient;
import com.weaponsmodaddon.gun.GunAimState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Non-scoped gun ADS / spear charge: SS aim-turn + right-shoulder camera offset.
 * Scoped muskets: never shoulder-aim (WeaponMod uses bow use-anim which SS treats as aiming);
 * force temporary first person so spyglass vignette + 2/4/8 FOV work like a real scope.
 */
public final class WeaponsModShoulderSurfingPlugin implements IShoulderSurfingPlugin {
	/** After SS defaults ({@link IEventBus#DEFAULT_PRIORITY}) so we can clear bow-anim aim for scopes. */
	private static final int AFTER_DEFAULT = IEventBus.DEFAULT_PRIORITY + 100;

	@Override
	public void register(IEventBus eventBus) {
		eventBus.register(AFTER_DEFAULT, (ComputePlayerAimStateEventHandler) this::onAimState);
		eventBus.register(AFTER_DEFAULT, (ComputeTargetCameraOffsetEventHandler) this::onCameraOffset);
		eventBus.register(AFTER_DEFAULT, (ComputeTemporaryFirstPersonStateEventHandler) this::onTemporaryFirstPerson);
	}

	private void onAimState(ComputePlayerAimStateEvent event) {
		LivingEntity entity = event.getEntity();
		if (entity instanceof LocalPlayer local && ScopedMusketAimClient.isAimingScoped(local)) {
			// SS marks bow use-anim as aiming; clear it so scoped stays spyglass FP, not shoulder ADS.
			event.setResult(false);
			return;
		}
		if (GunAimState.shouldLockBodyToLook(entity)) {
			event.setResult(true);
		}
	}

	private void onCameraOffset(ComputeTargetCameraOffsetEvent event) {
		Entity cameraEntity = event.getCameraEntity();
		if (cameraEntity instanceof LivingEntity living && WeaponAimShoulderCamera.shouldUseAimOffset(living)) {
			event.setResult(WeaponAimShoulderCamera.AIM_OFFSET);
		}
	}

	private void onTemporaryFirstPerson(ComputeTemporaryFirstPersonStateEvent event) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (ScopedMusketAimClient.isAimingScoped(player)) {
			event.setResult(true);
		}
	}
}
