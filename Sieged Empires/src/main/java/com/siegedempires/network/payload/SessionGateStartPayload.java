package com.siegedempires.network.payload;

import com.siegedempires.Siegedempires;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** S→C: player is held in the session gate; open Game Menu after world load. */
public record SessionGateStartPayload() implements CustomPacketPayload {
	public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "session_gate_start");
	public static final Type<SessionGateStartPayload> TYPE = new Type<>(ID_VALUE);
	public static final StreamCodec<FriendlyByteBuf, SessionGateStartPayload> CODEC =
			StreamCodec.unit(new SessionGateStartPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
