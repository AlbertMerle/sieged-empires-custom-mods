package com.voxmapsync;

import com.voxmapsync.command.MapsyncCommand;
import com.voxmapsync.config.SyncConfig;
import com.voxmapsync.network.ModNetworking;
import com.voxmapsync.server.MapSyncServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VoxelMapSync implements ModInitializer {
	public static final String MOD_ID = "voxelmapsync";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		SyncConfig.load();
		ModNetworking.registerPayloadTypes();
		ModNetworking.registerServerReceivers();
		MapsyncCommand.register();

		ServerLifecycleEvents.SERVER_STARTED.register(MapSyncServer::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(MapSyncServer::onServerStopping);
		ServerTickEvents.END_SERVER_TICK.register(MapSyncServer::onServerTick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				MapSyncServer.onPlayerJoin(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				MapSyncServer.onPlayerDisconnect(handler.getPlayer()));

		LOGGER.info("VoxelMapSync ready — terrain sync every {}s, claims={}",
				SyncConfig.syncIntervalSeconds, SyncConfig.claimsEnabled);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
