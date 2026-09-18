package com.siegedempires.network.payload;

import com.siegedempires.Siegedempires;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** C2S: Monarch updates town name and banner from Manage Town → Edit. */
public record UpdateTownPayload(
		String name,
		String bannerBaseColor,
		String bannerPatternsJoined,
		String bannerPixels
) implements CustomPacketPayload {
	public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "update_town");
	public static final Type<UpdateTownPayload> TYPE = new Type<>(ID_VALUE);
	public static final StreamCodec<FriendlyByteBuf, UpdateTownPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, UpdateTownPayload::name,
			ByteBufCodecs.STRING_UTF8, UpdateTownPayload::bannerBaseColor,
			ByteBufCodecs.STRING_UTF8, UpdateTownPayload::bannerPatternsJoined,
			ByteBufCodecs.STRING_UTF8, UpdateTownPayload::bannerPixels,
			UpdateTownPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
