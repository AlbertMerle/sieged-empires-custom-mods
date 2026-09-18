package com.voxmapsync.network;

import com.voxmapsync.VoxelMapSync;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** S2C: one VoxelMap region zip (or a chunk of it). */
public record RegionDataPayload(
		String dimension,
		int regionX,
		int regionZ,
		String contentHash,
		int partIndex,
		int partCount,
		byte[] data
) implements CustomPacketPayload {
	public static final Identifier ID = VoxelMapSync.id("region_data");
	public static final Type<RegionDataPayload> TYPE = new Type<>(ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, RegionDataPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, RegionDataPayload::dimension,
			ByteBufCodecs.VAR_INT, RegionDataPayload::regionX,
			ByteBufCodecs.VAR_INT, RegionDataPayload::regionZ,
			ByteBufCodecs.STRING_UTF8, RegionDataPayload::contentHash,
			ByteBufCodecs.VAR_INT, RegionDataPayload::partIndex,
			ByteBufCodecs.VAR_INT, RegionDataPayload::partCount,
			ByteBufCodecs.BYTE_ARRAY, RegionDataPayload::data,
			RegionDataPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
