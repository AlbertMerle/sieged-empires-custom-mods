package com.siegedempires.network.payload;

import com.siegedempires.Siegedempires;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S: Create a town from the GUI (avoids Brigadier chat-command parsing of descriptions).
 * bannerPatternsJoined uses ';' separators (e.g. "white:circle;blue:cross").
 * bannerPixels is an 800-char hex CustomBannerDesign encoding (empty = solid base color).
 */
public record CreateTownPayload(
		String name,
		String description,
		String monarchTitle,
		String bannerBaseColor,
		String bannerPatternsJoined,
		String bannerPixels
) implements CustomPacketPayload {
	public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "create_town");
	public static final Type<CreateTownPayload> TYPE = new Type<>(ID_VALUE);
	public static final StreamCodec<FriendlyByteBuf, CreateTownPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, CreateTownPayload::name,
			ByteBufCodecs.STRING_UTF8, CreateTownPayload::description,
			ByteBufCodecs.STRING_UTF8, CreateTownPayload::monarchTitle,
			ByteBufCodecs.STRING_UTF8, CreateTownPayload::bannerBaseColor,
			ByteBufCodecs.STRING_UTF8, CreateTownPayload::bannerPatternsJoined,
			ByteBufCodecs.STRING_UTF8, CreateTownPayload::bannerPixels,
			CreateTownPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
