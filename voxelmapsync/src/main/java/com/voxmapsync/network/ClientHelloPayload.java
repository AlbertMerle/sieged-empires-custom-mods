package com.voxmapsync.network;

import com.voxmapsync.VoxelMapSync;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** C2S: client announces its known region hashes so the server can send deltas. */
public record ClientHelloPayload(String worldKey, Map<String, String> knownHashes) implements CustomPacketPayload {
	public static final Identifier ID = VoxelMapSync.id("client_hello");
	public static final Type<ClientHelloPayload> TYPE = new Type<>(ID);

	/** Minecraft UTF-8 strings are capped at 32767 bytes; keep well under that per field. */
	public static final int MAX_KNOWN_ENTRIES = 256;

	public static final StreamCodec<RegistryFriendlyByteBuf, ClientHelloPayload> CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeUtf(payload.worldKey() != null ? payload.worldKey() : "");
				Map<String, String> known = payload.knownHashes() != null ? payload.knownHashes() : Map.of();
				buf.writeVarInt(known.size());
				for (Map.Entry<String, String> entry : known.entrySet()) {
					buf.writeUtf(entry.getKey() != null ? entry.getKey() : "");
					buf.writeUtf(entry.getValue() != null ? entry.getValue() : "");
				}
			},
			buf -> {
				String worldKey = buf.readUtf();
				int count = buf.readVarInt();
				if (count < 0) {
					throw new IllegalArgumentException("Negative known-hash count in client_hello");
				}
				Map<String, String> known = new LinkedHashMap<>(Math.max(0, count));
				for (int i = 0; i < count; i++) {
					known.put(buf.readUtf(), buf.readUtf());
				}
				return new ClientHelloPayload(worldKey, known);
			}
	);

	public ClientHelloPayload(String worldKey, Map<String, String> knownHashes) {
		this.worldKey = worldKey != null ? worldKey : "";
		this.knownHashes = knownHashes != null ? Map.copyOf(knownHashes) : Map.of();
	}

	/** Truncates to {@link #MAX_KNOWN_ENTRIES} so encoding cannot exceed the string limit. */
	public static ClientHelloPayload capped(String worldKey, Map<String, String> knownHashes) {
		if (knownHashes == null || knownHashes.isEmpty()) {
			return new ClientHelloPayload(worldKey, Map.of());
		}
		if (knownHashes.size() <= MAX_KNOWN_ENTRIES) {
			return new ClientHelloPayload(worldKey, knownHashes);
		}
		Map<String, String> capped = new LinkedHashMap<>(MAX_KNOWN_ENTRIES);
		for (Map.Entry<String, String> entry : knownHashes.entrySet()) {
			capped.put(entry.getKey(), entry.getValue());
			if (capped.size() >= MAX_KNOWN_ENTRIES) {
				break;
			}
		}
		return new ClientHelloPayload(worldKey, capped);
	}

	public Map<String, String> knownHashes() {
		return Collections.unmodifiableMap(knownHashes);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
