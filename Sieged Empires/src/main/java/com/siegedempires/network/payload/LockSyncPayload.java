package com.siegedempires.network.payload;

import com.siegedempires.Siegedempires;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C: Notifies clients of locked block positions for overlay rendering.
 * json is a Gson-serialized map of dimension -> list of "x,y,z" position strings.
 */
public record LockSyncPayload(String json) implements CustomPacketPayload {
    public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "lock_sync");
    public static final Type<LockSyncPayload> TYPE = new Type<>(ID_VALUE);
    public static final StreamCodec<FriendlyByteBuf, LockSyncPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, LockSyncPayload::json,
        LockSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}