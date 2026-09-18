package com.siegedempires.network.payload;

import com.siegedempires.Siegedempires;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** C2S: Client finished the session join cinematic — check for a pending Duke/Duchess pick. */
public record RequestPendingDukeDuchessPayload() implements CustomPacketPayload {
	public static final Identifier ID_VALUE =
			Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "request_pending_duke_duchess");
	public static final Type<RequestPendingDukeDuchessPayload> TYPE = new Type<>(ID_VALUE);
	public static final StreamCodec<FriendlyByteBuf, RequestPendingDukeDuchessPayload> CODEC =
			StreamCodec.unit(new RequestPendingDukeDuchessPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
