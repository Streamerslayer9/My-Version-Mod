package com.example.gulp;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: a short "what just happened" message for the action bar (Gulp! Swallowed Cow, Too big for your
 * stomach, ...). It goes through the mod instead of straight to the screen so each player can turn them off
 * in Settings, without hiding anyone else's action bar messages.
 */
public class MessagePacket {
    private final Component text;

    public MessagePacket(Component text) {
        this.text = text;
    }

    public static void encode(MessagePacket m, FriendlyByteBuf buf) {
        buf.writeComponent(m.text);
    }

    public static MessagePacket decode(FriendlyByteBuf buf) {
        return new MessagePacket(buf.readComponent());
    }

    public static void handle(MessagePacket m, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> ClientState.fireMessage(m.text));
        c.setPacketHandled(true);
    }
}
