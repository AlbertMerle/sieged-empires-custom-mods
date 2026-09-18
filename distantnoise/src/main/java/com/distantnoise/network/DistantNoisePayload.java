package com.distantnoise.network;

import com.distantnoise.Distantnoise;
import com.distantnoise.sound.DistantNoiseKind;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** S2C: play a distance-muffled directional loud noise at world coords. */
public record DistantNoisePayload(
		byte kindId,
		double x,
		double y,
		double z,
		long seed
) implements CustomPacketPayload {
	public static final Identifier ID = Distantnoise.id("distant_noise");
	public static final Type<DistantNoisePayload> TYPE = new Type<>(ID);

	public static final StreamCodec<FriendlyByteBuf, DistantNoisePayload> CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeByte(payload.kindId);
				buf.writeDouble(payload.x);
				buf.writeDouble(payload.y);
				buf.writeDouble(payload.z);
				buf.writeLong(payload.seed);
			},
			buf -> new DistantNoisePayload(
					buf.readByte(),
					buf.readDouble(),
					buf.readDouble(),
					buf.readDouble(),
					buf.readLong()
			)
	);

	public DistantNoisePayload(DistantNoiseKind kind, double x, double y, double z, long seed) {
		this(kind.id(), x, y, z, seed);
	}

	public DistantNoiseKind kind() {
		return DistantNoiseKind.byId(kindId);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
