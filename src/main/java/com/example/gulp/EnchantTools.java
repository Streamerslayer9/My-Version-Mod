package com.example.gulp;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
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
 * Enchant Stripper perk. Works on the first two slots of the open storage screen:
 *  Strip    slot 1 (enchanted gear) loses its enchantments, which go into an enchanted book in your storage.
 *  Transfer slot 1 (enchanted gear or book) gives its enchantments to the item in slot 2. Soft mode only.
 * Every rank loses one level less: rank 1 loses 2 levels per enchantment, rank 2 loses 1, rank 3 loses none.
 * An enchantment that would drop below level 1 is left alone (never destroyed).
 */
final class EnchantTools {
    private EnchantTools() { }

    static int levelsLost(int rank) { return Math.max(0, 3 - rank); }

    static void strip(ServerPlayer p, Stomach s, SimpleContainer view) {
        int rank = s.rank(Perk.STRIP);
        if (rank < 1) {
            StomachLogic.say(p, Component.literal("You need the Enchant Stripper perk."));
            return;
        }
        ItemStack src = view.getItem(0);
        if (src.isEmpty() || src.getCount() != 1 || src.is(Items.ENCHANTED_BOOK) || !src.isEnchanted()) {
            StomachLogic.say(p, Component.literal("Put one piece of enchanted gear in the first slot."));
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
        ItemStack left = view.addItem(book);
        if (!left.isEmpty()) ItemHandlerHelper.giveItemToPlayer(p, left); // storage full: the book goes to you
        view.setChanged();
        StomachLogic.say(p, Component.literal("Stripped " + name.getString() + " into an enchanted book"
                + (lost > 0 ? " (-" + lost + " level" + (lost > 1 ? "s" : "") + ")" : "")));
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
        ItemStack src = view.getItem(0);
        ItemStack tgt = view.getItem(1);
        boolean srcBook = src.is(Items.ENCHANTED_BOOK);
        if (src.isEmpty() || tgt.isEmpty() || src.getCount() != 1 || tgt.getCount() != 1
                || (!srcBook && !src.isEnchanted()) || tgt.is(Items.ENCHANTED_BOOK)) {
            StomachLogic.say(p, Component.literal("Put the enchanted item in slot 1 and the item to enchant in slot 2."));
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
                view.setItem(0, new ItemStack(Items.BOOK)); // an emptied book is just a book
            } else {
                for (Map.Entry<Enchantment, Integer> e : from.entrySet()) {
                    EnchantedBookItem.addEnchantment(src, new EnchantmentInstance(e.getKey(), e.getValue()));
                }
            }
        } else {
            EnchantmentHelper.setEnchantments(from, src); // whatever didn't move stays on the source
        }
        view.setChanged();
        StomachLogic.say(p, Component.literal("Moved " + moved + " enchantment" + (moved > 1 ? "s" : "") + " onto "
                + tgt.getHoverName().getString()));
        StomachLogic.sound(p, GulpSound.DIGEST_BUTTON);
    }
}
