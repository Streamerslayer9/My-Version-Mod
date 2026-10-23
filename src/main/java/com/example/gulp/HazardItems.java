package com.example.gulp;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

/**
 * Items that hurt to swallow: anything sharp (swords, axes, tridents, shears, arrows) plus the items in the data tag
 * gulp:stomach_hazardous (cactus, lava, TNT ...). Works on both sides: the client uses it to show the warning, the
 * server uses it to refuse an unconfirmed swallow and to apply the self-damage.
 */
public final class HazardItems {
    /** Edit data/gulp/tags/items/stomach_hazardous.json or add to it in a datapack. */
    public static final TagKey<Item> HAZARDOUS = ItemTags.create(new ResourceLocation(Gulp.ID, "stomach_hazardous"));

    private HazardItems() { }

    public static boolean isHazardous(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem
                || item instanceof ShearsItem || item instanceof ArrowItem || stack.is(HAZARDOUS);
    }
}
