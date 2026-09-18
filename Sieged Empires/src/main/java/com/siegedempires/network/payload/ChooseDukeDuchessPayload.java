package com.siegedempires.network.payload;

import com.siegedempires.Siegedempires;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** C2S: Target player accepts the crown and picks Duke (King) or Duchess (Queen). */
public record ChooseDukeDuchessPayload(String wartownId, String monarchTitle) implements CustomPacketPayload {
	public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "choose_duke_duchess");
	public static final Type<ChooseDukeDuchessPayload> TYPE = new Type<>(ID_VALUE);
	public static final StreamCodec<FriendlyByteBuf, ChooseDukeDuchessPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, ChooseDukeDuchessPayload::wartownId,
			ByteBufCodecs.STRING_UTF8, ChooseDukeDuchessPayload::monarchTitle,
			ChooseDukeDuchessPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
