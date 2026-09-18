package com.distantnoise.network;

import com.distantnoise.Distantnoise;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S: client reports a gun shot so the server can relay muffled audio to listeners
 * within {@code radius} (position is taken from the shooter on the server).
 */
public record GunShotReportPayload(byte kindId) implements CustomPacketPayload {
	public static final Identifier ID = Distantnoise.id("gun_shot_report");
	public static final Type<GunShotReportPayload> TYPE = new Type<>(ID);

	public static final StreamCodec<FriendlyByteBuf, GunShotReportPayload> CODEC = StreamCodec.of(
			(buf, payload) -> buf.writeByte(payload.kindId),
			buf -> new GunShotReportPayload(buf.readByte())
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
