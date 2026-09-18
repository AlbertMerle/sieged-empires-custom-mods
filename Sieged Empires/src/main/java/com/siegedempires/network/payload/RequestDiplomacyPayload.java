package com.siegedempires.network.payload;

import com.siegedempires.Siegedempires;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RequestDiplomacyPayload() implements CustomPacketPayload {
	public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "request_diplomacy");
	public static final Type<RequestDiplomacyPayload> TYPE = new Type<>(ID_VALUE);
	public static final StreamCodec<FriendlyByteBuf, RequestDiplomacyPayload> CODEC =
			StreamCodec.unit(new RequestDiplomacyPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
