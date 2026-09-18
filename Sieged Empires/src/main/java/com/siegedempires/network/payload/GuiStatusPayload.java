package com.siegedempires.network.payload;

import com.siegedempires.Siegedempires;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record GuiStatusPayload(
		boolean inTown,
		boolean isMonarch,
		boolean isLord,
		boolean isEmperor,
		boolean canAccessDiplomacy
) implements CustomPacketPayload {
	public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "gui_status");
	public static final Type<GuiStatusPayload> TYPE = new Type<>(ID_VALUE);
	public static final StreamCodec<FriendlyByteBuf, GuiStatusPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, GuiStatusPayload::inTown,
			ByteBufCodecs.BOOL, GuiStatusPayload::isMonarch,
			ByteBufCodecs.BOOL, GuiStatusPayload::isLord,
			ByteBufCodecs.BOOL, GuiStatusPayload::isEmperor,
			ByteBufCodecs.BOOL, GuiStatusPayload::canAccessDiplomacy,
			GuiStatusPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
