package com.example.gulp;

/**
 * The perks. Add a new one at the END of this list, give it an effect in StomachLogic and an icon in PerksScreen.
 * The game hands out exactly totalRanks() perk points by level 99 (see Stomach.cumulativePoints), so a max-level
 * player can buy every rank of every perk, with nothing left over and nothing missing. Add or change a perk's
 * ranks and the points adjust themselves.
 */
public enum Perk {
    ROOMY("Roomy Stomach", "+6% stomach capacity per rank.", 30),
    HEALING("Healing Stomach", "Soft mode: held mobs heal 1% max HP/rank every 5s.", 15),
    RICH("Better Loot", "Looting on digested mobs: +1 level per 2 ranks (max V).", 10),
    QUICK("Quick Gulp", "Swallow cooldown -0.075s/rank, -0.15s looking up.", 10),
    IRON("Iron Stomach", "Struggling mobs hurt 5% less per rank.", 15),
    GOURMET("Gourmet", "+5% XP from digesting mobs per rank.", 20),
    HEARTY("Hearty Meal", "Heal 0.5 HP per rank whenever you digest a mob.", 10),
    /** Iron Gut / Universal Digestion. Rank 0 is free and always active; each rank adds 0.5 hunger bars per 32 items. */
    UNIVERSAL("Iron Gut", "Digest any 32 items stored in your stomach to restore hunger.", 3),
    /** High-level perk. Storage screen: Strip gear into an enchanted book, or Transfer (soft mode) to another item. */
    STRIP("Enchant Stripper", "Strip enchantments off gear in your stomach into a book, or move them to another item (soft mode). Higher ranks lose fewer levels.", 3, 50),
    /** High-level perk. Slowly repairs gear and tools in the item storage. */
    MENDING("Mending Gut", "Gear and tools held in your stomach storage slowly repair themselves.", 5, 60);

    public final String title;
    public final String description;
    public final int maxRank;
    /** Stomach level needed before the first rank can be bought (1 = no requirement). */
    public final int minLevel;

    Perk(String title, String description, int maxRank) {
        this(title, description, maxRank, 1);
    }

    Perk(String title, String description, int maxRank, int minLevel) {
        this.title = title;
        this.description = description;
        this.maxRank = maxRank;
        this.minLevel = minLevel;
    }

    /** Every rank of every perk added together. */
    public static int totalRanks() {
        int total = 0;
        for (Perk p : values()) total += p.maxRank;
        return total;
    }

    public static Perk byIndex(int i) {
        Perk[] v = values();
        return i >= 0 && i < v.length ? v[i] : null;
    }
}
