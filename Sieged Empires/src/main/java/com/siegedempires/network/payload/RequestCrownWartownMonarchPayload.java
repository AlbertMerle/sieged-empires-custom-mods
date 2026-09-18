package com.siegedempires.network.payload;

import com.siegedempires.Siegedempires;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** C2S: Emperor requests to crown a player as Duke/Duchess of a wartown. */
public record RequestCrownWartownMonarchPayload(String wartownId, String targetUuid) implements CustomPacketPayload {
	public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "request_crown_wartown_monarch");
	public static final Type<RequestCrownWartownMonarchPayload> TYPE = new Type<>(ID_VALUE);
	public static final StreamCodec<FriendlyByteBuf, RequestCrownWartownMonarchPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, RequestCrownWartownMonarchPayload::wartownId,
			ByteBufCodecs.STRING_UTF8, RequestCrownWartownMonarchPayload::targetUuid,
			RequestCrownWartownMonarchPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
