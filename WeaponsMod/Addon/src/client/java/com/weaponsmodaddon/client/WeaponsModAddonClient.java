package com.weaponsmodaddon.client;

import com.weaponsmodaddon.WeaponsModAddon;
import com.weaponsmodaddon.client.anim.GunAnimController;
import com.weaponsmodaddon.client.anim.GunAnimLibrary;
import com.weaponsmodaddon.client.anim.SpearAnimController;
import com.weaponsmodaddon.client.anim.SpearAnimLibrary;
import com.weaponsmodaddon.client.item.JavelinFlipProperty;
import com.weaponsmodaddon.client.item.SpearChargingProperty;
import com.weaponsmodaddon.client.gun.GunAimClient;
import com.weaponsmodaddon.client.hud.ReloadHud;
import com.weaponsmodaddon.client.scope.ScopedMusketAimClient;
import com.weaponsmodaddon.client.scope.ScopedMusketHud;
import com.weaponsmodaddon.client.scope.ScopedMusketSwayApplier;
import com.weaponsmodaddon.client.sound.GunBangEchoClient;
import com.weaponsmodaddon.client.sound.GunEarRingClient;
import com.weaponsmodaddon.client.sound.GunReloadSoundClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperties;
import net.minecraft.resources.Identifier;

public class WeaponsModAddonClient implements ClientModInitializer {
	private static final Identifier SCOPED_MUSKET_HUD = WeaponsModAddon.id("scoped_musket_hud");
	private static final Identifier RELOAD_HUD = WeaponsModAddon.id("reload_hud");

	@Override
	public void onInitializeClient() {
		GunAnimLibrary.load();
		SpearAnimLibrary.load();
		ConditionalItemModelProperties.ID_MAPPER.put(
				Identifier.fromNamespaceAndPath(WeaponsModAddon.MOD_ID, "javelin_flip"),
				JavelinFlipProperty.MAP_CODEC);
		ConditionalItemModelProperties.ID_MAPPER.put(
				Identifier.fromNamespaceAndPath(WeaponsModAddon.MOD_ID, "spear_charging"),
				SpearChargingProperty.MAP_CODEC);

		HudElementRegistry.attachElementAfter(
				VanillaHudElements.MISC_OVERLAYS,
				SCOPED_MUSKET_HUD,
				(graphics, deltaTracker) -> ScopedMusketHud.render(graphics));
		HudElementRegistry.attachElementAfter(
				VanillaHudElements.INFO_BAR,
				RELOAD_HUD,
				(graphics, deltaTracker) -> ReloadHud.render(graphics));

		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			if (client.player != null && !client.isPaused()) {
				ScopedMusketSwayApplier.tick(client.player);
			}
			// Before vanilla handleKeybinds eats attack clicks while using-item.
			GunAimClient.tick(client);
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ScopedMusketAimClient.tick(client);
			GunReloadSoundClient.tick(client);
			GunBangEchoClient.tick(client);
			GunEarRingClient.tick(client);
			if (client.player == null) {
				ScopedMusketSwayApplier.clear(null);
				GunAnimController.clear();
				SpearAnimController.clear();
				GunReloadSoundClient.clear();
				GunBangEchoClient.clear();
				GunEarRingClient.clear(client);
			} else if (client.player.tickCount % 200 == 0) {
				GunAnimController.prune(64);
				SpearAnimController.prune(64);
			}
		});
		WeaponsModAddon.LOGGER.info("WeaponsMod Addon client ready (gun/pistol + javelin + scoped muskets)");
	}
}
