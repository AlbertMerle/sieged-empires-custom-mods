package com.voxmapsync.network;

import com.voxmapsync.VoxelMapSync;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S: client asks for VoxelMap region tiles visible (or near-visible) on the World Map.
 * Coordinates are VoxelMap region coords (256×256 blocks / 16×16 chunks), not MCA coords.
 */
public record RegionRequestPayload(
		String dimension,
		int centerRegionX,
		int centerRegionZ,
		int[] regionXs,
		int[] regionZs,
		long clientSeq
) implements CustomPacketPayload {
	public static final Identifier ID = VoxelMapSync.id("region_request");
	public static final Type<RegionRequestPayload> TYPE = new Type<>(ID);
	public static final int MAX_REGIONS = 256;

	public static final StreamCodec<RegistryFriendlyByteBuf, RegionRequestPayload> CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeUtf(payload.dimension() != null ? payload.dimension() : "");
				buf.writeVarInt(payload.centerRegionX());
				buf.writeVarInt(payload.centerRegionZ());
				int[] xs = payload.regionXs() != null ? payload.regionXs() : new int[0];
				int[] zs = payload.regionZs() != null ? payload.regionZs() : new int[0];
				int count = Math.min(xs.length, zs.length);
				buf.writeVarInt(count);
				for (int i = 0; i < count; i++) {
					buf.writeVarInt(xs[i]);
					buf.writeVarInt(zs[i]);
				}
				buf.writeLong(payload.clientSeq());
			},
			buf -> {
				String dimension = buf.readUtf();
				int centerX = buf.readVarInt();
				int centerZ = buf.readVarInt();
				int count = buf.readVarInt();
				if (count < 0 || count > MAX_REGIONS) {
					throw new IllegalArgumentException("Bad region_request count: " + count);
				}
				int[] xs = new int[count];
				int[] zs = new int[count];
				for (int i = 0; i < count; i++) {
					xs[i] = buf.readVarInt();
					zs[i] = buf.readVarInt();
				}
				long seq = buf.readLong();
				return new RegionRequestPayload(dimension, centerX, centerZ, xs, zs, seq);
			}
	);

	public RegionRequestPayload {
		dimension = dimension != null ? dimension : "";
		regionXs = regionXs != null ? regionXs : new int[0];
		regionZs = regionZs != null ? regionZs : new int[0];
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
