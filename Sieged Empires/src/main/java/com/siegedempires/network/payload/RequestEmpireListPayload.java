package com.siegedempires.network.payload;

import com.siegedempires.Siegedempires;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RequestEmpireListPayload() implements CustomPacketPayload {
    public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "request_empire_list");
    public static final Type<RequestEmpireListPayload> TYPE = new Type<>(ID_VALUE);
    public static final StreamCodec<FriendlyByteBuf, RequestEmpireListPayload> CODEC =
            StreamCodec.unit(new RequestEmpireListPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}