package com.siegedempires.network.payload;

import com.siegedempires.Siegedempires;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S: Create an empire from the GUI (avoids Brigadier chat-command parsing of descriptions).
 * bannerPatternsJoined uses ';' separators (e.g. "white:circle;blue:cross").
 * bannerPixels is an 800-char hex CustomBannerDesign encoding (empty = solid base color).
 */
public record CreateEmpirePayload(
		String name,
		String description,
		String title,
		String bannerBaseColor,
		String bannerPatternsJoined,
		String bannerPixels
) implements CustomPacketPayload {
	public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "create_empire");
	public static final Type<CreateEmpirePayload> TYPE = new Type<>(ID_VALUE);
	public static final StreamCodec<FriendlyByteBuf, CreateEmpirePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, CreateEmpirePayload::name,
			ByteBufCodecs.STRING_UTF8, CreateEmpirePayload::description,
			ByteBufCodecs.STRING_UTF8, CreateEmpirePayload::title,
			ByteBufCodecs.STRING_UTF8, CreateEmpirePayload::bannerBaseColor,
			ByteBufCodecs.STRING_UTF8, CreateEmpirePayload::bannerPatternsJoined,
			ByteBufCodecs.STRING_UTF8, CreateEmpirePayload::bannerPixels,
			CreateEmpirePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
