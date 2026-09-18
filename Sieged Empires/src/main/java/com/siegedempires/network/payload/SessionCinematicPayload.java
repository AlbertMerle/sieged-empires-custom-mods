package com.siegedempires.network.payload;

import com.siegedempires.Siegedempires;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S→C: location title for the post-Join cinematic.
 * <p>
 * {@code kind}: {@code wilderness}, {@code ocean}, or {@code claim}.
 * {@code subtitle}: empty for towns; otherwise {@code (Daytime)}, {@code (Nighttime)},
 * or {@code (Thunderstorm)} (ocean storms only).
 * {@code snowy}: wilderness with biome base temperature &lt; 0.2 (white title + snowday ambient).
 */
public record SessionCinematicPayload(String title, String kind, String subtitle, boolean snowy)
		implements CustomPacketPayload {
	public static final String KIND_WILDERNESS = "wilderness";
	public static final String KIND_OCEAN = "ocean";
	public static final String KIND_CLAIM = "claim";

	public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "session_cinematic");
	public static final Type<SessionCinematicPayload> TYPE = new Type<>(ID_VALUE);
	public static final StreamCodec<FriendlyByteBuf, SessionCinematicPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, SessionCinematicPayload::title,
			ByteBufCodecs.STRING_UTF8, SessionCinematicPayload::kind,
			ByteBufCodecs.STRING_UTF8, SessionCinematicPayload::subtitle,
			ByteBufCodecs.BOOL, SessionCinematicPayload::snowy,
			SessionCinematicPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
