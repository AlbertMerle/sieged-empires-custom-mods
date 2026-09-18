package com.voxmapsync.client;

import com.voxmapsync.VoxelMapSync;
import com.voxmapsync.client.claims.ClientClaimsStore;
import com.voxmapsync.config.SyncConfig;
import com.voxmapsync.network.ClaimsPayload;
import com.voxmapsync.network.ClientHelloPayload;
import com.voxmapsync.network.ModNetworking;
import com.voxmapsync.network.RegionDataPayload;
import com.voxmapsync.network.RegionUploadRequestPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VoxelMapSyncClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SyncConfig.load();
		ModNetworking.registerPayloadTypes();

		ClientPlayNetworking.registerGlobalReceiver(RegionDataPayload.TYPE, (payload, context) ->
				ClientCacheApplier.onRegionPart(payload));
		ClientPlayNetworking.registerGlobalReceiver(ClaimsPayload.TYPE, (payload, context) ->
				context.client().execute(() -> ClientClaimsStore.apply(payload.json())));
		if (SyncConfig.clientUploadEnabled) {
			ClientPlayNetworking.registerGlobalReceiver(RegionUploadRequestPayload.TYPE, (payload, context) ->
					context.client().execute(() -> ClientRegionUploader.onUploadRequest(payload)));
		}

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			ClientCacheApplier.resetSession();
			// Delay two ticks so VoxelMap can set its world name (cache folder key).
			client.execute(() -> client.execute(() ->
					ClientCacheApplier.prepareJoin(() -> sendHelloBatches(client))));
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ViewportRegionRequester.resetSession();
			ClientMapSession.resetSession();
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ClientCacheApplier.tick();
			if (SyncConfig.clientUploadEnabled) {
				ClientRegionUploader.tick();
			}
		});
	}

	private static void sendHelloBatches(net.minecraft.client.Minecraft client) {
		if (client.getConnection() == null) {
			return;
		}
		if (!ClientPlayNetworking.canSend(ClientHelloPayload.TYPE)) {
			VoxelMapSync.LOGGER.debug("VoxelMapSync hello skipped — play channel not ready");
			return;
		}
		String worldKey = ClientCacheApplier.currentWorldKey();
		Map<String, String> known = ClientCacheApplier.helloHashes();
		List<Map.Entry<String, String>> entries = new ArrayList<>(known.entrySet());
		int batches = Math.max(1, (entries.size() + ClientHelloPayload.MAX_KNOWN_ENTRIES - 1)
				/ ClientHelloPayload.MAX_KNOWN_ENTRIES);
		int sent = 0;
		for (int batch = 0; batch < batches; batch++) {
			int from = batch * ClientHelloPayload.MAX_KNOWN_ENTRIES;
			int to = Math.min(entries.size(), from + ClientHelloPayload.MAX_KNOWN_ENTRIES);
			Map<String, String> slice = new LinkedHashMap<>();
			for (int i = from; i < to; i++) {
				Map.Entry<String, String> entry = entries.get(i);
				slice.put(entry.getKey(), entry.getValue());
			}
			ClientHelloPayload payload = new ClientHelloPayload(worldKey, slice);
			try {
				ClientPlayNetworking.send(payload);
				sent += slice.size();
			} catch (RuntimeException e) {
				VoxelMapSync.LOGGER.error(
						"VoxelMapSync hello encode/send failed worldKey={} batch={}/{}",
						worldKey, batch + 1, batches, e);
				return;
			}
		}
		VoxelMapSync.LOGGER.info(
				"VoxelMapSync hello sent worldKey={} known={} batches={} (voxelmap={}, siegedempires={})",
				worldKey,
				sent,
				batches,
				FabricLoader.getInstance().isModLoaded("voxelmap"),
				FabricLoader.getInstance().isModLoaded("siegedempires"));
	}
}
