package com.siegedempires.network.payload;

import com.siegedempires.Siegedempires;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** C2S: Emperor updates empire name and banner from Manage Empire → Edit. */
public record UpdateEmpirePayload(
		String name,
		String bannerBaseColor,
		String bannerPatternsJoined,
		String bannerPixels
) implements CustomPacketPayload {
	public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "update_empire");
	public static final Type<UpdateEmpirePayload> TYPE = new Type<>(ID_VALUE);
	public static final StreamCodec<FriendlyByteBuf, UpdateEmpirePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, UpdateEmpirePayload::name,
			ByteBufCodecs.STRING_UTF8, UpdateEmpirePayload::bannerBaseColor,
			ByteBufCodecs.STRING_UTF8, UpdateEmpirePayload::bannerPatternsJoined,
			ByteBufCodecs.STRING_UTF8, UpdateEmpirePayload::bannerPixels,
			UpdateEmpirePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
