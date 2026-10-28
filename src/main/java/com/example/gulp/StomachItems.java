package com.example.gulp;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * Things that happen to the ITEMS in the stomach's item storage, on the server tick:
 *
 *  Deconstruction (hard mode only, like mobs): damageable gear (tools, weapons, armor, bows, shields ...)
 *  is broken down into the ingredients of its crafting recipe (iron armor -> iron ingots). The more worn the item,
 *  the fewer parts you get back. Gear with no crafting recipe (netherite, modded) is left alone, never deleted.
 *  Items in the data tag gulp:stomach_trash (cobblestone, dirt ...) simply dissolve. Nothing else is ever destroyed.
 */
public final class StomachItems {
    /** Items in this tag dissolve in hard mode. Edit data/gulp/tags/items/stomach_trash.json or add to it in a datapack. */
    public static final TagKey<Item> TRASH = ItemTags.create(new ResourceLocation(Gulp.ID, "stomach_trash"));

    /** Mending Gut: ticks between repair passes (5 seconds). Each pass repairs 2 durability per rank on every damaged item. */
    public static final int MEND_INTERVAL = 100;

    /** Iron Gut / Universal Digestion: eating this many items of any kind pays out hunger. */
    public static final int DIGEST_THRESHOLD = 32;

    /** Hunger points (1 point = half a drumstick) restored per 32 items: 1 at rank 0, up to 4 at rank 3. */
    public static int hungerPoints(int rank) { return 1 + Math.max(0, rank); }

    /** The same payout in hunger bars (full drumsticks): 0.5 at rank 0 ... 2.0 at rank 3. */
    public static float hungerBars(int rank) { return hungerPoints(rank) / 2f; }

    /** Ticks between deconstruction passes (20 per second). One gear item is broken down per pass. */
    private static final int DECON_INTERVAL = 60;
    /** How many trash items dissolve per pass when there is no gear to break down. */
    private static final int TRASH_PER_PASS = 4;

    // Recipe lookups are cached per RecipeManager (a /reload makes a new one, which clears the cache).
    private static final Map<Item, List<ItemStack>> PARTS = new HashMap<>();
    private static RecipeManager cachedFor;

    private StomachItems() { }

    /** Called every tick for every player from StomachLogic.tickPlayer. */
    public static void tick(ServerPlayer p, StomachManager mgr, Stomach s, boolean hardNow) {
        if (!GulpServerConfig.ITEM_STORAGE_ENABLED.get()) return;
        if (!p.isAlive() || p.isSpectator() || PlayerHolding.isHeld(p)) return;
        // While any container is open (including the storage screen itself) leave the storage alone,
        // otherwise the open screen and the saved storage would disagree.
        if (p.containerMenu != p.inventoryMenu) return;

        boolean changed = false;

        if (hardNow && GulpServerConfig.ITEM_DIGESTION_ENABLED.get() && p.tickCount % DECON_INTERVAL == 0) {
            changed |= breakDownOne(p, s);
        }

        // Mending Gut: damaged gear and tools in the storage slowly repair themselves.
        int mend = s.rank(Perk.MENDING);
        if (mend > 0 && p.tickCount % MEND_INTERVAL == 0) {
            int amount = mend * 2;
            for (int i = 0; i < s.storageSlots(); i++) {
                ItemStack stack = s.storage.get(i);
                if (!stack.isEmpty() && stack.isDamaged()) {
                    stack.setDamageValue(Math.max(0, stack.getDamageValue() - amount));
                    changed = true;
                }
            }
        }

        if (changed) {
            mgr.setDirty();
            StomachLogic.sync(p, s); // also updates the item-fill belly (CPM sliders)
        }
    }

    // ------------------------------------------------------------------ manual Digest buttons (storage screen)

    /** Stomach XP for digesting one item that can't be broken down (food adds its nutrition on top). */
    private static final int XP_PER_ITEM = 2;

    /**
     * The Eat 1 / Eat 64 buttons: eats up to `count` items from the FIRST slot of the open storage screen.
     * Gear that has a crafting recipe is broken down into its parts instead (in either mode). Every other item (food,
     * blocks, junk) is digested by Iron Gut: it counts toward the 32-item payout and gives a little
     * stomach XP (food adds its nutrition on top). Works in either mode. Goes through the open screen's own container so the screen stays in step.
     */
    public static void digestFirstSlot(ServerPlayer p, Stomach s, SimpleContainer view, int count) {
        digestSlot(p, s, view, 0, count);
    }

    /** Eats up to `count` of the item in ANY storage slot (right-click on the storage screen, or the buttons for slot 0). */
    public static void digestSlot(ServerPlayer p, Stomach s, SimpleContainer view, int slot, int count) {
        if (slot < 0 || slot >= view.getContainerSize()) return;
        ItemStack stack = view.getItem(slot);
        if (stack.isEmpty()) {
            StomachLogic.say(p, Component.literal("That slot is empty."));
            return;
        }
        if (!GulpServerConfig.UNIVERSAL_DIGESTION_ENABLED.get()) {
            StomachLogic.say(p, Component.literal("Eating non-food items is turned off on this server."));
            return;
        }
        // Never destroy something that holds other items (shulker boxes, bundles ...).
        if (stack.hasTag() && (stack.getTag().contains("BlockEntityTag") || stack.getTag().contains("Items"))) {
            StomachLogic.say(p, Component.literal("Can't digest something that holds other items."));
            return;
        }
        if (stack.isEnchanted() && !GulpServerConfig.DECONSTRUCT_ENCHANTED.get()) {
            StomachLogic.say(p, Component.literal("Enchanted items are protected on this server."));
            return;
        }

        // Gear that can be broken down is broken down into its crafting parts (iron sword -> iron + stick) instead of
        // being eaten. It doesn't count toward the Iron Gut 32-item payout, the parts are what you get. Gear with no
        // crafting recipe (netherite, some modded items) falls through and is eaten like any other item.
        if (stack.isDamageableItem()) {
            List<ItemStack> parts = partsOf(p.getServer(), stack.getItem());
            if (parts != null) {
                breakDownSlot(p, s, view, slot, count, parts);
                return;
            }
        }

        int n = Math.max(1, Math.min(Math.min(count, 64), stack.getCount()));
        Component name = stack.getHoverName();
        FoodProperties food = stack.getFoodProperties(p);
        int nutrition = food == null ? 0 : food.getNutrition();

        view.removeItem(slot, n); // the screen's listener saves the change and syncs the client

        // Iron Gut: every 32 items eaten (of any kind) restores hunger. The counter lives in the Stomach, so it survives
        // closing the screen, relogging and dying. Several payouts can happen at once (e.g. "Eat 64" = two).
        int rank = s.rank(Perk.UNIVERSAL);
        s.digestProgress += n;
        int payouts = s.digestProgress / DIGEST_THRESHOLD;
        s.digestProgress %= DIGEST_THRESHOLD;
        int hunger = payouts * hungerPoints(rank);
        if (hunger > 0) p.getFoodData().eat(hunger, 0f); // hunger only, no saturation; the bar can't overfill

        float xp = n * (XP_PER_ITEM + nutrition);
        StomachLogic.gainXp(p, s, xp);
        Component msg = Component.literal("Ate " + n + "x ").append(name);
        if (hunger > 0) {
            msg = msg.copy().append(Component.literal(String.format(" (+%.1f hunger)", hunger / 2f)));
        } else {
            msg = msg.copy().append(Component.literal(" (" + s.digestProgress + "/" + DIGEST_THRESHOLD + ")"));
        }
        StomachLogic.say(p, msg);
        StomachLogic.sound(p, GulpSound.DIGEST_BUTTON);
        p.containerMenu.broadcastChanges();
    }

    /** Eating breakable gear: breaks up to `count` of the slot's item into its parts (worn gear gives back less). */
    private static void breakDownSlot(ServerPlayer p, Stomach s, SimpleContainer view, int slot, int count, List<ItemStack> parts) {
        ItemStack stack = view.getItem(slot);
        int n = Math.max(1, Math.min(Math.min(count, 64), stack.getCount()));
        Component name = stack.getHoverName();
        double condition = 1.0 - stack.getDamageValue() / (double) Math.max(1, stack.getMaxDamage());

        view.removeItem(slot, n); // free the slot BEFORE adding the parts
        for (int i = 0; i < n; i++) {
            for (ItemStack part : parts) {
                // Same as the hard-mode breakdown: 100% of the parts when new, about half when nearly broken.
                int amount = Math.max(1, (int) Math.round(part.getCount() * (0.5 + 0.5 * condition)));
                ItemStack left = view.addItem(new ItemStack(part.getItem(), amount));
                if (!left.isEmpty()) ItemHandlerHelper.giveItemToPlayer(p, left); // storage full: goes to you
            }
        }
        StomachLogic.gainXp(p, s, n * XP_PER_ITEM);
        StomachLogic.say(p, Component.literal("Broke down " + n + "x ").append(name));
        StomachLogic.sound(p, GulpSound.DIGEST_BUTTON);
        p.containerMenu.broadcastChanges();
    }

    // ------------------------------------------------------------------ Feature A: deconstruction

    /** @return true if something was broken down or dissolved */
    private static boolean breakDownOne(ServerPlayer p, Stomach s) {
        int slots = s.storageSlots();
        MinecraftServer server = p.getServer();

        // 1) Gear first: one item per pass.
        for (int i = 0; i < slots; i++) {
            ItemStack stack = s.storage.get(i);
            if (stack.isEmpty() || !stack.isDamageableItem()) continue;
            if (stack.isEnchanted() && !GulpServerConfig.DECONSTRUCT_ENCHANTED.get()) continue;
            List<ItemStack> parts = partsOf(server, stack.getItem());
            if (parts == null) continue; // no crafting recipe: keep it, never delete it

            double condition = 1.0 - stack.getDamageValue() / (double) Math.max(1, stack.getMaxDamage());
            Component name = stack.getHoverName();
            stack.shrink(1);
            if (stack.isEmpty()) s.storage.set(i, ItemStack.EMPTY); // free the slot BEFORE inserting the parts
            for (ItemStack part : parts) {
                // Worn gear gives back less: 100% of the parts when new, about half when nearly broken.
                int count = Math.max(1, (int) Math.round(part.getCount() * (0.5 + 0.5 * condition)));
                stash(p, s, new ItemStack(part.getItem(), count));
            }
            StomachLogic.say(p, Component.literal("Broke down ").append(name));
            return true;
        }

        // 2) Otherwise let trash dissolve.
        if (!GulpServerConfig.TRASH_DISSOLVE_ENABLED.get()) return false;
        for (int i = 0; i < slots; i++) {
            ItemStack stack = s.storage.get(i);
            if (stack.isEmpty() || !stack.is(TRASH) || stack.hasTag()) continue;
            stack.shrink(TRASH_PER_PASS);
            if (stack.isEmpty()) s.storage.set(i, ItemStack.EMPTY);
            return true;
        }
        return false;
    }

    /** Puts a stack in the item storage; whatever doesn't fit goes to the player's inventory (or their feet). */
    private static void stash(ServerPlayer p, Stomach s, ItemStack stack) {
        ItemStack left = s.insertItem(stack);
        if (!left.isEmpty()) ItemHandlerHelper.giveItemToPlayer(p, left);
    }

    /**
     * What a piece of gear is made of: the ingredients of its first normal crafting recipe, or null if it has none.
     * One level only (a chestplate gives ingots, not nuggets), and the result is cached.
     */
    private static List<ItemStack> partsOf(MinecraftServer server, Item item) {
        RecipeManager rm = server.getRecipeManager();
        if (rm != cachedFor) {
            PARTS.clear();
            cachedFor = rm;
        }
        if (PARTS.containsKey(item)) return PARTS.get(item);

        List<ItemStack> result = null;
        RegistryAccess access = server.registryAccess();
        for (CraftingRecipe recipe : rm.getAllRecipesFor(RecipeType.CRAFTING)) {
            if (recipe instanceof CustomRecipe) continue; // dyeing, repairing, fireworks ... not real "recipes"
            ItemStack out = recipe.getResultItem(access);
            if (out.isEmpty() || out.getItem() != item) continue;

            Map<Item, Integer> counts = new LinkedHashMap<>();
            boolean selfReference = false;
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient.isEmpty()) continue;
                ItemStack[] options = ingredient.getItems();
                if (options.length == 0) continue;
                Item pick = options[0].getItem();
                if (pick == item) selfReference = true;
                counts.merge(pick, 1, Integer::sum);
            }
            if (selfReference || counts.isEmpty()) continue;

            int perCraft = Math.max(1, out.getCount());
            List<ItemStack> parts = new ArrayList<>();
            for (Map.Entry<Item, Integer> e : counts.entrySet()) {
                parts.add(new ItemStack(e.getKey(), Math.max(1, e.getValue() / perCraft)));
            }
            result = parts;
            break;
        }
        PARTS.put(item, result);
        return result;
    }
}
