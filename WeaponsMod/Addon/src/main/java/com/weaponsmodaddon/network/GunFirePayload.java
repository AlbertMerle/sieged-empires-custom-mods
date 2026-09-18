package com.weaponsmodaddon.network;

import com.weaponsmodaddon.WeaponsModAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: fire the ready gun with client aim (yaw / pitch). */
public record GunFirePayload(float yaw, float pitch) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<GunFirePayload> TYPE =
			new CustomPacketPayload.Type<>(WeaponsModAddon.id("gun_fire"));

	public static final StreamCodec<FriendlyByteBuf, GunFirePayload> CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeFloat(payload.yaw);
				buf.writeFloat(payload.pitch);
			},
			buf -> new GunFirePayload(buf.readFloat(), buf.readFloat())
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
