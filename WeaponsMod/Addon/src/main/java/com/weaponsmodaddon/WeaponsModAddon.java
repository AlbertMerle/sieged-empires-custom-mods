package com.weaponsmodaddon;

import ckathode.weaponmod.WMRegistries;
import ckathode.weaponmod.WeaponModConfig;
import com.weaponsmodaddon.config.AddonConfig;
import com.weaponsmodaddon.item.ScopedMusketItems;
import com.weaponsmodaddon.network.ModNetworking;
import com.weaponsmodaddon.sound.ModSounds;
import com.weaponsmodaddon.gun.GunHitscan;
import com.weaponsmodaddon.spear.SpearPendingThrows;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WeaponsModAddon implements ModInitializer {
	public static final String MOD_ID = "weaponsmodaddon";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		// Loom may run us before weaponmod. ItemMusket / MeleeCompKnife bake attribute
		// Holders at construct time — null asHolder() → creative tooltip NPE.
		WeaponModConfig.init();
		WMRegistries.init();
		AddonConfig.load();
		ModSounds.register();
		ScopedMusketItems.register();
		ModNetworking.register();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			SpearPendingThrows.tickServer(server);
			com.weaponsmodaddon.gun.GunHitscan.tickServer(server);
		});
		LOGGER.info("WeaponsMod Addon ready (weaponmod={}, scoped muskets registered)",
				FabricLoader.getInstance().isModLoaded("weaponmod"));
	}
}
