package com.siegedempires.network.payload;

import com.siegedempires.Siegedempires;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record EmpireListPayload(String json) implements CustomPacketPayload {
    public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "empire_list");
    public static final Type<EmpireListPayload> TYPE = new Type<>(ID_VALUE);
    public static final StreamCodec<FriendlyByteBuf, EmpireListPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, EmpireListPayload::json,
            EmpireListPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}