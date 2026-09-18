package com.distantnoise.sound;

import com.distantnoise.network.ModNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Shared server-side relay for WeaponsMod shoot / impact hooks. */
public final class WeaponModNoiseRelay {
	private WeaponModNoiseRelay() {
	}

	public static void relay(Level world, DistantNoiseKind kind, double x, double y, double z) {
		if (world instanceof ServerLevel serverLevel) {
			ModNetworking.broadcast(serverLevel, kind, x, y, z);
		}
	}
}
