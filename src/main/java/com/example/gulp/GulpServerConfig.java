package com.example.gulp;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Server settings for multiplayer admins (config file: serverconfig/gulp-server.toml inside each world,
 * or the world's serverconfig folder on a dedicated server). Only the server reads these.
 */
public final class GulpServerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue HARD_MODE_ENABLED;
    public static final ForgeConfigSpec.DoubleValue STRUGGLE_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue XP_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue ITEM_STORAGE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue PLAYER_SWALLOWING_ENABLED;
    public static final ForgeConfigSpec.IntValue PLAYER_MAX_HOLD_SECONDS;
    public static final ForgeConfigSpec.BooleanValue PLAYER_HARD_VORE_ENABLED;
    public static final ForgeConfigSpec.IntValue PLAYER_DIGEST_SECONDS;
    public static final ForgeConfigSpec.DoubleValue PLAYER_STRUGGLE_CHANCE;
    public static final ForgeConfigSpec.BooleanValue PLAYER_DIGESTION_KILLS;
    public static final ForgeConfigSpec.IntValue PLAYER_WALL_RUB_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.DoubleValue NESTED_WEIGHT_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue HAZARD_SELF_DAMAGE;
    public static final ForgeConfigSpec.BooleanValue SHARED_STORAGE_VIEW_ENABLED;
    public static final ForgeConfigSpec.BooleanValue ITEM_DIGESTION_ENABLED;
    public static final ForgeConfigSpec.BooleanValue TRASH_DISSOLVE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue DECONSTRUCT_ENCHANTED;
    public static final ForgeConfigSpec.BooleanValue UNIVERSAL_DIGESTION_ENABLED;
    public static final ForgeConfigSpec.BooleanValue MCA_ENABLED;
    public static final ForgeConfigSpec.DoubleValue MCA_REACTION_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue MCA_LOCKOUT_ENABLED;
    public static final ForgeConfigSpec.IntValue MCA_PREDATOR_LOCK;
    public static final ForgeConfigSpec.IntValue MCA_HEARTS_LOCK;
    public static final ForgeConfigSpec.IntValue MCA_PREDATOR_DECAY_SECONDS;
    public static final ForgeConfigSpec.BooleanValue MCA_PANIC;
    public static final ForgeConfigSpec.BooleanValue DEV_LEVEL_UP_ENABLED;
    public static final ForgeConfigSpec.BooleanValue MCA_MEMORY_ENABLED;
    public static final ForgeConfigSpec.BooleanValue MCA_AFFECTION_ENABLED;
    public static final ForgeConfigSpec.IntValue MCA_AFFECTION_HEARTS;
    public static final ForgeConfigSpec.IntValue MCA_REQUEST_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.BooleanValue MCA_STATUS_MESSAGE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        HARD_MODE_ENABLED = b
                .comment("If false, nobody can digest mobs: hard mode and the Digest button are turned off (soft mode only).")
                .define("hardModeEnabled", true);
        STRUGGLE_DAMAGE_MULTIPLIER = b
                .comment("Multiplies the damage swallowed mobs deal to you in hard mode. 0 = they never hurt you, 1 = normal.")
                .defineInRange("struggleDamageMultiplier", 1.0, 0.0, 10.0);
        XP_MULTIPLIER = b
                .comment("Multiplies all stomach XP gained. 0.5 = half as fast, 2 = twice as fast.")
                .defineInRange("xpMultiplier", 1.0, 0.0, 10.0);
        ITEM_STORAGE_ENABLED = b
                .comment("If false, the stomach can't store items (no item swallowing and no Storage screen).")
                .define("itemStorageEnabled", true);
        PLAYER_SWALLOWING_ENABLED = b
                .comment("If false, players can't swallow each other at all.",
                        "Even when true, only players whose role is Prey or Both can be swallowed, and only after they accept a request;",
                        "swallowed players can leave any time with the Release key, unless it is hard mode (see playerHardVoreEnabled).")
                .define("playerSwallowingEnabled", true);
        PLAYER_MAX_HOLD_SECONDS = b
                .comment("Longest a swallowed player is held before they are let out automatically.")
                .defineInRange("playerMaxHoldSeconds", 300, 10, 3600);
        PLAYER_HARD_VORE_ENABLED = b
                .comment("If false, players are always held softly, even when the swallower is in hard mode.",
                        "If true, a player swallowed in hard mode is digested over playerDigestSeconds and can only get out",
                        "by struggling (the Release key does not work for them). The request they accept says which mode it is.")
                .define("playerHardVoreEnabled", true);
        PLAYER_DIGEST_SECONDS = b
                .comment("Hard mode only: how long a swallowed player takes to be digested.")
                .defineInRange("playerDigestSeconds", 180, 30, 3600);
        PLAYER_STRUGGLE_CHANCE = b
                .comment("Hard mode only: percent chance that one struggle (once per second at most) frees a swallowed player.",
                        "It drops to half of this as digestion nears 100%.")
                .defineInRange("playerStruggleChancePercent", 12.0, 1.0, 100.0);
        PLAYER_DIGESTION_KILLS = b
                .comment("Hard mode only: what happens when the digestion timer of a swallowed player runs out.",
                        "false (default) = they are let out, weakened and slowed for a while. true = they die.",
                        "Players are always warned in the request they accept.")
                .define("playerDigestionKills", false);
        PLAYER_WALL_RUB_COOLDOWN_SECONDS = b
                .comment("Soft mode: how often a swallowed player can rub the stomach walls (gives the swallower food and buffs).")
                .defineInRange("playerWallRubCooldownSeconds", 10, 1, 300);
        NESTED_WEIGHT_MULTIPLIER = b
                .comment("Shulker boxes and bundles weigh what is inside them: each item counts like a stack in a slot,",
                        "times this number, on top of the box itself. 0 = boxes always weigh one slot. This drives the item belly size.")
                .defineInRange("nestedWeightMultiplier", 1.0, 0.0, 2.0);
        HAZARD_SELF_DAMAGE = b
                .comment("Health points (2 = one heart) you take when you swallow something sharp or hazardous (swords, axes, cactus, lava ...),",
                        "and whenever you move one between your inventory and the stomach storage. It never takes you below 2 hearts and creative players take none. 0 = no damage.",
                        "Edit the item tag gulp:stomach_hazardous to change which items count (swords, axes, tridents, shears and arrows always do).")
                .defineInRange("hazardSelfDamage", 2.0, 0.0, 10.0);
        SHARED_STORAGE_VIEW_ENABLED = b
                .comment("If true, a swallowed player can look at (not touch) the item storage of whoever swallowed them.")
                .define("sharedStorageViewEnabled", true);
        ITEM_DIGESTION_ENABLED = b
                .comment("Hard mode only: gear in the item storage (tools, weapons, armor) is broken down into its crafting ingredients over time.",
                        "If false, stored items are never touched by hard mode.")
                .define("itemDigestionEnabled", true);
        TRASH_DISSOLVE_ENABLED = b
                .comment("Hard mode only: items in the tag gulp:stomach_trash (cobblestone, dirt, seeds ...) dissolve over time.")
                .define("trashDissolveEnabled", true);
        DECONSTRUCT_ENCHANTED = b
                .comment("If true, enchanted gear is broken down too (the enchantments are lost). If false it is left alone.")
                .define("deconstructEnchanted", false);
        UNIVERSAL_DIGESTION_ENABLED = b
                .comment("If false, the Eat buttons on the storage screen only eat food (Iron Gut / Universal Digestion does nothing).",
                        "Otherwise any item can be eaten: every 32 items restores hunger (see the Iron Gut perk).")
                .define("universalDigestionEnabled", true);
        MCA_ENABLED = b
                .comment("Minecraft Comes Alive reactions (only does anything if MCA is installed): swallowing villagers changes",
                        "how they feel about you, and a 'predator' status makes frightened villagers refuse to talk to you.")
                .define("mcaIntegrationEnabled", true);
        MCA_REACTION_MULTIPLIER = b
                .comment("Scales every change to villagers' hearts and mood. 0 = villagers never react, 2 = twice as strongly.")
                .defineInRange("mcaReactionMultiplier", 1.0, 0.0, 5.0);
        MCA_LOCKOUT_ENABLED = b
                .comment("If false, villagers never refuse to talk, trade or marry because of your predator status.")
                .define("mcaLockoutEnabled", true);
        MCA_PREDATOR_LOCK = b
                .comment("Predator status at which frightened villagers refuse to talk to you.",
                        "Swallowing a villager in hard mode adds about 15, digesting one about 25.")
                .defineInRange("mcaPredatorLockThreshold", 30, 1, 200);
        MCA_HEARTS_LOCK = b
                .comment("A villager whose hearts for you are at or below this also refuses to talk while your predator status is above 0.")
                .defineInRange("mcaHeartsLockThreshold", -30, -500, 0);
        MCA_PREDATOR_DECAY_SECONDS = b
                .comment("Seconds for the predator status to drop by 1 (at the default 20, a status of 40 fades in about 13 minutes).")
                .defineInRange("mcaPredatorDecaySeconds", 20, 1, 3600);
        MCA_PANIC = b
                .comment("If true, villagers who see you swallow or digest a villager run from you for a few seconds.")
                .define("mcaVillagersPanic", true);
        MCA_MEMORY_ENABLED = b
                .comment("Villagers remember being swallowed, released, or seeing it happen: written to the villager's own saved data",
                        "and into MCA's long-term memory, where MCA dialogue and the MCA: Conversations add-on can read it.")
                .define("mcaMemoryEnabled", true);
        MCA_AFFECTION_ENABLED = b
                .comment("Villagers who like you enough may ask to be carried safely (soft mode only, they can never be digested),",
                        "and comment on being inside. Children are never swallowed, whatever this is set to.")
                .define("mcaAffectionEnabled", true);
        MCA_AFFECTION_HEARTS = b
                .comment("MCA hearts a villager needs with you before they will ask to be carried or comment from inside.")
                .defineInRange("mcaAffectionHearts", 60, 1, 500);
        MCA_REQUEST_COOLDOWN_SECONDS = b
                .comment("How long a villager waits before asking to be carried again.")
                .defineInRange("mcaRequestCooldownSeconds", 600, 30, 36000);
        MCA_STATUS_MESSAGE = b
                .comment("Test/dev helper: when a player joins, show a chat line saying whether this mod connected to Minecraft Comes Alive.",
                        "Set to false to turn it off once you've confirmed it works.")
                .define("mcaStatusMessage", true);
        DEV_LEVEL_UP_ENABLED = b
                .comment("Test/dev helper: pressing the 1 and P keys together gives yourself one instant stomach level.",
                        "SET THIS TO FALSE BEFORE RELEASING. When false, only operators (permission level 2) can use it.")
                .define("devLevelUpKey", true);
        SPEC = b.build();
    }

    private GulpServerConfig() { }
}
