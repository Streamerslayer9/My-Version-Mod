package com.example.gulp;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 0 swallow | 1 toggle mode | 2 release last | 3 release all
 *  4 release entry (arg = uid) | 5 digest entry now (arg = uid) | 6 buy perk (arg = perk index) */
public class ActionPacket {
    private final int action;
    private final int arg;

    public ActionPacket(int action, int arg) {
        this.action = action;
        this.arg = arg;
    }

    public static void encode(ActionPacket m, FriendlyByteBuf buf) {
        buf.writeByte(m.action);
        buf.writeInt(m.arg);
    }

    public static ActionPacket decode(FriendlyByteBuf buf) {
        return new ActionPacket(buf.readByte(), buf.readInt());
    }

    public static void handle(ActionPacket m, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        ServerPlayer sender = c.getSender();
        c.enqueueWork(() -> StomachLogic.handleAction(sender, m.action, m.arg));
        c.setPacketHandled(true);
    }
}
