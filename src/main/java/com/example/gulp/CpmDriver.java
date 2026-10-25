package com.example.gulp;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Drives the belly "sliders" of your Custom Player Model (CPM), from this player's own stomach.
 *
 * The model's author adds animations to the model in the CPM editor (value layers, so they can sit anywhere
 * between "empty" and "full") and names them:
 *   belly        how full you are overall (creatures, plus items counting half as much)
 *   belly_mobs   how full of creatures you are (their volume compared to your capacity)
 *   belly_items  how full your item storage is
 * Each is set to a value between 0 and the maximum the model allows for that animation (we read the maximum from the
 * model, so any range works). An animation the model doesn't have is simply skipped. The names can be changed in
 * config/gulp-client.properties. Values ease toward their target so the belly grows and shrinks smoothly.
 * CPM then shows it to everyone, the same way it shares any other animation.
 *
 * CPM matches animation names exactly (capital letters count, "Belly" is not "belly") and its API can't list a model's
 * animations, so for each slider this tries the configured name in a few spellings (belly, Belly, BELLY, Belly_Mobs,
 * "Belly Mobs" ...) plus a few common alternative names (stomach, stomach_scale ...). No model files or folders are
 * involved: whatever model the player is wearing is checked again whenever it stops answering.
 */
public final class CpmDriver {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Other names a model author might reasonably have used, tried after the configured name. */
    private static final String[] BELLY_ALIASES = {"belly", "stomach", "stomach_scale", "belly_scale", "tummy"};
    private static final String[] MOBS_ALIASES = {"belly_mobs", "stomach_mobs"};
    private static final String[] ITEMS_ALIASES = {"belly_items", "stomach_items"};

    private static float shownMobs;
    private static float shownItems;
    private static boolean localModelHasBelly;
    private static int ticks;
    /** Last value sent, keyed by the real animation name in the model. */
    private static final Map<String, Integer> LAST_SENT = new HashMap<>();
    /** Which real animation name each slider (by its configured name) resolved to, so we don't search every tick. */
    private static final Map<String, String> RESOLVED = new HashMap<>();

    private CpmDriver() { }

    /** Does the local player's CPM model have its own belly animation? (Then our built-in bulge steps aside.) */
    public static boolean localModelHandlesBelly() { return localModelHasBelly; }

    public static void reset() {
        shownMobs = 0f;
        shownItems = 0f;
        localModelHasBelly = false;
        ticks = 0;
        LAST_SENT.clear();
        RESOLVED.clear();
    }

    /** Called every client tick. */
    public static void tick() {
        if (!GulpConfig.cpmSliders || !CpmBridge.available()) {
            localModelHasBelly = false;
            return;
        }
        if (++ticks % 2 != 0) return; // every other tick is plenty

        float cap = Math.max(0.0001f, ClientState.cap());
        float mobTarget = clamp01(ClientState.used() / cap);
        float itemTarget = clamp01(ClientState.itemFill());
        shownMobs = ease(shownMobs, mobTarget);
        shownItems = ease(shownItems, itemTarget);
        float total = clamp01(shownMobs + 0.5f * shownItems);

        boolean resend = ticks % 200 == 0; // now and then, in case the player switched models
        boolean bellyExists = push(GulpConfig.cpmBellyName, BELLY_ALIASES, total, resend);
        boolean mobsExists = push(GulpConfig.cpmMobsName, MOBS_ALIASES, shownMobs, resend);
        boolean itemsExists = push(GulpConfig.cpmItemsName, ITEMS_ALIASES, shownItems, resend);
        // A model with ANY of the three belly animations drives its own belly, so our built-in box steps aside.
        localModelHasBelly = bellyExists || mobsExists || itemsExists;
    }

    /** @return true if the model has a slider for this one (under the configured name or a common alternative) */
    private static boolean push(String configured, String[] aliases, float ratio, boolean force) {
        if (configured == null || configured.isBlank()) return false;
        String name = resolve(configured, aliases);
        if (name == null) return false; // this model has no such animation
        int max = CpmBridge.maxValue(name);
        if (max < 0) { // it was there a moment ago: the player switched models
            RESOLVED.remove(configured);
            LAST_SENT.remove(name);
            return false;
        }
        int value = Math.max(0, Math.min(max, Math.round(ratio * max)));
        Integer last = LAST_SENT.get(name);
        if (force || last == null || last != value) {
            CpmBridge.play(name, value);
            LAST_SENT.put(name, value);
        }
        return true;
    }

    /** The animation name this slider really has in the current model, or null if the model has none. */
    private static String resolve(String configured, String[] aliases) {
        String known = RESOLVED.get(configured);
        if (known != null && CpmBridge.maxValue(known) >= 0) return known;
        // Nothing found yet (a normal skin, say): only search about once a second instead of on every update.
        if (known == null && ticks % 20 != 0) return null;

        Set<String> candidates = new LinkedHashSet<>(variants(configured));
        for (String alias : aliases) candidates.addAll(variants(alias));
        for (String candidate : candidates) {
            int max = CpmBridge.maxValue(candidate);
            if (max >= 0) {
                if (!candidate.equals(known)) {
                    LOGGER.info("Vore Mod: found a CPM belly slider named '{}' (max {}) for '{}'", candidate, max, configured);
                }
                RESOLVED.put(configured, candidate);
                return candidate;
            }
        }
        if (known != null) RESOLVED.remove(configured);
        return null;
    }

    /** The same name in the spellings people commonly use: belly_mobs, Belly_mobs, BELLY_MOBS, Belly_Mobs, Belly Mobs ... */
    private static Set<String> variants(String name) {
        Set<String> out = new LinkedHashSet<>();
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return out;
        for (String base : new String[]{trimmed, trimmed.replace('_', ' ')}) {
            out.add(base);
            out.add(base.toLowerCase(Locale.ROOT));
            out.add(base.toUpperCase(Locale.ROOT));
            out.add(capitalize(base));
            out.add(titleCase(base));
        }
        return out;
    }

    private static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    /** Capitalises each word, keeping whatever separator (underscore or space) was there. */
    private static String titleCase(String s) {
        StringBuilder sb = new StringBuilder();
        boolean startOfWord = true;
        for (char c : s.toCharArray()) {
            if (c == '_' || c == ' ') {
                startOfWord = true;
                sb.append(c);
            } else {
                sb.append(startOfWord ? Character.toUpperCase(c) : Character.toLowerCase(c));
                startOfWord = false;
            }
        }
        return sb.toString();
    }

    private static float ease(float shown, float target) {
        float next = shown + (target - shown) * 0.2f;
        return Math.abs(target - next) < 0.004f ? target : next;
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
}
