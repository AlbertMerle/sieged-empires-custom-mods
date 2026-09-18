package com.siegedempires.network.payload;

import com.siegedempires.Siegedempires;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: convert all plain banners in inventory to the player's
 * town or empire flag design. {@code factionKind} is {@code "town"} or {@code "empire"}.
 */
public record ConvertInventoryBannersPayload(String factionKind) implements CustomPacketPayload {
	public static final String KIND_TOWN = "town";
	public static final String KIND_EMPIRE = "empire";

	public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "convert_inventory_banners");
	public static final Type<ConvertInventoryBannersPayload> TYPE = new Type<>(ID_VALUE);
	public static final StreamCodec<FriendlyByteBuf, ConvertInventoryBannersPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, ConvertInventoryBannersPayload::factionKind,
			ConvertInventoryBannersPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
