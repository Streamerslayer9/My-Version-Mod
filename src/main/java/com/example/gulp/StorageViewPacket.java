package com.example.gulp;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server -> a swallowed player: a read-only snapshot of the swallower's item storage. */
public class StorageViewPacket {
    public final String host;
    public final List<ItemStack> items;

    public StorageViewPacket(String host, List<ItemStack> items) {
        this.host = host;
        this.items = items;
    }

    public static void encode(StorageViewPacket m, FriendlyByteBuf buf) {
        buf.writeUtf(m.host);
        buf.writeVarInt(m.items.size());
        for (ItemStack stack : m.items) buf.writeItem(stack);
    }

    public static StorageViewPacket decode(FriendlyByteBuf buf) {
        String host = buf.readUtf();
        int n = buf.readVarInt();
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < n; i++) items.add(buf.readItem());
        return new StorageViewPacket(host, items);
    }

    public static void handle(StorageViewPacket m, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SharedStorageScreen.show(m.host, m.items)));
        c.setPacketHandled(true);
    }
}
