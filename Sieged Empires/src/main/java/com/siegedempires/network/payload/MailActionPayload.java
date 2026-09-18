package com.siegedempires.network.payload;

import com.siegedempires.Siegedempires;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MailActionPayload(String action, String mailType, String targetId,
                                String entityType, String entityName) implements CustomPacketPayload {
	public static final String ACTION_ACCEPT = "accept";
	public static final String ACTION_DECLINE = "decline";
	public static final String ACTION_DISMISS = "dismiss";

	public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "mail_action");
	public static final Type<MailActionPayload> TYPE = new Type<>(ID_VALUE);
	public static final StreamCodec<FriendlyByteBuf, MailActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, MailActionPayload::action,
			ByteBufCodecs.STRING_UTF8, MailActionPayload::mailType,
			ByteBufCodecs.STRING_UTF8, MailActionPayload::targetId,
			ByteBufCodecs.STRING_UTF8, MailActionPayload::entityType,
			ByteBufCodecs.STRING_UTF8, MailActionPayload::entityName,
			MailActionPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
