package com.siegedempires.network.payload;

import com.siegedempires.Siegedempires;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RequestGuiStatusPayload() implements CustomPacketPayload {
	public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "request_gui_status");
	public static final Type<RequestGuiStatusPayload> TYPE = new Type<>(ID_VALUE);
	public static final StreamCodec<FriendlyByteBuf, RequestGuiStatusPayload> CODEC =
			StreamCodec.unit(new RequestGuiStatusPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
