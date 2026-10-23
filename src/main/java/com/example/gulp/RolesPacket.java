package com.example.gulp;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Server -> everyone: the role of every player online, so you can see who is a Pred / Prey when you point at them. */
public class RolesPacket {
    public final Map<UUID, Integer> roles;

    public RolesPacket(Map<UUID, Integer> roles) {
        this.roles = roles;
    }

    /** Sends the full list to all players. Called when someone joins and whenever someone changes their role. */
    public static void broadcast(MinecraftServer server) {
        StomachManager mgr = StomachManager.get(server);
        Map<UUID, Integer> map = new HashMap<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            map.put(p.getUUID(), mgr.of(p.getUUID()).role.ordinal());
        }
        Net.CHANNEL.send(PacketDistributor.ALL.noArg(), new RolesPacket(map));
    }

    public static void encode(RolesPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.roles.size());
        m.roles.forEach((id, role) -> {
            buf.writeUUID(id);
            buf.writeByte(role);
        });
    }

    public static RolesPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        Map<UUID, Integer> map = new HashMap<>();
        for (int i = 0; i < n; i++) map.put(buf.readUUID(), (int) buf.readByte());
        return new RolesPacket(map);
    }

    public static void handle(RolesPacket m, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> ClientState.setRoles(m.roles));
        c.setPacketHandled(true);
    }
}
