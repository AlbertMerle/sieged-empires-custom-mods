package com.distantnoise.network;

import com.distantnoise.Distantnoise;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** S2C: play a distance-scaled animal vocal at world coords. */
public record DistantVocalPayload(
		Identifier soundId,
		double x,
		double y,
		double z,
		float baseVolume,
		float pitch,
		long seed
) implements CustomPacketPayload {
	public static final Identifier ID = Distantnoise.id("distant_vocal");
	public static final Type<DistantVocalPayload> TYPE = new Type<>(ID);

	public static final StreamCodec<FriendlyByteBuf, DistantVocalPayload> CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeIdentifier(payload.soundId);
				buf.writeDouble(payload.x);
				buf.writeDouble(payload.y);
				buf.writeDouble(payload.z);
				buf.writeFloat(payload.baseVolume);
				buf.writeFloat(payload.pitch);
				buf.writeLong(payload.seed);
			},
			buf -> new DistantVocalPayload(
					buf.readIdentifier(),
					buf.readDouble(),
					buf.readDouble(),
					buf.readDouble(),
					buf.readFloat(),
					buf.readFloat(),
					buf.readLong()
			)
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
