package com.example.gulp;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/** Network channel + the three packets: action (client->server), sync and belly (server->client). */
public final class Net {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Gulp.ID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private Net() { }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, ActionPacket.class, ActionPacket::encode, ActionPacket::decode,
                ActionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, SyncPacket.class, SyncPacket::encode, SyncPacket::decode,
                SyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, BellyPacket.class, BellyPacket::encode, BellyPacket::decode,
                BellyPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, SoundPacket.class, SoundPacket::encode, SoundPacket::decode,
                SoundPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, MessagePacket.class, MessagePacket::encode, MessagePacket::decode,
                MessagePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, LevelUpPacket.class, LevelUpPacket::encode, LevelUpPacket::decode,
                LevelUpPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, StorageViewPacket.class, StorageViewPacket::encode, StorageViewPacket::decode,
                StorageViewPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, HeldPacket.class, HeldPacket::encode, HeldPacket::decode,
                HeldPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, RolesPacket.class, RolesPacket::encode, RolesPacket::decode,
                RolesPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /** Client -> server: an action with no argument. */
    public static void send(int action) {
        send(action, 0);
    }

    /** Client -> server: an action with an argument (entry uid or perk index). */
    public static void send(int action, int arg) {
        CHANNEL.sendToServer(new ActionPacket(action, arg));
    }
}
