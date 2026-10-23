package com.example.gulp;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: "play this Gulp sound here". The CLIENT picks which sound to actually play
 * (custom or Minecraft) and whether it is switched on, based on that player's Settings.
 */
public class SoundPacket {
    private final int type;
    private final double x, y, z;

    public SoundPacket(int type, double x, double y, double z) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static void encode(SoundPacket m, FriendlyByteBuf buf) {
        buf.writeInt(m.type);
        buf.writeDouble(m.x);
        buf.writeDouble(m.y);
        buf.writeDouble(m.z);
    }

    public static SoundPacket decode(FriendlyByteBuf buf) {
        return new SoundPacket(buf.readInt(), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public static void handle(SoundPacket m, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> ClientState.firePlaySound(m.type, m.x, m.y, m.z));
        c.setPacketHandled(true);
    }
}
