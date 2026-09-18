package com.voxmapsync.network;

import com.voxmapsync.VoxelMapSync;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C: server asks the client to upload VoxelMap region tiles it has explored locally.
 * {@code serverHashes[i]} is the server's current hash for that tile, or empty when absent.
 */
public record RegionUploadRequestPayload(
		String dimension,
		int[] regionXs,
		int[] regionZs,
		String[] serverHashes
) implements CustomPacketPayload {
	public static final Identifier ID = VoxelMapSync.id("region_upload_request");
	public static final Type<RegionUploadRequestPayload> TYPE = new Type<>(ID);
	public static final int MAX_REGIONS = 64;

	public static final StreamCodec<RegistryFriendlyByteBuf, RegionUploadRequestPayload> CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeUtf(payload.dimension() != null ? payload.dimension() : "");
				int[] xs = payload.regionXs() != null ? payload.regionXs() : new int[0];
				int[] zs = payload.regionZs() != null ? payload.regionZs() : new int[0];
				String[] hashes = payload.serverHashes() != null ? payload.serverHashes() : new String[0];
				int count = Math.min(xs.length, Math.min(zs.length, hashes.length));
				buf.writeVarInt(count);
				for (int i = 0; i < count; i++) {
					buf.writeVarInt(xs[i]);
					buf.writeVarInt(zs[i]);
					buf.writeUtf(hashes[i] != null ? hashes[i] : "");
				}
			},
			buf -> {
				String dimension = buf.readUtf();
				int count = buf.readVarInt();
				if (count < 0 || count > MAX_REGIONS) {
					throw new IllegalArgumentException("Bad region_upload_request count: " + count);
				}
				int[] xs = new int[count];
				int[] zs = new int[count];
				String[] hashes = new String[count];
				for (int i = 0; i < count; i++) {
					xs[i] = buf.readVarInt();
					zs[i] = buf.readVarInt();
					hashes[i] = buf.readUtf();
				}
				return new RegionUploadRequestPayload(dimension, xs, zs, hashes);
			}
	);

	public RegionUploadRequestPayload {
		dimension = dimension != null ? dimension : "";
		regionXs = regionXs != null ? regionXs : new int[0];
		regionZs = regionZs != null ? regionZs : new int[0];
		serverHashes = serverHashes != null ? serverHashes : new String[0];
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
