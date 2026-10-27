package com.example.gulp;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** What the client currently knows. Plain Java only (no client-only classes), so it's safe to load anywhere. */
public final class ClientState {
    private ClientState() { }

    /** One swallowed mob as the server describes it. */
    public record EntryInfo(int uid, Component name, String typeId, float size, float hp, float maxHp, float digest) { }

    private static boolean hard;
    private static boolean instantDigest;
    // While swallowed: 0 = not, 1 = soft, 2 = hard, plus who holds you, how digested you are and your escape chance.
    private static int heldMode;
    private static String heldHolder = "";
    private static float heldProgress;
    private static float heldChance;
    private static Role role = Role.PRED;
    /** Everyone's role, from the server (for the "point at a player" badge). */
    private static final java.util.Map<java.util.UUID, Role> roles = new java.util.HashMap<>();
    private static int predator;
    private static float itemFill;
    private static int digestProgress;
    private static net.minecraft.world.item.ItemStack strip0 = net.minecraft.world.item.ItemStack.EMPTY;
    private static net.minecraft.world.item.ItemStack strip1 = net.minecraft.world.item.ItemStack.EMPTY;
    private static int level = 1, xp, xpNeeded = 100, points;
    private static float used, cap = 1.75f;
    private static int[] ranks = new int[Perk.values().length];
    private static List<EntryInfo> entries = new ArrayList<>();

    public static void apply(SyncPacket m) {
        hard = m.hard;
        instantDigest = m.instantDigest;
        level = m.level;
        xp = m.xp;
        xpNeeded = m.xpNeeded;
        used = m.used;
        cap = m.cap;
        points = m.points;
        role = Role.byId(m.role) != null ? Role.byId(m.role) : Role.PRED;
        predator = m.predator;
        itemFill = m.itemFill;
        digestProgress = m.digestProgress;
        strip0 = m.strip0;
        strip1 = m.strip1;
        ranks = m.ranks;
        entries = m.entries;
    }

    /** Set by client setup; shows the level-up chat message if the player has it turned on. */
    public static java.util.function.BiConsumer<Integer, Integer> levelUpListener;

    public static void fireLevelUp(int level, int points) {
        if (levelUpListener != null) levelUpListener.accept(level, points);
    }

    /** Shows an action-bar message on this client (unless the player turned them off). Set by client setup. */
    public static java.util.function.Consumer<Component> messageListener;

    public static void fireMessage(Component text) {
        if (messageListener != null) messageListener.accept(text);
    }

    /** Plays a Gulp sound on this client. Set by client setup (see ClientSounds). */
    public interface SoundListener { void play(int type, double x, double y, double z); }

    public static SoundListener soundListener;

    public static void firePlaySound(int type, double x, double y, double z) {
        if (soundListener != null) soundListener.play(type, x, y, z);
    }

    /** Forget everything (called when you leave a world/server so nothing carries over to the next one). */
    public static void reset() {
        hard = false;
        instantDigest = false;
        level = 1;
        xp = 0;
        xpNeeded = Stomach.xpForNext(1);
        points = 0;
        role = Role.PRED;
        roles.clear();
        heldMode = 0;
        predator = 0;
        itemFill = 0f;
        digestProgress = 0;
        strip0 = net.minecraft.world.item.ItemStack.EMPTY;
        strip1 = net.minecraft.world.item.ItemStack.EMPTY;
        used = 0f;
        cap = 1.75f;
        ranks = new int[Perk.values().length];
        entries = new ArrayList<>();
        BELLY_TARGET.clear();
        BELLY_SHOWN.clear();
        BELLY_TIME.clear();
    }

    public static boolean isHard() { return hard; }
    public static boolean isInstantDigest() { return instantDigest; }
    public static Role role() { return role; }

    public static void setHeld(int mode, String holder, float progress, float chance) {
        heldMode = mode;
        heldHolder = holder;
        heldProgress = progress;
        heldChance = chance;
    }
    public static int heldMode() { return heldMode; }
    public static String heldHolder() { return heldHolder; }
    public static float heldProgress() { return heldProgress; }
    public static float heldChance() { return heldChance; }

    /** Another player's role, or null if the server hasn't told us. */
    public static Role roleOf(java.util.UUID id) { return roles.get(id); }

    public static void setRoles(java.util.Map<java.util.UUID, Integer> map) {
        roles.clear();
        map.forEach((id, r) -> {
            Role role = Role.byId(r);
            if (role != null) roles.put(id, role);
        });
    }
    public static int predator() { return predator; }
    public static float itemFill() { return itemFill; }
    public static int digestProgress() { return digestProgress; }

    /** The Enchant Stripper boxes: 0 = source, 1 = target. */
    public static net.minecraft.world.item.ItemStack stripSlot(int i) { return i == 0 ? strip0 : strip1; }
    public static int level() { return level; }
    public static int xp() { return xp; }
    public static int xpNeeded() { return xpNeeded; }
    public static int points() { return points; }
    public static float used() { return used; }
    public static float cap() { return cap; }
    public static int rank(Perk p) { return ranks[p.ordinal()]; }
    public static List<EntryInfo> entries() { return Collections.unmodifiableList(entries); }

    /** Changes whenever the screens need to rebuild their buttons. */
    public static String signature() {
        StringBuilder sb = new StringBuilder();
        sb.append(hard).append(instantDigest).append('|').append(points).append('|').append(Arrays.toString(ranks)).append('|');
        for (EntryInfo e : entries) sb.append(e.uid()).append(',');
        return sb.toString();
    }

    // Belly fullness (0..1) for every player we can see, keyed by UUID.
    // Custom player models can read this too: ClientState.bellyAmount(uuid)
    private static final Map<UUID, Float> BELLY_TARGET = new HashMap<>();
    private static final Map<UUID, Float> BELLY_SHOWN = new HashMap<>();
    private static final Map<UUID, Long> BELLY_TIME = new HashMap<>();

    public static void setBelly(UUID id, float ratio) { BELLY_TARGET.put(id, ratio); }

    public static float bellyAmount(UUID id) { return BELLY_TARGET.getOrDefault(id, 0f); }

    /** Eases the displayed belly toward the real value so it grows/shrinks smoothly. */
    public static float smoothedBelly(UUID id) {
        float target = bellyAmount(id);
        float cur = BELLY_SHOWN.getOrDefault(id, 0f);
        // Ease toward the real value over about a third of a second, the same at any frame rate.
        long now = System.nanoTime();
        Long last = BELLY_TIME.put(id, now);
        float seconds = last == null ? 0.016f : Math.min(0.25f, (now - last) / 1.0e9f);
        cur += (target - cur) * (1f - (float) Math.exp(-seconds / 0.35f));
        if (Math.abs(target - cur) < 0.002f) cur = target;
        BELLY_SHOWN.put(id, cur);
        return cur;
    }
}
