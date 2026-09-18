package com.siegedempires.network.payload;

import com.siegedempires.Siegedempires;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** S2C: Result of empire name/banner update. */
public record UpdateEmpireResultPayload(boolean success, String errorMessage) implements CustomPacketPayload {
	public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "update_empire_result");
	public static final Type<UpdateEmpireResultPayload> TYPE = new Type<>(ID_VALUE);
	public static final StreamCodec<FriendlyByteBuf, UpdateEmpireResultPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, UpdateEmpireResultPayload::success,
			ByteBufCodecs.STRING_UTF8, UpdateEmpireResultPayload::errorMessage,
			UpdateEmpireResultPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
