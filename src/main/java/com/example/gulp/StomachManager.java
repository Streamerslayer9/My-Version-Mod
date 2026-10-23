package com.example.gulp;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Saves every player's stomach with the world, so swallowed villagers survive restarts. */
public class StomachManager extends SavedData {
    private final Map<UUID, Stomach> map = new HashMap<>();

    public static StomachManager get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(StomachManager::load, StomachManager::new, "gulp_stomachs");
    }

    public Stomach of(UUID id) {
        return map.computeIfAbsent(id, k -> new Stomach());
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        CompoundTag players = new CompoundTag();
        map.forEach((id, stomach) -> players.put(id.toString(), stomach.toNbt()));
        nbt.put("Players", players);
        return nbt;
    }

    public static StomachManager load(CompoundTag nbt) {
        StomachManager m = new StomachManager();
        CompoundTag players = nbt.getCompound("Players");
        for (String key : players.getAllKeys()) {
            m.map.put(UUID.fromString(key), Stomach.fromNbt(players.getCompound(key)));
        }
        return m;
    }
}
