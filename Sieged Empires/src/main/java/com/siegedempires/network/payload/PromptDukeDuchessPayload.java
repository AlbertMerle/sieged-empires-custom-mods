package com.siegedempires.network.payload;

import com.siegedempires.Siegedempires;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** S2C: Ask the target player to choose Duke or Duchess for a wartown. */
public record PromptDukeDuchessPayload(String wartownId, String wartownName, String emperorName)
		implements CustomPacketPayload {
	public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "prompt_duke_duchess");
	public static final Type<PromptDukeDuchessPayload> TYPE = new Type<>(ID_VALUE);
	public static final StreamCodec<FriendlyByteBuf, PromptDukeDuchessPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, PromptDukeDuchessPayload::wartownId,
			ByteBufCodecs.STRING_UTF8, PromptDukeDuchessPayload::wartownName,
			ByteBufCodecs.STRING_UTF8, PromptDukeDuchessPayload::emperorName,
			PromptDukeDuchessPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
