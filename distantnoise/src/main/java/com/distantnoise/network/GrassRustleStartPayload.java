package com.distantnoise.network;

import com.distantnoise.Distantnoise;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C: begin looping grass rustle on an entity. */
public record GrassRustleStartPayload(
		int entityId,
		float startOffsetSeconds,
		boolean fleeing
) implements CustomPacketPayload {
	public static final Type<GrassRustleStartPayload> TYPE = new Type<>(Distantnoise.id("grass_rustle_start"));

	public static final StreamCodec<FriendlyByteBuf, GrassRustleStartPayload> CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeVarInt(payload.entityId);
				buf.writeFloat(payload.startOffsetSeconds);
				buf.writeBoolean(payload.fleeing);
			},
			buf -> new GrassRustleStartPayload(buf.readVarInt(), buf.readFloat(), buf.readBoolean())
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
