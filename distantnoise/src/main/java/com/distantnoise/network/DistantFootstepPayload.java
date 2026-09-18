package com.distantnoise.network;

import com.distantnoise.Distantnoise;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** S2C: play a distance-scaled footstep at world coords. */
public record DistantFootstepPayload(
		Identifier soundId,
		double x,
		double y,
		double z,
		float baseVolume,
		float pitch,
		long seed,
		byte relayKind,
		float maxRange
) implements CustomPacketPayload {
	public static final Identifier ID = Distantnoise.id("distant_footstep");
	public static final Type<DistantFootstepPayload> TYPE = new Type<>(ID);

	public static final StreamCodec<FriendlyByteBuf, DistantFootstepPayload> CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeIdentifier(payload.soundId);
				buf.writeDouble(payload.x);
				buf.writeDouble(payload.y);
				buf.writeDouble(payload.z);
				buf.writeFloat(payload.baseVolume);
				buf.writeFloat(payload.pitch);
				buf.writeLong(payload.seed);
				buf.writeByte(payload.relayKind);
				buf.writeFloat(payload.maxRange);
			},
			buf -> new DistantFootstepPayload(
					buf.readIdentifier(),
					buf.readDouble(),
					buf.readDouble(),
					buf.readDouble(),
					buf.readFloat(),
					buf.readFloat(),
					buf.readLong(),
					buf.readByte(),
					buf.readFloat()
			)
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
