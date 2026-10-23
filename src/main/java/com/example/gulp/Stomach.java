package com.example.gulp;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** One player's stomach. Each entry in `contents` is an NBT compound:
 *  Entity (full saved mob), Size, MaxHealth, Digest, DigestTime, Name, Uid */
public class Stomach {
    public static final int MAX_LEVEL = 99;

    /** The most creatures one stomach can hold at once, so a huge stomach full of chickens can't lag a server. */
    public static final int MAX_MOBS = 100;

    /** Soft mode: a completely full stomach earns one level's worth of XP in this many seconds. */
    public static final float SOFT_SECONDS_PER_LEVEL = 240f;

    /** Formats a stomach volume so big numbers stay short (1.8, 16.5, 859). */
    public static String fmtVolume(double v) {
        return v >= 100 ? String.format("%.0f", v) : String.format("%.1f", v);
    }

    public boolean hard = false;
    public int level = 1;
    public int xp = 0;
    public int points = 0;                                  // unspent perk points
    public final int[] ranks = new int[Perk.values().length];
    public final List<CompoundTag> contents = new ArrayList<>();

    /** Item storage: up to 54 slots, but only the first storageSlots() are usable (it grows with your level). */
    public final NonNullList<ItemStack> storage = NonNullList.withSize(54, ItemStack.EMPTY);

    /** Role badge: who can swallow whom. Pred by default (you can swallow, nobody can swallow you). See Role. */
    public Role role = Role.PRED;

    /** MCA: "predator" status. Grows when you swallow/digest villagers, fades with time (see McaIntegration). */
    public int predator = 0;

    /** Universal Digestion (Iron Gut): items eaten since the last hunger payout, always 0..31 (see StomachItems.DIGEST_THRESHOLD). */
    public int digestProgress = 0;

    public transient int cooldown = 0;

    /**
     * XP needed to go from `level` to the next. Up to level 10 it grows faster than linear (100, 280, 520, 800 ...
     * 3160 at level 10); after that it only creeps up by 40 per level (4080 at level 33, 6720 at level 99), because
     * your stomach and the mobs you can eat keep getting bigger and worth more XP.
     */
    public static int xpForNext(int level) {
        if (level <= 10) return (int) (Math.round(100 * Math.pow(level, 1.5) / 10.0) * 10);
        return 3160 + 40 * (level - 10);
    }

    /** XP for digesting a mob. Tougher mobs are worth much more. */
    public static int digestXp(float maxHealth) {
        return 20 + (int) (4 * maxHealth);
    }

    public int rank(Perk perk) { return ranks[perk.ordinal()]; }

    /**
     * How much "mob volume" fits. The base grows faster with every level (1.8 at level 1, 16 at level 10, 48 at
     * level 20, 239 at level 50, 859 at level 99), then Roomy Stomach multiplies it by up to 2.8.
     * For comparison: chicken ~0.1, villager ~0.7, cow ~1.1, horse ~3, iron golem ~5, big modded mobs 50-500+.
     */
    public double capacity() {
        double base = 1.0 + 0.75 * level + 0.08 * level * level;
        return base * (1.0 + 0.06 * rank(Perk.ROOMY));
    }

    /** Rows of item storage: 1 at level 1, one more every 17 levels, 6 rows (54 slots) from level 85. */
    public int storageRows() { return Math.min(6, 1 + level / 17); }

    public int storageSlots() { return storageRows() * 9; }

    /**
     * Puts an item stack into the usable storage slots (topping up matching stacks first).
     * @return whatever didn't fit (empty if it all did)
     */
    public ItemStack insertItem(ItemStack stack) {
        ItemStack remaining = stack.copy();
        int slots = storageSlots();
        for (int i = 0; i < slots && !remaining.isEmpty(); i++) {
            ItemStack in = storage.get(i);
            if (!in.isEmpty() && ItemStack.isSameItemSameTags(in, remaining) && in.getCount() < in.getMaxStackSize()) {
                int move = Math.min(remaining.getCount(), in.getMaxStackSize() - in.getCount());
                in.grow(move);
                remaining.shrink(move);
            }
        }
        for (int i = 0; i < slots && !remaining.isEmpty(); i++) {
            if (storage.get(i).isEmpty()) {
                storage.set(i, remaining.split(Math.min(remaining.getCount(), remaining.getMaxStackSize())));
            }
        }
        return remaining;
    }

    /** How full the item storage is, 0 to 1 (each slot counts by how full its stack is). */
    public float itemFill() {
        int slots = storageSlots();
        float total = 0f;
        for (int i = 0; i < slots; i++) {
            ItemStack stack = storage.get(i);
            total += weightOf(stack);
        }
        return Math.min(1f, total / slots);
    }

    /**
     * How much of a slot a stack weighs: count / max stack size. Shulker boxes and bundles also weigh what is inside
     * them (nestedWeightMultiplier), so a full shulker fills the belly far more than an empty one.
     */
    public static float weightOf(ItemStack stack) {
        return weightOf(stack, 0);
    }

    private static float weightOf(ItemStack stack, int depth) {
        if (stack.isEmpty()) return 0f;
        float weight = stack.getCount() / (float) stack.getMaxStackSize();
        if (depth >= 3 || !stack.hasTag()) return weight;
        ListTag inner = null;
        if (stack.getItem() instanceof BundleItem) {
            inner = stack.getTag().getList("Items", Tag.TAG_COMPOUND);
        } else if (Block.byItem(stack.getItem()) instanceof ShulkerBoxBlock) {
            inner = stack.getTag().getCompound("BlockEntityTag").getList("Items", Tag.TAG_COMPOUND);
        }
        if (inner != null) {
            float sum = 0f;
            for (int i = 0; i < inner.size(); i++) sum += weightOf(ItemStack.of(inner.getCompound(i)), depth + 1);
            weight += sum * GulpServerConfig.NESTED_WEIGHT_MULTIPLIER.get().floatValue() * stack.getCount();
        }
        return weight;
    }

    public double used() {
        double total = 0;
        for (CompoundTag e : contents) total += e.getFloat("Size");
        return total;
    }

    /**
     * Total perk points earned by reaching `level`: 0 at level 1, exactly Perk.totalRanks() at level 99, spread evenly
     * (most levels give 1 point, and about every eighth level gives 2).
     */
    public static int cumulativePoints(int level) {
        return (int) ((long) (Math.max(1, level) - 1) * Perk.totalRanks() / (MAX_LEVEL - 1));
    }

    /** @return how many levels were gained */
    public int addXp(int amount) {
        if (level >= MAX_LEVEL) return 0;
        xp += amount;
        int gained = 0;
        while (level < MAX_LEVEL && xp >= xpForNext(level)) {
            xp -= xpForNext(level);
            level++;
            points += cumulativePoints(level) - cumulativePoints(level - 1);
            gained++;
        }
        return gained;
    }

    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putBoolean("Hard", hard);
        nbt.putInt("Level", level);
        nbt.putInt("Xp", xp);
        nbt.putInt("Points", points);
        CompoundTag perks = new CompoundTag();
        for (Perk p : Perk.values()) perks.putInt(p.name(), ranks[p.ordinal()]);
        nbt.put("Perks", perks);
        nbt.putString("Role", role.name());
        nbt.putInt("Predator", predator);
        nbt.putInt("DigestProgress", digestProgress);
        nbt.put("Storage", ContainerHelper.saveAllItems(new CompoundTag(), storage));
        ListTag list = new ListTag();
        list.addAll(contents);
        nbt.put("Contents", list);
        return nbt;
    }

    public static Stomach fromNbt(CompoundTag nbt) {
        Stomach s = new Stomach();
        s.hard = nbt.getBoolean("Hard");
        s.level = Math.max(1, nbt.getInt("Level"));
        s.xp = nbt.getInt("Xp");
        s.points = nbt.contains("Points") ? nbt.getInt("Points") : s.level - 1;
        // Saves from before roles existed only had the "let players swallow me" switch: ON became Both, OFF became Pred.
        Role saved = nbt.contains("Role") ? Role.byName(nbt.getString("Role")) : null;
        s.role = saved != null ? saved : (nbt.getBoolean("AllowSwallowed") ? Role.BOTH : Role.PRED);
        s.predator = Math.max(0, nbt.getInt("Predator"));
        s.digestProgress = Math.max(0, Math.min(StomachItems.DIGEST_THRESHOLD - 1, nbt.getInt("DigestProgress")));
        ContainerHelper.loadAllItems(nbt.getCompound("Storage"), s.storage);
        CompoundTag perks = nbt.getCompound("Perks");
        for (Perk p : Perk.values()) s.ranks[p.ordinal()] = Math.min(p.maxRank, perks.getInt(p.name()));
        // Older saves earned fewer points. Top them up so everyone has what a player at their level should have.
        int spent = 0;
        for (int r : s.ranks) spent += r;
        int shouldHave = cumulativePoints(s.level);
        if (s.points + spent < shouldHave) s.points = shouldHave - spent;
        ListTag list = nbt.getList("Contents", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) s.contents.add(list.getCompound(i));
        return s;
    }
}
