package com.distantnoise.network;

import com.distantnoise.Distantnoise;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C: stop grass rustle on an entity. */
public record GrassRustleStopPayload(int entityId) implements CustomPacketPayload {
	public static final Type<GrassRustleStopPayload> TYPE = new Type<>(Distantnoise.id("grass_rustle_stop"));

	public static final StreamCodec<FriendlyByteBuf, GrassRustleStopPayload> CODEC = StreamCodec.of(
			(buf, payload) -> buf.writeVarInt(payload.entityId),
			buf -> new GrassRustleStopPayload(buf.readVarInt())
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
