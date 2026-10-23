package com.example.gulp;

import net.minecraft.network.chat.Component;

/**
 * Player role badge. Chosen by each player in Settings, saved with their stomach.
 *  PRED    can swallow, can't be swallowed (the default: how the mod behaved before roles existed)
 *  PREY    can be swallowed (after accepting a request), can't swallow anything
 *  BOTH    can do both
 *  NEITHER all vore is off: no swallowing, can't be swallowed, the stomach menu and HUD are hidden
 * The enum order is the order the Settings button cycles through, and the ordinal is what goes over the network.
 */
public enum Role {
    PRED("Pred", 0xFFFF5555, "Predator: can swallow, can't be swallowed",
            "You can swallow others. Others can't swallow you."),
    PREY("Prey", 0xFF55FFFF, "Prey: can be swallowed, can't swallow",
            "Others can swallow you, but only if you accept their request. You can't swallow anyone."),
    BOTH("Both", 0xFFFFAA00, "Pred & Prey: can swallow and be swallowed",
            "You can swallow others, and they can swallow you (only if you accept their request)."),
    NEITHER("Neither", 0xFFAAAAAA, "Vore is off",
            "All vore is off for you: you can't swallow anyone, nobody can swallow you, and the stomach menu is hidden.");

    public final String label;
    public final int color;
    /** Shown under the name when another player points at you. */
    public final String hint;
    /** Shown on the Settings button. */
    public final String description;

    Role(String label, int color, String hint, String description) {
        this.label = label;
        this.color = color;
        this.hint = hint;
        this.description = description;
    }

    public boolean canEat() { return this == PRED || this == BOTH; }

    public boolean canBeEaten() { return this == PREY || this == BOTH; }

    public Role next() { return values()[(ordinal() + 1) % values().length]; }

    public static Role byId(int id) {
        Role[] all = values();
        return id >= 0 && id < all.length ? all[id] : null;
    }

    public static Role byName(String name) {
        for (Role r : values()) if (r.name().equalsIgnoreCase(name)) return r;
        return null;
    }

    /** What a player who can't swallow is told when they try. */
    public Component cantSwallowMessage() {
        return Component.literal(this == NEITHER
                ? "Vore is turned off for you (role: Neither). Change your role in Settings."
                : "You're set to Prey, so you can't swallow anything. Change your role in Settings.");
    }

    /** What a player is told when they try to swallow someone with this role (only for roles that can't be swallowed). */
    public Component cantBeSwallowedMessage() {
        return Component.literal(this == PRED
                ? "This player is a predator and cannot be swallowed"
                : "This player has opted out of vore and cannot be swallowed");
    }
}
