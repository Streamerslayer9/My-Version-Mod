package com.example.gulp;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Enchant Stripper perk. Uses the two boxes on the left of the storage screen (Stomach.stripSlots), which you fill by
 * clicking items into them like normal slots:
 *  box 0 = source, box 1 = target.
 *  Strip    the source (enchanted gear) loses its enchantments, which go into a plain book from the target box (one book
 *           is used up and becomes the enchanted book, so no books are ever created from nothing). The enchanted book
 *           stays in the target box if that was the only book, otherwise it goes to your item storage (or inventory).
 *  Destroy  deletes all the enchantments on the source for good, and makes nothing.
 *  Transfer the source's enchantments (gear or enchanted book) move onto the item in the target box. Soft mode only.
 * Every rank loses one level less: rank 1 loses 2 levels per enchantment, rank 2 loses 1, rank 3 loses none.
 * An enchantment that would drop below level 1 is left alone (never destroyed).
 */
final class EnchantTools {
    private EnchantTools() { }

    static int levelsLost(int rank) { return Math.max(0, 3 - rank); }

    /** A click on box 0 or 1 with the mouse: picks the item up, puts the carried item in, merges or swaps, like a slot. */
    static void clickBox(ServerPlayer p, Stomach s, SimpleContainer view, int box, int button, boolean shift) {
        if (box < 0 || box > 1) return;
        if (s.rank(Perk.STRIP) < 1) {
            StomachLogic.say(p, Component.literal("You need the Enchant Stripper perk."));
            return;
        }
        AbstractContainerMenu menu = p.containerMenu;
        ItemStack inBox = s.stripSlots.get(box);
        ItemStack carried = menu.getCarried();

        if (shift) {
            // Shift-click sends the item back to the item storage (or your inventory if that is full).
            if (!inBox.isEmpty()) {
                s.stripSlots.set(box, ItemStack.EMPTY);
                ItemStack left = view.addItem(inBox);
                if (!left.isEmpty()) ItemHandlerHelper.giveItemToPlayer(p, left);
                view.setChanged();
            }
        } else if (carried.isEmpty()) {
            if (!inBox.isEmpty()) { // pick the whole stack up
                menu.setCarried(inBox);
                s.stripSlots.set(box, ItemStack.EMPTY);
            }
        } else if (inBox.isEmpty()) {
            ItemStack placed = carried.split(button == 1 ? 1 : carried.getCount()); // right click puts just one
            s.stripSlots.set(box, placed);
            if (carried.isEmpty()) menu.setCarried(ItemStack.EMPTY);
        } else if (ItemStack.isSameItemSameTags(inBox, carried) && inBox.getCount() < inBox.getMaxStackSize()) {
            int move = Math.min(button == 1 ? 1 : carried.getCount(), inBox.getMaxStackSize() - inBox.getCount());
            inBox.grow(move);
            carried.shrink(move);
            if (carried.isEmpty()) menu.setCarried(ItemStack.EMPTY);
        } else { // different items: swap
            s.stripSlots.set(box, carried);
            menu.setCarried(inBox);
        }
        menu.sendAllDataToRemote(); // the client's cursor item and boxes follow
    }

    static void strip(ServerPlayer p, Stomach s, SimpleContainer view) {
        int rank = s.rank(Perk.STRIP);
        if (rank < 1) {
            StomachLogic.say(p, Component.literal("You need the Enchant Stripper perk."));
            return;
        }
        ItemStack src = s.stripSlots.get(0);
        ItemStack bookBox = s.stripSlots.get(1);
        if (src.isEmpty() || src.getCount() != 1 || src.is(Items.ENCHANTED_BOOK) || !src.isEnchanted()) {
            StomachLogic.say(p, Component.literal("Put one piece of enchanted gear in the first box."));
            return;
        }
        if (!bookBox.is(Items.BOOK)) {
            StomachLogic.say(p, Component.literal("Put a plain book in the second box. Stripping uses it up."));
            return;
        }
        int lost = levelsLost(rank);
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        int kept = 0;
        for (Map.Entry<Enchantment, Integer> e : EnchantmentHelper.getEnchantments(src).entrySet()) {
            int level = e.getValue() - lost;
            if (level >= 1) {
                EnchantedBookItem.addEnchantment(book, new EnchantmentInstance(e.getKey(), level));
                kept++;
            }
        }
        if (kept == 0) { // nothing would survive: don't destroy anything
            StomachLogic.say(p, Component.literal("At your rank every enchantment on this would be lost. Raise the perk first."));
            return;
        }
        Component name = src.getHoverName();
        EnchantmentHelper.setEnchantments(new HashMap<>(), src); // the gear is plain now
        if (bookBox.getCount() == 1) {
            s.stripSlots.set(1, book); // the plain book becomes the enchanted book
        } else {
            bookBox.shrink(1); // one plain book used up
            ItemStack left = view.addItem(book);
            if (!left.isEmpty()) ItemHandlerHelper.giveItemToPlayer(p, left); // storage full: the book goes to you
            view.setChanged();
        }
        StomachLogic.say(p, Component.literal("Stripped " + name.getString() + " onto a book"
                + (lost > 0 ? " (-" + lost + " level" + (lost > 1 ? "s" : "") + ")" : "")));
        StomachLogic.sound(p, GulpSound.DIGEST_BUTTON);
    }

    /** Deletes every enchantment on the item in the source box. Nothing is made. */
    static void destroy(ServerPlayer p, Stomach s) {
        if (s.rank(Perk.STRIP) < 1) {
            StomachLogic.say(p, Component.literal("You need the Enchant Stripper perk."));
            return;
        }
        ItemStack src = s.stripSlots.get(0);
        boolean book = src.is(Items.ENCHANTED_BOOK);
        if (src.isEmpty() || src.getCount() != 1 || (!book && !src.isEnchanted())) {
            StomachLogic.say(p, Component.literal("Put one enchanted item in the first box."));
            return;
        }
        Component name = src.getHoverName();
        if (book) {
            s.stripSlots.set(0, new ItemStack(Items.BOOK)); // an emptied book is just a book
        } else {
            EnchantmentHelper.setEnchantments(new HashMap<>(), src);
        }
        StomachLogic.say(p, Component.literal("Destroyed the enchantments on " + name.getString() + "."));
        StomachLogic.sound(p, GulpSound.DIGEST_BUTTON);
    }

    static void transfer(ServerPlayer p, Stomach s, SimpleContainer view) {
        if (s.hard && GulpServerConfig.HARD_MODE_ENABLED.get()) {
            StomachLogic.say(p, Component.literal("Transferring enchantments only works in soft mode."));
            return;
        }
        int rank = s.rank(Perk.STRIP);
        if (rank < 1) {
            StomachLogic.say(p, Component.literal("You need the Enchant Stripper perk."));
            return;
        }
        ItemStack src = s.stripSlots.get(0);
        ItemStack tgt = s.stripSlots.get(1);
        boolean srcBook = src.is(Items.ENCHANTED_BOOK);
        if (src.isEmpty() || tgt.isEmpty() || src.getCount() != 1 || tgt.getCount() != 1
                || (!srcBook && !src.isEnchanted()) || tgt.is(Items.ENCHANTED_BOOK)) {
            StomachLogic.say(p, Component.literal("Put the enchanted item in the first box and the item to enchant in the second."));
            return;
        }
        Map<Enchantment, Integer> from = new LinkedHashMap<>(EnchantmentHelper.getEnchantments(src));
        Map<Enchantment, Integer> to = new LinkedHashMap<>(EnchantmentHelper.getEnchantments(tgt));
        int lost = levelsLost(rank);
        int moved = 0;
        for (Iterator<Map.Entry<Enchantment, Integer>> it = from.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Enchantment, Integer> e = it.next();
            Enchantment ench = e.getKey();
            int level = e.getValue() - lost;
            if (level < 1 || !ench.canEnchant(tgt)) continue;
            boolean conflict = false;
            for (Enchantment other : to.keySet()) {
                if (other != ench && !ench.isCompatibleWith(other)) conflict = true;
            }
            Integer existing = to.get(ench);
            if (conflict || (existing != null && existing >= level)) continue; // doesn't fit, or nothing to gain
            to.put(ench, level);
            it.remove(); // leaves the source
            moved++;
        }
        if (moved == 0) {
            StomachLogic.say(p, Component.literal("Nothing could move: the item can't take them, or they conflict."));
            return;
        }
        EnchantmentHelper.setEnchantments(to, tgt);
        if (srcBook) {
            src.removeTagKey("StoredEnchantments");
            if (from.isEmpty()) {
                s.stripSlots.set(0, new ItemStack(Items.BOOK)); // an emptied book is just a book
            } else {
                for (Map.Entry<Enchantment, Integer> e : from.entrySet()) {
                    EnchantedBookItem.addEnchantment(src, new EnchantmentInstance(e.getKey(), e.getValue()));
                }
            }
        } else {
            EnchantmentHelper.setEnchantments(from, src); // whatever didn't move stays on the source
        }
        StomachLogic.say(p, Component.literal("Moved " + moved + " enchantment" + (moved > 1 ? "s" : "") + " onto "
                + tgt.getHoverName().getString()));
        StomachLogic.sound(p, GulpSound.DIGEST_BUTTON);
    }
}
