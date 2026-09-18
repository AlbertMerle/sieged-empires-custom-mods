package com.shiftyocean.client;

import net.fabricmc.api.ClientModInitializer;

/** Client entrypoint. Driven boats apply current via AbstractBoat mixin (client-authoritative). */
public class ShiftyoceanClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
	}
}
