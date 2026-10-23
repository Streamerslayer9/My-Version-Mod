package com.example.gulp;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Tells clients how full a player's belly is (0..1) so everyone sees the same bulge. */
public class BellyPacket {
    private final UUID player;
    private final float ratio;

    public BellyPacket(UUID player, float ratio) {
        this.player = player;
        this.ratio = ratio;
    }

    public static void encode(BellyPacket m, FriendlyByteBuf buf) {
        buf.writeUUID(m.player);
        buf.writeFloat(m.ratio);
    }

    public static BellyPacket decode(FriendlyByteBuf buf) {
        return new BellyPacket(buf.readUUID(), buf.readFloat());
    }

    public static void handle(BellyPacket m, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> ClientState.setBelly(m.player, m.ratio));
        c.setPacketHandled(true);
    }
}
