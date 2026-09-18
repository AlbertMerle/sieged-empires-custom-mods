package com.siegedempires.network.payload;

import com.siegedempires.Siegedempires;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** S2C: Result of a rename-town request from the Manage Town GUI. */
public record RenameTownResultPayload(boolean success, String errorMessage) implements CustomPacketPayload {
	public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "rename_town_result");
	public static final Type<RenameTownResultPayload> TYPE = new Type<>(ID_VALUE);
	public static final StreamCodec<FriendlyByteBuf, RenameTownResultPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, RenameTownResultPayload::success,
			ByteBufCodecs.STRING_UTF8, RenameTownResultPayload::errorMessage,
			RenameTownResultPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
