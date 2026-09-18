package com.voxmapsync.network;

import com.voxmapsync.VoxelMapSync;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** C2S: client uploads a VoxelMap region zip (or one multipart slice). */
public record RegionUploadPayload(
		String dimension,
		int regionX,
		int regionZ,
		String contentHash,
		int partIndex,
		int partCount,
		byte[] data
) implements CustomPacketPayload {
	public static final Identifier ID = VoxelMapSync.id("region_upload");
	public static final Type<RegionUploadPayload> TYPE = new Type<>(ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, RegionUploadPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, RegionUploadPayload::dimension,
			ByteBufCodecs.VAR_INT, RegionUploadPayload::regionX,
			ByteBufCodecs.VAR_INT, RegionUploadPayload::regionZ,
			ByteBufCodecs.STRING_UTF8, RegionUploadPayload::contentHash,
			ByteBufCodecs.VAR_INT, RegionUploadPayload::partIndex,
			ByteBufCodecs.VAR_INT, RegionUploadPayload::partCount,
			ByteBufCodecs.BYTE_ARRAY, RegionUploadPayload::data,
			RegionUploadPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
