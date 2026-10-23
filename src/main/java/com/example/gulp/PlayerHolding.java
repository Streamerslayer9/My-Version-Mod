package com.example.gulp;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.level.GameType;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Swallowing other players. Built to be safe in multiplayer:
 *  - roles: only a Prey or Both player can be swallowed, and only a Pred or Both player can swallow (see Role),
 *  - nobody is swallowed straight away: they get an Accept / Decline request first (see VoreRequests),
 *  - soft mode: swallowed players are only held, never harmed. They can leave any time with the Release key and rub the
 *    walls (Struggle key) to give the swallower food and buffs. They are let out automatically after a while,
 *  - hard mode: a digestion timer runs; the Release key doesn't work, the Struggle key rolls an escape chance, and when
 *    the timer ends they are let out weakened (or die, if the server turned playerDigestionKills on),
 *  - in both modes they are let out when the swallower dies, logs out, changes dimension, or the server stops,
 *  - potions the swallower drinks reach them, and they sleep through the night with the swallower,
 *  - server admins can switch the whole thing off (playerSwallowingEnabled in gulp-server.toml).
 *
 * How it works: the swallowed player is put in spectator mode looking through the swallower's eyes (so they
 * can't interact with anything), and their previous game mode is remembered, including in their saved data so a
 * crash can never leave anyone stuck in spectator mode.
 */
public final class PlayerHolding {
    private static final String KEY_PREV_MODE = "gulp_prev_mode";

    /** One swallowed player. `hard` is decided when they are swallowed and never changes while they are inside. */
    private static final class Held {
        private final UUID swallower;
        private final int previousMode;
        final boolean hard;
        int struggleCd; // ticks until they can struggle / rub again
        int rubCd;      // ticks until they can rub the walls again (soft mode)

        Held(UUID swallower, int previousMode, boolean hard) {
            this.swallower = swallower;
            this.previousMode = previousMode;
            this.hard = hard;
        }

        UUID swallower() { return swallower; }
        int previousMode() { return previousMode; }
    }

    /** True if a player swallowed by this stomach right now would be digested (hard mode). */
    public static boolean hardVore(Stomach s) {
        return s.hard && GulpServerConfig.HARD_MODE_ENABLED.get() && GulpServerConfig.PLAYER_HARD_VORE_ENABLED.get();
    }

    /** victim -> who is holding them. Only lives while the server runs. */
    private static final Map<UUID, Held> HELD = new HashMap<>();

    private PlayerHolding() { }

    public static boolean isHeld(ServerPlayer p) { return HELD.containsKey(p.getUUID()); }

    public static boolean isPlayerEntry(CompoundTag entry) { return entry.contains("PlayerUuid"); }

    // ------------------------------------------------------------------ swallowing

    private static float sizeOf(ServerPlayer victim) {
        return Math.max(0.05f, victim.getBbWidth() * victim.getBbWidth() * victim.getBbHeight());
    }

    /**
     * Why `victim` can't be swallowed by `p` right now (a message for p), or null if they can.
     * Checked when the request is made AND again when the target accepts.
     */
    static Component blocker(ServerPlayer p, Stomach s, ServerPlayer victim, StomachManager mgr) {
        if (!GulpServerConfig.PLAYER_SWALLOWING_ENABLED.get()) {
            return Component.literal("Swallowing players is turned off on this server.");
        }
        if (!s.role.canEat()) return s.role.cantSwallowMessage();
        if (!victim.isAlive() || victim.isSpectator() || isHeld(victim)) {
            return Component.literal("Nothing swallowable in reach.");
        }
        Role victimRole = mgr.of(victim.getUUID()).role;
        if (!victimRole.canBeEaten()) return victimRole.cantBeSwallowedMessage();
        float size = sizeOf(victim);
        if (size > s.capacity()) {
            return Component.literal("Too big for your stomach! (needs " + Stomach.fmtVolume(size)
                    + ", capacity " + Stomach.fmtVolume(s.capacity()) + ")");
        }
        if (s.used() + size > s.capacity()) return Component.literal("Your stomach is too full.");
        if (s.contents.size() >= Stomach.MAX_MOBS) {
            return Component.literal("You can't hold more than " + Stomach.MAX_MOBS + " creatures at once.");
        }
        return null;
    }

    /** The swallow key was pressed on a player: after the checks, they get an Accept / Decline request. Nothing is swallowed yet. */
    public static void request(ServerPlayer p, Stomach s, ServerPlayer victim, StomachManager mgr) {
        Component why = blocker(p, s, victim, mgr);
        if (why != null) {
            StomachLogic.say(p, why);
            return;
        }
        VoreRequests.send(p, victim);
    }

    /**
     * Actually swallows the player. Only called once they accepted a request (see VoreRequests.respond).
     * @return true if the player was swallowed (the caller then adds the cooldown, sound and message)
     */
    public static boolean swallow(ServerPlayer p, Stomach s, ServerPlayer victim, StomachManager mgr) {
        Component why = blocker(p, s, victim, mgr);
        if (why != null) {
            StomachLogic.say(p, why);
            return false;
        }
        float size = sizeOf(victim);
        boolean hard = hardVore(s);

        // The entry looks like a mob entry so the stomach screen can list it, but it has no saved entity:
        // it points at the real player instead.
        CompoundTag entry = new CompoundTag();
        CompoundTag display = new CompoundTag();
        display.putString("id", "minecraft:player");
        display.putFloat("Health", victim.getHealth());
        entry.put("Entity", display);
        entry.putString("PlayerUuid", victim.getUUID().toString());
        entry.putFloat("Size", size);
        entry.putFloat("MaxHealth", victim.getMaxHealth());
        entry.putInt("Digest", 0);
        entry.putInt("DigestTime", hard ? GulpServerConfig.PLAYER_DIGEST_SECONDS.get() * 20 : 0);
        entry.putBoolean("HardVore", hard);
        Component name = victim.getName();
        entry.putString("Name", name.getString());
        entry.putString("NameJson", Component.Serializer.toJson(name));
        entry.putInt("Uid", p.getRandom().nextInt());
        entry.putInt("HeldTicks", 0);
        s.contents.add(entry);

        int previous = victim.gameMode.getGameModeForPlayer().getId();
        Held held = new Held(p.getUUID(), previous, hard);
        HELD.put(victim.getUUID(), held);
        victim.getPersistentData().putInt(KEY_PREV_MODE, previous); // survives a crash
        victim.setGameMode(GameType.SPECTATOR);
        victim.setCamera(p);
        victim.sendSystemMessage(Component.literal("You were swallowed by ").append(p.getName()).append(hard
                ? " (HARD mode). The Release key won't work: press your Struggle key (default R) to try to break free before you're digested."
                : ". Press your Release key (default G) to get out whenever you like, or your Struggle key (default R) to rub the walls."));
        sendHeldState(victim, held, p, 0f);
        return true;
    }

    // ------------------------------------------------------------------ letting them out

    /** Lets a swallowed player out of the swallower's stomach. The caller removes the entry from the list. */
    public static boolean releaseEntry(ServerPlayer swallower, CompoundTag entry) {
        UUID id = victimId(entry);
        if (id == null) return true;
        Held held = HELD.remove(id);
        ServerPlayer victim = swallower.getServer().getPlayerList().getPlayer(id);
        if (victim != null) restore(victim, held, swallower);
        return true;
    }

    /** The swallowed player pressed Release. In soft mode they get out; in hard mode they have to struggle instead. */
    public static void tryRelease(ServerPlayer victim) {
        Held held = HELD.get(victim.getUUID());
        if (held == null) return;
        if (held.hard) {
            StomachLogic.say(victim, Component.literal("Hard mode: you can't just leave. Struggle to try to break free!"));
            return;
        }
        escape(victim);
    }

    /** Gets a swallowed player out, whatever the swallower is doing (struggle success, soft-mode Release, safety releases). */
    public static void escape(ServerPlayer victim) {
        Held held = HELD.get(victim.getUUID());
        if (held == null) return;
        MinecraftServer server = victim.getServer();
        ServerPlayer holder = server.getPlayerList().getPlayer(held.swallower());
        StomachManager mgr = StomachManager.get(server);
        Stomach hs = mgr.of(held.swallower());
        String id = victim.getUUID().toString();
        hs.contents.removeIf(e -> isPlayerEntry(e) && id.equals(e.getString("PlayerUuid")));
        HELD.remove(victim.getUUID());
        restore(victim, held, holder);
        mgr.setDirty();
        if (holder != null) {
            StomachLogic.say(holder, Component.empty().append(victim.getName()).append(" got out."));
            StomachLogic.sync(holder, hs);
            StomachLogic.sound(holder, GulpSound.SPIT);
        }
    }

    /** Called every tick for every player: keeps a swallowed player inside, and lets them out if something went wrong. */
    public static void tickHeld(ServerPlayer victim) {
        Held held = HELD.get(victim.getUUID());
        if (held == null) return;
        if (held.struggleCd > 0) held.struggleCd--;
        if (held.rubCd > 0) held.rubCd--;
        ServerPlayer holder = victim.getServer().getPlayerList().getPlayer(held.swallower());
        if (holder == null || !holder.isAlive() || holder.isSpectator()) {
            escape(victim);
            return;
        }
        // Pressing sneak makes a spectator stop looking through someone else's eyes; put them straight back.
        if (victim.getCamera() != holder) victim.setCamera(holder);
    }

    /**
     * Called every tick for the swallower, for each swallowed player they hold.
     * @return false if this entry should be removed (the player is gone or their time ran out)
     */
    public static boolean tickEntry(ServerPlayer swallower, CompoundTag entry) {
        UUID id = victimId(entry);
        Held held = id == null ? null : HELD.get(id);
        if (held == null || !held.swallower().equals(swallower.getUUID())) return false; // stale entry
        int ticks = entry.getInt("HeldTicks") + 1;
        entry.putInt("HeldTicks", ticks);
        ServerPlayer victim = swallower.getServer().getPlayerList().getPlayer(id);

        if (held.hard) {
            // Hard mode: the digestion timer runs. No hold limit, the timer decides. Struggling is the way out.
            int d = entry.getInt("Digest") + 1;
            entry.putInt("Digest", d);
            int total = Math.max(1, entry.getInt("DigestTime"));
            if (d >= total) {
                finishDigestion(swallower, entry, victim);
                return false;
            }
            if (ticks % 20 == 0 && victim != null) {
                entry.getCompound("Entity").putFloat("Health", victim.getHealth());
                sendHeldState(victim, held, swallower, d / (float) total);
            }
            return true;
        }

        if (ticks > GulpServerConfig.PLAYER_MAX_HOLD_SECONDS.get() * 20) {
            if (victim != null) victim.sendSystemMessage(Component.literal("You were let out."));
            releaseEntry(swallower, entry);
            return false;
        }
        if (ticks % 20 == 0 && victim != null) entry.getCompound("Entity").putFloat("Health", victim.getHealth());
        return true;
    }

    /** The hard-mode timer ran out. By default they are let out weakened; with playerDigestionKills they die. */
    private static void finishDigestion(ServerPlayer swallower, CompoundTag entry, ServerPlayer victim) {
        Stomach s = StomachManager.get(swallower.getServer()).of(swallower.getUUID());
        Component name = victim != null ? victim.getName() : StomachLogic.entryName(entry);
        releaseEntry(swallower, entry); // gives them their game mode back and puts them down in front of the swallower
        if (victim != null) {
            if (GulpServerConfig.PLAYER_DIGESTION_KILLS.get()) {
                victim.sendSystemMessage(Component.literal("You were digested."));
                victim.hurt(victim.damageSources().genericKill(), Float.MAX_VALUE);
            } else {
                victim.sendSystemMessage(Component.literal("Their stomach is done with you. You were let out, weak and wobbly."));
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 1));
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 300, 0));
            }
        }
        StomachLogic.gainXp(swallower, s, 100f);
        swallower.getFoodData().eat(4, 0.8f);
        StomachLogic.say(swallower, Component.literal("Finished digesting ").append(name));
        StomachLogic.sound(swallower, GulpSound.DIGEST);
    }

    // ------------------------------------------------------------------ struggling, wall rubbing, potions, sleep

    /** Percent chance that one struggle frees the player: the configured chance, falling to half of it as digestion nears 100%. */
    private static float escapeChance(float progress) {
        float base = GulpServerConfig.PLAYER_STRUGGLE_CHANCE.get().floatValue();
        return base * (1f - 0.5f * Math.max(0f, Math.min(1f, progress)));
    }

    private static CompoundTag findEntry(Stomach s, UUID victimId) {
        String id = victimId.toString();
        for (CompoundTag e : s.contents) {
            if (isPlayerEntry(e) && id.equals(e.getString("PlayerUuid"))) return e;
        }
        return null;
    }

    private static void sendHeldState(ServerPlayer victim, Held held, ServerPlayer host, float progress) {
        Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> victim),
                new HeldPacket(held.hard ? 2 : 1, host.getName().getString(), progress, held.hard ? escapeChance(progress) : 0f));
    }

    /** A swallowed player asks to look at the storage of whoever holds them (read-only snapshot). */
    public static void sendSharedStorage(ServerPlayer victim) {
        Held held = HELD.get(victim.getUUID());
        if (held == null) return;
        if (!GulpServerConfig.ITEM_STORAGE_ENABLED.get() || !GulpServerConfig.SHARED_STORAGE_VIEW_ENABLED.get()) {
            StomachLogic.say(victim, Component.literal("Looking at their stomach storage is turned off on this server."));
            return;
        }
        MinecraftServer server = victim.getServer();
        ServerPlayer host = server.getPlayerList().getPlayer(held.swallower());
        if (host == null) return;
        Stomach hs = StomachManager.get(server).of(held.swallower());
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < hs.storageSlots(); i++) items.add(hs.storage.get(i).copy());
        Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> victim), new StorageViewPacket(host.getName().getString(), items));
    }

    private static void sendHeldNone(ServerPlayer victim) {
        Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> victim), new HeldPacket(0, "", 0f, 0f));
    }

    private static Component bracketed(ServerPlayer p, String rest) {
        return Component.literal("[").append(p.getName()).append("] " + rest);
    }

    /**
     * The Struggle key. Hard mode: a roll against the escape chance (at most once a second); the swallower sees
     * "[Name] squirms in your gut" when it fails. Soft mode: it is a wall rub instead.
     */
    public static void struggle(ServerPlayer victim) {
        Held held = HELD.get(victim.getUUID());
        if (held == null || held.struggleCd > 0) return;
        held.struggleCd = 20;
        MinecraftServer server = victim.getServer();
        ServerPlayer host = server.getPlayerList().getPlayer(held.swallower());
        if (host == null) return;
        if (!held.hard) {
            wallRub(victim, held, host);
            return;
        }
        CompoundTag entry = findEntry(StomachManager.get(server).of(held.swallower()), victim.getUUID());
        float progress = entry == null ? 0f : entry.getInt("Digest") / (float) Math.max(1, entry.getInt("DigestTime"));
        float chance = escapeChance(progress);
        if (victim.getRandom().nextFloat() * 100f < chance) {
            StomachLogic.say(host, bracketed(victim, "broke free!"));
            escape(victim);
        } else {
            StomachLogic.say(host, bracketed(victim, "squirms in your gut"));
            StomachLogic.say(victim, Component.literal(String.format("You struggle, but can't break free (%.0f%% chance)", chance)));
            StomachLogic.sound(host, GulpSound.DIGEST);
        }
    }

    /** Soft mode: rubbing the walls makes the swallower feel good: food, saturation, regeneration and a little speed. */
    private static void wallRub(ServerPlayer victim, Held held, ServerPlayer host) {
        if (held.rubCd > 0) {
            StomachLogic.say(victim, Component.literal("Give it a moment before you rub again."));
            return;
        }
        held.rubCd = GulpServerConfig.PLAYER_WALL_RUB_COOLDOWN_SECONDS.get() * 20;
        host.getFoodData().eat(2, 0.5f);
        host.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 0));
        host.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0));
        StomachLogic.say(host, bracketed(victim, "rubs your stomach walls. You feel great."));
        StomachLogic.say(victim, Component.literal("You rub the walls. ").append(host.getName()).append(" looks pleased."));
        StomachLogic.sound(host, GulpSound.DIGEST);
    }

    /**
     * The swallower drank a potion: its effects pass to everyone inside. Instant damage and wither are never passed on
     * (they could kill), and other harmful effects only reach players held in hard mode.
     */
    public static void transferPotion(ServerPlayer host, ItemStack potion) {
        List<MobEffectInstance> effects = PotionUtils.getMobEffects(potion);
        if (effects.isEmpty()) return;
        Stomach s = StomachManager.get(host.getServer()).of(host.getUUID());
        for (CompoundTag entry : s.contents) {
            if (!isPlayerEntry(entry)) continue;
            UUID id = victimId(entry);
            Held held = id == null ? null : HELD.get(id);
            ServerPlayer victim = id == null ? null : host.getServer().getPlayerList().getPlayer(id);
            if (held == null || victim == null) continue;
            boolean any = false;
            for (MobEffectInstance inst : effects) {
                MobEffect fx = inst.getEffect();
                if (fx == MobEffects.HARM || fx == MobEffects.WITHER) continue;
                if (fx.getCategory() == MobEffectCategory.HARMFUL && !held.hard) continue;
                if (fx.isInstantenous()) {
                    fx.applyInstantenousEffect(host, host, victim, inst.getAmplifier(), 1.0);
                } else {
                    victim.addEffect(new MobEffectInstance(inst));
                }
                any = true;
            }
            if (any) {
                victim.sendSystemMessage(Component.empty().append(host.getName())
                        .append(" drank a potion. You feel its effects too."));
            }
        }
    }

    /**
     * The swallower slept through the night (time skipped while they were in bed). Players inside don't count towards
     * the "everyone must sleep" check (they're spectators), so they never hold the night up. They sleep it through too:
     * their rest timer resets (no phantoms) and they get a message.
     */
    public static void sleepThroughNight(ServerPlayer host) {
        Stomach s = StomachManager.get(host.getServer()).of(host.getUUID());
        for (CompoundTag entry : s.contents) {
            if (!isPlayerEntry(entry)) continue;
            UUID id = victimId(entry);
            ServerPlayer victim = id == null || !HELD.containsKey(id) ? null : host.getServer().getPlayerList().getPlayer(id);
            if (victim == null) continue;
            victim.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
            victim.sendSystemMessage(Component.literal("You slept through the night inside ").append(host.getName())
                    .append("'s stomach."));
            StomachLogic.say(host, bracketed(victim, "slept soundly in your gut"));
        }
    }

    // ------------------------------------------------------------------ joining, leaving, dimension changes, shutdown

    /** Swallower logs out or changes dimension: let everyone they hold out first. */
    public static void releaseAllHeldBy(ServerPlayer swallower) {
        StomachManager mgr = StomachManager.get(swallower.getServer());
        Stomach s = mgr.of(swallower.getUUID());
        boolean any = s.contents.removeIf(e -> isPlayerEntry(e) && releaseEntry(swallower, e));
        if (any) {
            mgr.setDirty();
            StomachLogic.sync(swallower, s);
        }
    }

    /** A swallowed player logs out: put their game mode back BEFORE the game saves them. */
    public static void onLogout(ServerPlayer p) {
        if (isHeld(p)) escape(p);
        releaseAllHeldBy(p);
    }

    public static void onLogin(ServerPlayer p) {
        StomachManager mgr = StomachManager.get(p.getServer());
        Stomach s = mgr.of(p.getUUID());
        // Entries left over from a crash (nobody is really being held any more).
        s.contents.removeIf(e -> {
            if (!isPlayerEntry(e)) return false;
            UUID id = victimId(e);
            return id == null || !HELD.containsKey(id);
        });
        // A player who was swallowed when the server crashed would still be in spectator mode.
        if (!isHeld(p) && p.getPersistentData().contains(KEY_PREV_MODE)) restore(p, null, null);
    }

    public static void onServerStopping(MinecraftServer server) {
        for (UUID id : new ArrayList<>(HELD.keySet())) {
            ServerPlayer victim = server.getPlayerList().getPlayer(id);
            if (victim != null) escape(victim);
        }
        HELD.clear();
    }

    // ------------------------------------------------------------------ helpers

    private static UUID victimId(CompoundTag entry) {
        try {
            return UUID.fromString(entry.getString("PlayerUuid"));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Gives the player their game mode back and (if there's a swallower) puts them down in front of them. */
    private static void restore(ServerPlayer victim, Held held, ServerPlayer anchor) {
        int modeId = held != null ? held.previousMode() : victim.getPersistentData().getInt(KEY_PREV_MODE);
        GameType mode = GameType.byId(modeId);
        if (mode == GameType.SPECTATOR) mode = GameType.SURVIVAL; // never leave anyone stuck as a spectator
        victim.setCamera(victim);
        victim.setGameMode(mode);
        victim.getPersistentData().remove(KEY_PREV_MODE);
        if (anchor != null) {
            ServerLevel w = anchor.serverLevel();
            Vec3 fwd = anchor.getViewVector(1.0f).scale(1.5);
            double x = anchor.getX() + fwd.x;
            double y = anchor.getY();
            double z = anchor.getZ() + fwd.z;
            AABB box = victim.getBoundingBox().move(x - victim.getX(), y - victim.getY(), z - victim.getZ());
            if (!w.noCollision(box)) { // no room in front of them: use the spot they're standing on
                x = anchor.getX();
                y = anchor.getY();
                z = anchor.getZ();
            }
            victim.teleportTo(w, x, y, z, anchor.getYRot() + 180f, 0f);
        }
        sendHeldNone(victim);
        victim.sendSystemMessage(Component.literal("You are out."));
    }
}
