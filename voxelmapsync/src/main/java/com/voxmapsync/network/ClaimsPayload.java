package com.voxmapsync.network;

import com.voxmapsync.VoxelMapSync;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** S2C: full Sieged Empires claim snapshot (JSON). */
public record ClaimsPayload(String json) implements CustomPacketPayload {
	public static final Identifier ID = VoxelMapSync.id("claims");
	public static final Type<ClaimsPayload> TYPE = new Type<>(ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, ClaimsPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, ClaimsPayload::json,
			ClaimsPayload::new
	);

	public ClaimsPayload(String json) {
		this.json = json != null ? json : "[]";
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
