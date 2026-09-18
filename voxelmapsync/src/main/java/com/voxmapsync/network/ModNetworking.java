package com.voxmapsync.network;

import com.voxmapsync.VoxelMapSync;
import com.voxmapsync.server.MapSyncServer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ModNetworking {
	private static final AtomicBoolean TYPES_REGISTERED = new AtomicBoolean(false);

	/** Matches {@link com.voxmapsync.server.MapSyncServer} multipart slice size + headers. */
	private static final int REGION_DATA_MAX_BYTES = 32 * 1024;

	private ModNetworking() {
	}

	public static void registerPayloadTypes() {
		if (!TYPES_REGISTERED.compareAndSet(false, true)) {
			return;
		}
		registerServerbound(ClientHelloPayload.TYPE, ClientHelloPayload.CODEC);
		registerServerbound(RegionRequestPayload.TYPE, RegionRequestPayload.CODEC);
		registerServerbound(RegionUploadPayload.TYPE, RegionUploadPayload.CODEC, REGION_DATA_MAX_BYTES);
		registerClientbound(RegionDataPayload.TYPE, RegionDataPayload.CODEC, REGION_DATA_MAX_BYTES);
		registerClientbound(RegionUploadRequestPayload.TYPE, RegionUploadRequestPayload.CODEC, REGION_DATA_MAX_BYTES);
		registerClientbound(ClaimsPayload.TYPE, ClaimsPayload.CODEC, 1024 * 1024);
		VoxelMapSync.LOGGER.debug("Registered VoxelMapSync payloads");
	}

	private static <T extends CustomPacketPayload> void registerServerbound(
			CustomPacketPayload.Type<T> type,
			StreamCodec<RegistryFriendlyByteBuf, T> codec
	) {
		PayloadTypeRegistry.serverboundPlay().register(type, codec);
	}

	private static <T extends CustomPacketPayload> void registerServerbound(
			CustomPacketPayload.Type<T> type,
			StreamCodec<RegistryFriendlyByteBuf, T> codec,
			int maxBytes
	) {
		PayloadTypeRegistry.serverboundPlay().registerLarge(type, codec, maxBytes);
	}

	private static <T extends CustomPacketPayload> void registerClientbound(
			CustomPacketPayload.Type<T> type,
			StreamCodec<RegistryFriendlyByteBuf, T> codec,
			int maxBytes
	) {
		PayloadTypeRegistry.clientboundPlay().registerLarge(type, codec, maxBytes);
	}

	public static void registerServerReceivers() {
		registerPayloadTypes();
		ServerPlayNetworking.registerGlobalReceiver(ClientHelloPayload.TYPE, (payload, context) ->
				context.server().execute(() -> MapSyncServer.onClientHello(context.player(), payload)));
		ServerPlayNetworking.registerGlobalReceiver(RegionRequestPayload.TYPE, (payload, context) ->
				context.server().execute(() -> MapSyncServer.onRegionRequest(context.player(), payload)));
		ServerPlayNetworking.registerGlobalReceiver(RegionUploadPayload.TYPE, (payload, context) ->
				context.server().execute(() -> MapSyncServer.onRegionUpload(context.player(), payload)));
	}
}
