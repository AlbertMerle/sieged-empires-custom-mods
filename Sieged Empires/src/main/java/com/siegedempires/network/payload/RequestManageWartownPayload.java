package com.siegedempires.network.payload;

import com.siegedempires.Siegedempires;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** C2S: Emperor opens Manage Wartown for a captured territory. */
public record RequestManageWartownPayload(String wartownId) implements CustomPacketPayload {
	public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "request_manage_wartown");
	public static final Type<RequestManageWartownPayload> TYPE = new Type<>(ID_VALUE);
	public static final StreamCodec<FriendlyByteBuf, RequestManageWartownPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, RequestManageWartownPayload::wartownId,
			RequestManageWartownPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
