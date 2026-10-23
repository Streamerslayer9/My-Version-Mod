package com.example.gulp;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Minecraft Comes Alive (MCA Reborn) reactions. Optional: if MCA isn't installed, none of this does anything.
 *
 * MCA is reached by name (reflection) instead of being a build dependency, so this mod builds and runs the same
 * with or without it, and if a future MCA update changes something, the worst case is that the reactions switch
 * themselves off (with one line in the log) instead of crashing anything.
 *
 * What happens:
 *  - Swallowing an MCA villager changes how that villager feels about you (MCA "hearts" + mood), and nearby villagers
 *    who saw it react too. Some villagers enjoy being carried SAFELY (see attitude()): they gain hearts and happiness.
 *  - You get a "predator" status that grows when you swallow/digest villagers and slowly fades with time.
 *  - While it is high, villagers who are afraid of you refuse to talk, trade or marry you. Villagers who dislike you
 *    personally refuse too while the status lasts. When it fades, everyone talks to you again.
 *  - Witnesses panic and run from you for a few seconds (MCA villagers don't talk while panicking).
 *  - When a villager is digested, MCA's own death rules also apply (it already makes witnesses lose hearts).
 */
public final class McaIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int WITNESS_RANGE = 16;
    private static final int MAX_WITNESSES = 12;
    private static final int MAX_PREDATOR = 200;

    private enum Attitude { FAN, NEUTRAL, FEARFUL }

    private static Boolean modLoaded;
    private static boolean broken; // MCA looked different from what we expected: stop trying
    private static Class<?> villagerClass;
    private static Method getVillagerBrain, getMemoriesForPlayer, getHearts, rewardHearts, getPersonality, isGuard;

    private record Scare(LivingEntity villager, UUID player, long until) { }

    private static final List<Scare> SCARED = new ArrayList<>();
    private static final Map<UUID, Long> LAST_REFUSAL_MESSAGE = new HashMap<>();

    private McaIntegration() { }

    // ------------------------------------------------------------------ finding MCA

    /** True if MCA is installed, working, and switched on in the server config. */
    public static boolean active() {
        if (!GulpServerConfig.MCA_ENABLED.get()) return false;
        if (modLoaded == null) modLoaded = ModList.get().isLoaded("mca");
        return modLoaded && !broken && ensureReflection();
    }

    /** A one-line chat message saying whether this mod is connected to MCA (shown on join; see mcaStatusMessage). */
    public static Component status() {
        if (!GulpServerConfig.MCA_ENABLED.get()) {
            return Component.literal("[Gulp] MCA: reactions are turned off in the server config.").withStyle(ChatFormatting.GRAY);
        }
        if (modLoaded == null) modLoaded = ModList.get().isLoaded("mca");
        if (!modLoaded) {
            return Component.literal("[Gulp] MCA: not found (Minecraft Comes Alive isn't installed).").withStyle(ChatFormatting.GRAY);
        }
        if (active()) {
            return Component.literal("[Gulp] MCA connected: villager reactions are on.").withStyle(ChatFormatting.GREEN);
        }
        return Component.literal("[Gulp] MCA found, but it didn't match what I expected, so reactions are off (see latest.log).")
                .withStyle(ChatFormatting.RED);
    }

    /**
     * Finds one of MCA's classes by its normal name (net.mca...). MCA's Forge build renames its classes with a
     * "forge." in front (forge.net.mca...), so that spelling is tried too. Used here and in McaSkins.
     */
    static Class<?> mcaClass(String name) throws ClassNotFoundException {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException notPlain) {
            return Class.forName("forge." + name);
        }
    }

    private static boolean ensureReflection() {
        if (villagerClass != null) return true;
        try {
            villagerClass = mcaClass("net.mca.entity.VillagerEntityMCA");
            getVillagerBrain = villagerClass.getMethod("getVillagerBrain");
            Class<?> brainClass = mcaClass("net.mca.entity.ai.brain.VillagerBrain");
            getMemoriesForPlayer = method(brainClass, "getMemoriesForPlayer", 1);
            rewardHearts = method(brainClass, "rewardHearts", 2);
            getPersonality = method(brainClass, "getPersonality", 0);
            getHearts = method(mcaClass("net.mca.entity.ai.Memories"), "getHearts", 0);
            try {
                isGuard = method(villagerClass, "isGuard", 0);
            } catch (NoSuchMethodException ignored) {
                isGuard = null; // optional
            }
            LOGGER.info("Vore Mod: MCA found, villager reactions are ready.");
            return true;
        } catch (Throwable t) {
            broken = true;
            villagerClass = null;
            LOGGER.warn("Vore Mod: MCA is installed but its code isn't what I expected, so the MCA reactions are switched off.", t);
            return false;
        }
    }

    /** Logs a problem with the full error, but only the first few times so a repeating failure can't flood the log. */
    private static int problemsLogged;

    private static void problem(String what, Throwable t) {
        if (problemsLogged++ < 5) LOGGER.warn("Vore Mod: MCA reaction problem: {}", what, t);
    }

    private static Method method(Class<?> c, String name, int parameters) throws NoSuchMethodException {
        for (Method m : c.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == parameters) return m;
        }
        throw new NoSuchMethodException(c.getName() + "." + name);
    }

    public static boolean isVillager(Entity e) {
        return e != null && villagerClass != null && villagerClass.isInstance(e);
    }

    // ------------------------------------------------------------------ reading a villager

    private static Object brainOf(Entity villager) throws ReflectiveOperationException {
        return getVillagerBrain.invoke(villager);
    }

    private static int heartsWith(Entity villager, Player player) {
        try {
            Object memories = getMemoriesForPlayer.invoke(brainOf(villager), player);
            return (Integer) getHearts.invoke(memories);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static String personalityOf(Entity villager) {
        try {
            Object personality = getPersonality.invoke(brainOf(villager));
            return personality instanceof Enum<?> e ? e.name() : "";
        } catch (Throwable t) {
            problem("couldn't read a villager's personality", t);
            return "";
        }
    }

    private static boolean guard(Entity villager) {
        try {
            return isGuard != null && (Boolean) isGuard.invoke(villager);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * How a villager feels about being swallowed, from their MCA personality:
     *  FAN      (peppy, confident, athletic, odd): they find being carried safely a thrill.
     *  FEARFUL  (sensitive, shy, gloomy, grumpy): they hate it.
     *  NEUTRAL  (everyone else): they don't like it, but they're not terrified.
     */
    private static Attitude attitude(Entity villager) {
        return switch (personalityOf(villager)) {
            case "PEPPY", "CONFIDENT", "ATHLETIC", "ODD" -> Attitude.FAN;
            case "SENSITIVE", "SHY", "GLOOMY", "GRUMPY" -> Attitude.FEARFUL;
            default -> Attitude.NEUTRAL;
        };
    }

    // ------------------------------------------------------------------ changing a villager

    /**
     * Changes how a villager feels about the player. MCA's own rewardHearts does the work: it changes the hearts,
     * moves their mood the same amount (so big losses make them "depressed", big gains "overjoyed"), shows the
     * happy/angry particles, and doubles the loss for sensitive villagers.
     */
    private static void reward(Entity villager, ServerPlayer player, int hearts) {
        int scaled = Math.round(hearts * GulpServerConfig.MCA_REACTION_MULTIPLIER.get().floatValue());
        if (scaled == 0) return;
        try {
            rewardHearts.invoke(brainOf(villager), player, scaled);
            LOGGER.info("Vore Mod: villager {} hearts {} for {}", villager.getName().getString(),
                    (scaled > 0 ? "+" : "") + scaled, player.getName().getString());
        } catch (Throwable t) {
            problem("couldn't change a villager's hearts", t);
        }
    }

    private static void addPredator(ServerPlayer p, Stomach s, int amount) {
        if (amount <= 0) return;
        int lock = GulpServerConfig.MCA_PREDATOR_LOCK.get();
        int before = s.predator;
        s.predator = Math.min(MAX_PREDATOR, s.predator + amount);
        if (before < lock && s.predator >= lock) {
            StomachLogic.say(p, Component.literal("Villagers are afraid of you now."));
        }
    }

    /** Makes a villager run from the player for a few seconds (not guards: they'd fight instead). */
    private static void scare(Entity villager, ServerPlayer player) {
        if (!GulpServerConfig.MCA_PANIC.get() || !(villager instanceof LivingEntity le) || guard(villager)) return;
        SCARED.add(new Scare(le, player.getUUID(), le.level().getGameTime() + 160)); // 8 seconds
    }

    private static void reactNearby(ServerPlayer p, Entity subject, boolean hard) {
        int count = 0;
        for (Entity w : p.level().getEntities(p, p.getBoundingBox().inflate(WITNESS_RANGE), e -> e != subject && isVillager(e))) {
            if (++count > MAX_WITNESSES) break;
            Attitude a = attitude(w);
            int hearts = hard
                    ? (a == Attitude.FAN ? -3 : a == Attitude.FEARFUL ? -15 : -10)   // they saw something terrifying
                    : (a == Attitude.FAN ? 5 : a == Attitude.FEARFUL ? -8 : -2);     // a safe ride: fun to some, upsetting to others
            reward(w, p, hearts);
            if (hard || a == Attitude.FEARFUL) scare(w, p);
        }
    }

    // ------------------------------------------------------------------ hooks called by the stomach

    /** A villager is about to be swallowed (it is still in the world, so what it feels is saved with it). */
    public static void onSwallow(ServerPlayer p, Stomach s, LivingEntity swallowed) {
        boolean on = active();
        if (!on || !isVillager(swallowed)) {
            // Help work out why nothing happened: say so when a villager-type mob is swallowed but we didn't react.
            if (swallowed.getType().toString().contains("villager")) {
                LOGGER.info("Vore Mod: swallowed {} ({}) but no MCA reaction: MCA reactions {}, is MCA villager: {}",
                        swallowed.getName().getString(), swallowed.getClass().getName(),
                        on ? "on" : "off (config off, MCA missing, or MCA didn't match)", isVillager(swallowed));
            }
            return;
        }
        boolean hard = s.hard && GulpServerConfig.HARD_MODE_ENABLED.get();
        Attitude a = attitude(swallowed);
        LOGGER.info("Vore Mod: MCA villager {} swallowed (personality {}, attitude {}, {} mode)",
                swallowed.getName().getString(), personalityOf(swallowed), a, hard ? "hard" : "soft");
        int hearts = hard
                ? (a == Attitude.FAN ? -10 : a == Attitude.FEARFUL ? -50 : -40)
                : (a == Attitude.FAN ? 15 : a == Attitude.FEARFUL ? -25 : -10);
        reward(swallowed, p, hearts);
        int predator = hard
                ? (a == Attitude.FAN ? 8 : a == Attitude.FEARFUL ? 20 : 15)
                : (a == Attitude.FAN ? 0 : a == Attitude.FEARFUL ? 5 : 3);
        addPredator(p, s, predator);
        reactNearby(p, swallowed, hard);
    }

    /** A villager is let out. The ones who enjoy it get happier the longer the ride was. */
    public static void onRelease(ServerPlayer p, Stomach s, Entity released, int heldTicks) {
        if (!active() || !isVillager(released)) return;
        Attitude a = attitude(released);
        if (a == Attitude.FAN) {
            reward(released, p, Math.min(25, 5 + heldTicks / 200)); // +5, then +1 for every 10 seconds, up to +25
        } else if (!s.hard) {
            reward(released, p, a == Attitude.FEARFUL ? 3 : 5);     // relief at being let out safely
        }
    }

    /** A villager is being digested. MCA's own death rules apply too; this adds the predator status and panic. */
    public static void onDigest(ServerPlayer p, Stomach s, LivingEntity digested) {
        if (!active() || !isVillager(digested)) return;
        Attitude a = attitude(digested);
        addPredator(p, s, a == Attitude.FAN ? 10 : a == Attitude.FEARFUL ? 35 : 25);
        for (Entity w : p.level().getEntities(p, p.getBoundingBox().inflate(WITNESS_RANGE), e -> e != digested && isVillager(e))) {
            scare(w, p);
        }
    }

    // ------------------------------------------------------------------ talking, trading and marrying

    /** @return why this villager refuses to deal with the player right now, or null if they're fine with it */
    public static Component refusal(ServerPlayer p, Entity target) {
        if (!active() || !GulpServerConfig.MCA_LOCKOUT_ENABLED.get() || !isVillager(target)) return null;
        Stomach s = StomachManager.get(p.getServer()).of(p.getUUID());
        if (s.predator <= 0) return null;
        if (s.predator >= GulpServerConfig.MCA_PREDATOR_LOCK.get() && attitude(target) != Attitude.FAN) {
            return Component.empty().append(target.getName()).append(" is too afraid of you to talk.");
        }
        if (heartsWith(target, p) <= GulpServerConfig.MCA_HEARTS_LOCK.get()) {
            return Component.empty().append(target.getName()).append(" wants nothing to do with you right now.");
        }
        return null;
    }

    /** Stops the "refuses to talk" message from repeating on every click. */
    public static boolean shouldTell(ServerPlayer p) {
        long now = System.currentTimeMillis();
        Long last = LAST_REFUSAL_MESSAGE.put(p.getUUID(), now);
        return last == null || now - last > 1500;
    }

    // ------------------------------------------------------------------ every 10 ticks: keep scared villagers running

    public static void tickScared(MinecraftServer server) {
        if (SCARED.isEmpty()) return;
        Iterator<Scare> it = SCARED.iterator();
        while (it.hasNext()) {
            Scare s = it.next();
            LivingEntity v = s.villager();
            if (v.isRemoved() || !v.isAlive() || v.level().getGameTime() > s.until()) {
                it.remove();
                continue;
            }
            try {
                ServerPlayer p = server.getPlayerList().getPlayer(s.player());
                Brain<?> brain = v.getBrain();
                if (p != null) brain.setMemoryWithExpiry(MemoryModuleType.HURT_BY_ENTITY, p, 40L); // what to run from
                brain.setActiveActivityIfPossible(Activity.PANIC);
            } catch (Throwable t) {
                it.remove();
            }
        }
    }
}
