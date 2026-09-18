package com.siegedempires.network.payload;

import com.siegedempires.Siegedempires;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S: Client typed a rename and/or password in the Locksmithing Table GUI.
 * Server applies them to the open menu's input and rebuilds the output slot.
 */
public record LocksmithingUpdatePayload(String rename, String password) implements CustomPacketPayload {
	public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "locksmithing_update");
	public static final Type<LocksmithingUpdatePayload> TYPE = new Type<>(ID_VALUE);
	public static final StreamCodec<FriendlyByteBuf, LocksmithingUpdatePayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8, LocksmithingUpdatePayload::rename,
		ByteBufCodecs.STRING_UTF8, LocksmithingUpdatePayload::password,
		LocksmithingUpdatePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
