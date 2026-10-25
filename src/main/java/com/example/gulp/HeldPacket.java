package com.example.gulp;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> the swallowed player: who is holding them, which mode, how digested they are and their escape chance. */
public class HeldPacket {
    /** 0 = not swallowed, 1 = soft (just held), 2 = hard (being digested). */
    public final int mode;
    public final String holder;
    /** 0..1 */
    public final float progress;
    /** Percent chance that one struggle gets them out right now (hard mode). */
    public final float chance;

    public HeldPacket(int mode, String holder, float progress, float chance) {
        this.mode = mode;
        this.holder = holder;
        this.progress = progress;
        this.chance = chance;
    }

    public static void encode(HeldPacket m, FriendlyByteBuf buf) {
        buf.writeByte(m.mode);
        buf.writeUtf(m.holder);
        buf.writeFloat(m.progress);
        buf.writeFloat(m.chance);
    }

    public static HeldPacket decode(FriendlyByteBuf buf) {
        return new HeldPacket(buf.readByte(), buf.readUtf(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(HeldPacket m, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> ClientState.setHeld(m.mode, m.holder, m.progress, m.chance));
        c.setPacketHandled(true);
    }
}
