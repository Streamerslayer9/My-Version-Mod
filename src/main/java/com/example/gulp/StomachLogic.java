package com.example.gulp;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.network.PacketDistributor;

import org.slf4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.UUID;

/** All the server-side stomach rules. */
public final class StomachLogic {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Hard mode only: each swallowed mob has this chance every second to struggle and hurt you. */
    private static final float STRUGGLE_CHANCE_PER_SECOND = 0.08f;

    /**
     * Once the digestion bar is full, the mob doesn't die at once: for about this long it takes steady damage
     * (you can watch its health bar drain on the stomach screen), and it dies and drops its loot when it runs out.
     * Damage per second = the mob's max health / this, so a mob at full health lasts this many seconds.
     */
    private static final float DISSOLVE_SECONDS = 8f;
    /** Struggle damage = base + per-volume x the mob's size, capped (2.0 = one heart), before armor and Iron Stomach. */
    private static final float STRUGGLE_BASE_DAMAGE = 2.0f;
    private static final float STRUGGLE_PER_VOLUME = 0.75f;
    private static final float STRUGGLE_MAX_DAMAGE = 5.0f;

    // While a mob is being digested these say whose it is, so ServerEvents can hand its loot to that player.
    private static ServerPlayer digestingPlayer;
    private static LivingEntity digestingMob;
    private static int itemsGiven;

    public static ServerPlayer digestingPlayer() { return digestingPlayer; }
    public static LivingEntity digestingMob() { return digestingMob; }

    /** Puts a digested mob's drops straight into the player's inventory (anything that doesn't fit lands at their feet). */
    public static void giveDrops(Collection<ItemEntity> drops) {
        if (digestingPlayer == null) return;
        for (ItemEntity drop : drops) {
            if (drop.getItem().isEmpty()) continue;
            itemsGiven += drop.getItem().getCount();
            ItemHandlerHelper.giveItemToPlayer(digestingPlayer, drop.getItem().copy());
        }
    }

    /** Extra Looting levels applied to the mob being digested right now (see ServerEvents.onLooting). */
    private static int pendingLooting = 0;

    public static int pendingLooting() { return pendingLooting; }

    /** Each player's currently open storage screen container, so the Digest buttons can't touch any other chest. */
    private static final Map<UUID, SimpleContainer> OPEN_VIEWS = new HashMap<>();

    private StomachLogic() { }

    // 0 swallow | 1 toggle mode | 2 release last | 3 release all
    // 4 release entry (arg = uid) | 5 digest entry now (arg = uid) | 6 buy perk (arg = perk index)
    // 7 open item storage | 8 set your role (arg = Role ordinal)
    // 12 strip slot 1 into an enchanted book | 13 transfer slot 1 enchantments to slot 2 (soft mode)
    // 11 swallowed player: struggle (hard mode) / rub the walls (soft mode)
    // 10 digest the first slot of the open storage screen (arg = how many, 1 or 64)
    // 9 swallow the item in an inventory slot (arg = slot in the inventory screen, plus 65536 for just one item)
    public static void handleAction(ServerPlayer p, int action, int arg) {
        if (p == null || !p.isAlive()) return;
        // A swallowed player can only try to get out (Release, soft mode only) or struggle / rub the walls (action 11).
        if (PlayerHolding.isHeld(p)) {
            if (action == 2 || action == 3) PlayerHolding.tryRelease(p);
            else if (action == 11) PlayerHolding.struggle(p);
            else if (action == 7) PlayerHolding.sendSharedStorage(p); // look at the swallower's storage (read-only)
            return;
        }
        if (p.isSpectator()) return;
        StomachManager mgr = StomachManager.get(p.getServer());
        Stomach s = mgr.of(p.getUUID());
        // Roles: Prey and Neither have no swallowing, mode switch, digesting or item storage at all.
        // (Release, perks and changing your role always work.)
        if (!s.role.canEat() && (action == 0 || action == 1 || action == 5 || action == 7 || action == 9 || action == 10 || action == 12 || action == 13)) {
            StomachLogic.say(p, s.role.cantSwallowMessage());
            return;
        }
        switch (action) {
            case 0 -> swallow(p, s, arg);
            case 1 -> {
                if (!s.hard && !hardModeAllowed()) {
                    StomachLogic.say(p, Component.literal("Hard mode is turned off on this server."));
                } else {
                    s.hard = !s.hard;
                    StomachLogic.say(p, Component.literal(s.hard
                            ? "Hard mode: contents will be digested for loot."
                            : "Soft mode: contents are held safely."));
                }
            }
            case 2 -> {
                if (!s.contents.isEmpty() && release(p, s.contents.get(s.contents.size() - 1))) {
                    s.contents.remove(s.contents.size() - 1);
                    sound(p, GulpSound.SPIT);
                }
            }
            case 3 -> {
                boolean any = false;
                Iterator<CompoundTag> it = s.contents.iterator();
                while (it.hasNext()) {
                    if (release(p, it.next())) {
                        it.remove();
                        any = true;
                    }
                }
                if (any) sound(p, GulpSound.SPIT);
            }
            case 4 -> {
                int i = indexOf(s, arg);
                if (i >= 0 && release(p, s.contents.get(i))) {
                    s.contents.remove(i);
                    sound(p, GulpSound.SPIT);
                }
            }
            case 5 -> {
                int i = indexOf(s, arg);
                if (i >= 0) {
                    if (PlayerHolding.isPlayerEntry(s.contents.get(i))) {
                        StomachLogic.say(p, Component.literal("Players are only held, never digested."));
                    } else if (hardModeAllowed()) {
                        digest(p, s, s.contents.remove(i), true);
                    } else {
                        StomachLogic.say(p, Component.literal("Digesting is turned off on this server."));
                    }
                }
            }
            case 7 -> openStorage(p, mgr, s);
            case 9 -> swallowFromInventorySlot(p, s, arg);
            case 12, 13 -> {
                // Enchant Stripper buttons on the storage screen: 12 = strip slot 1 into a book, 13 = transfer slot 1 -> slot 2.
                if (GulpServerConfig.ITEM_STORAGE_ENABLED.get() && p.containerMenu instanceof ChestMenu cm
                        && cm.getContainer() == OPEN_VIEWS.get(p.getUUID())
                        && cm.getContainer() instanceof SimpleContainer view) {
                    if (action == 12) EnchantTools.strip(p, s, view); else EnchantTools.transfer(p, s, view);
                }
            }
            case 10 -> {
                if (GulpServerConfig.ITEM_STORAGE_ENABLED.get() && p.containerMenu instanceof ChestMenu cm
                        && cm.getContainer() == OPEN_VIEWS.get(p.getUUID())
                        && cm.getContainer() instanceof SimpleContainer view) {
                    StomachItems.digestFirstSlot(p, s, view, arg);
                }
            }
            case 8 -> {
                // Set your role (arg = Role ordinal).
                Role newRole = Role.byId(arg);
                if (newRole != null && newRole != s.role) {
                    s.role = newRole;
                    if (!newRole.canEat()) {
                        // Someone who can't swallow can't keep holding anyone: everything is let out.
                        boolean any = false;
                        Iterator<CompoundTag> it = s.contents.iterator();
                        while (it.hasNext()) {
                            if (release(p, it.next())) {
                                it.remove();
                                any = true;
                            }
                        }
                        if (any) sound(p, GulpSound.SPIT);
                    }
                    StomachLogic.say(p, Component.literal("Your role is now " + newRole.label + ". " + newRole.description));
                    RolesPacket.broadcast(p.getServer());
                }
            }
            case 6 -> {
                // arg = perk index in the low 8 bits, how many ranks to buy above that (0 or 1 means one)
                Perk perk = Perk.byIndex(arg & 0xFF);
                int count = Math.max(1, Math.min(100, arg >> 8));
                if (perk != null && s.level < perk.minLevel) {
                    say(p, Component.literal(perk.title + " unlocks at stomach level " + perk.minLevel + "."));
                } else if (perk != null) {
                    int bought = Math.min(count, Math.min(s.points, perk.maxRank - s.rank(perk)));
                    if (bought > 0) {
                        s.ranks[perk.ordinal()] += bought;
                        s.points -= bought;
                        p.level().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.2f);
                    }
                }
            }
            default -> { }
        }
        mgr.setDirty();
        sync(p, s);
    }

    private static int indexOf(Stomach s, int uid) {
        for (int i = 0; i < s.contents.size(); i++) {
            if (s.contents.get(i).getInt("Uid") == uid) return i;
        }
        return -1;
    }

    private static void swallow(ServerPlayer p, Stomach s, int targetId) {
        if (s.cooldown > 0) return;
        // Prefer what the player's client says they were looking at; fall back to our own forgiving search.
        Entity target = null;
        if (targetId >= 0) {
            Entity candidate = p.serverLevel().getEntity(targetId);
            if (candidate != null && TargetFinder.valid(candidate) && TargetFinder.withinReach(p, candidate, 2.0)) {
                target = candidate;
            }
        }
        if (target == null) target = TargetFinder.find(p, TargetFinder.REACH);
        if (target instanceof ItemEntity item) {
            swallowItem(p, s, item);
            return;
        }
        if (target instanceof ServerPlayer victim) {
            // Players are never swallowed on the spot: they get a request they can accept or decline.
            PlayerHolding.request(p, s, victim, StomachManager.get(p.getServer()));
            return;
        }
        if (!(target instanceof LivingEntity le)) {
            StomachLogic.say(p, Component.literal("Nothing swallowable in reach."));
            return;
        }
        // Multiplayer: don't let anyone swallow (or digest) another player's tamed pet or horse.
        if (le instanceof TamableAnimal pet && pet.isTame() && pet.getOwnerUUID() != null
                && !pet.getOwnerUUID().equals(p.getUUID())) {
            StomachLogic.say(p, Component.literal("That pet belongs to someone else."));
            return;
        }
        if (le instanceof AbstractHorse horse && horse.isTamed() && horse.getOwnerUUID() != null
                && !horse.getOwnerUUID().equals(p.getUUID())) {
            StomachLogic.say(p, Component.literal("That animal belongs to someone else."));
            return;
        }
        float size = Math.max(0.05f, le.getBbWidth() * le.getBbWidth() * le.getBbHeight());
        if (size > s.capacity()) {
            StomachLogic.say(p, Component.literal("Too big for your stomach! (needs " + Stomach.fmtVolume(size) + ", capacity " + Stomach.fmtVolume(s.capacity()) + ")"));
            return;
        }
        if (s.used() + size > s.capacity()) {
            StomachLogic.say(p, Component.literal("Your stomach is too full."));
            return;
        }
        if (s.contents.size() >= Stomach.MAX_MOBS) {
            StomachLogic.say(p, Component.literal("You can't hold more than " + Stomach.MAX_MOBS + " creatures at once."));
            return;
        }
        le.stopRiding();
        McaIntegration.onSwallow(p, s, le); // MCA villagers react first, so what they feel is saved with them
        CompoundTag tag = new CompoundTag();
        if (!le.save(tag)) return;

        CompoundTag entry = new CompoundTag();
        entry.put("Entity", tag);
        entry.putFloat("Size", size);
        entry.putFloat("MaxHealth", le.getMaxHealth());
        entry.putInt("Digest", 0);
        // Bigger = slower, but gently: 13s for a cow, 22s for an iron golem, about 3 minutes for a size-500 monster.
        entry.putInt("DigestTime", 100 + (int) (150 * Math.sqrt(size)));
        // Keep the name as a translatable component, so every player sees it in their own language, and
        // modded mobs and name-tagged mobs show properly. "Name" is a plain-text backup for older saves.
        Component displayName = le.getName();
        entry.putString("Name", displayName.getString());
        entry.putString("NameJson", Component.Serializer.toJson(displayName));
        entry.putInt("Uid", p.getRandom().nextInt());

        le.discard();
        s.contents.add(entry);
        finishSwallow(p, s, displayName);
    }

    /** Starts the swallow cooldown, plays the sound and shows the message. */
    static void finishSwallow(ServerPlayer p, Stomach s, Component what) {
        // Quick Gulp perk: every rank takes 0.075s off the cooldown anywhere, and 0.15s when looking up.
        // 2s with no perk; with 10 ranks it is 1.25s anywhere and 0.5s when looking up.
        int quick = s.rank(Perk.QUICK);
        float reduction = (p.getXRot() < -30 ? 3f : 1.5f) * quick; // in ticks (20 per second)
        s.cooldown = Math.max(10, 40 - Math.round(reduction));
        sound(p, GulpSound.SWALLOW);
        StomachLogic.say(p, Component.literal("Gulp! Swallowed ").append(what));
    }

    // ------------------------------------------------------------ item storage

    /** Swallows a dropped item stack into the item storage. */
    private static void swallowItem(ServerPlayer p, Stomach s, ItemEntity item) {
        if (!GulpServerConfig.ITEM_STORAGE_ENABLED.get()) {
            StomachLogic.say(p, Component.literal("Item storage is turned off on this server."));
            return;
        }
        if (p.containerMenu != p.inventoryMenu) return; // the storage screen is open: don't change it underneath them
        ItemStack stack = item.getItem();
        int before = stack.getCount();
        ItemStack left = s.insertItem(stack);
        int taken = before - left.getCount();
        if (taken <= 0) {
            StomachLogic.say(p, Component.literal("Your item storage is full."));
            return;
        }
        if (left.isEmpty()) item.discard();
        else item.setItem(left);
        finishSwallow(p, s, Component.literal(taken + "x ").append(stack.getHoverName()));
    }

    /**
     * The inventory-screen hotkey: swallows the item under the mouse (the whole stack, or just one if asked) into the
     * item storage. Only the player's own inventory and hotbar count: never armor, the offhand or crafting slots.
     */
    private static void swallowFromInventorySlot(ServerPlayer p, Stomach s, int arg) {
        if (!GulpServerConfig.ITEM_STORAGE_ENABLED.get()) {
            say(p, Component.literal("Item storage is turned off on this server."));
            return;
        }
        if (p.containerMenu != p.inventoryMenu) return; // only from the plain inventory screen
        int slotId = arg & 0xFFFF;
        boolean justOne = (arg & (1 << 16)) != 0;
        if (slotId < 0 || slotId >= p.inventoryMenu.slots.size()) return;
        Slot slot = p.inventoryMenu.getSlot(slotId);
        if (!(slot.container instanceof Inventory) || slot.getSlotIndex() >= 36 || !slot.hasItem()) return;

        ItemStack stack = slot.getItem();
        // Sharp / hazardous items hurt. The client shows a warning and sends "confirmed" (bit 17); the server insists on it.
        boolean hazardous = HazardItems.isHazardous(stack) && !p.isCreative();
        boolean confirmed = (arg & (1 << 17)) != 0;
        if (hazardous && !confirmed) {
            say(p, Component.literal("Careful! Swallowing " + stack.getHoverName().getString() + " would hurt you. Confirm the warning first."));
            return;
        }
        int wanted = justOne ? 1 : stack.getCount();
        ItemStack offer = stack.copy();
        offer.setCount(wanted);
        ItemStack left = s.insertItem(offer);
        int taken = wanted - left.getCount();
        if (taken <= 0) {
            say(p, Component.literal("Your item storage is full."));
            return;
        }
        Component name = stack.getHoverName();
        slot.remove(taken); // takes them out of the inventory slot
        p.containerMenu.broadcastChanges();
        sound(p, GulpSound.SWALLOW);
        say(p, Component.literal("Gulp! Swallowed " + taken + "x ").append(name));
        if (hazardous) {
            // Never below 2 hearts, so a hazard can hurt but not kill.
            float damage = Math.min(GulpServerConfig.HAZARD_SELF_DAMAGE.get().floatValue(), p.getHealth() - 4f);
            if (damage > 0f) {
                p.hurt(p.damageSources().generic(), damage);
                say(p, Component.literal("Ouch! " + name.getString() + " hurt on the way down."));
            }
        }
    }

    /** Opens the item storage as a chest-style screen (shift-click moves items in and out like a chest). */
    private static void openStorage(ServerPlayer p, StomachManager mgr, Stomach s) {
        if (!GulpServerConfig.ITEM_STORAGE_ENABLED.get()) {
            StomachLogic.say(p, Component.literal("Item storage is turned off on this server."));
            return;
        }
        int rows = s.storageRows();
        int slots = rows * 9;
        SimpleContainer view = new SimpleContainer(slots);
        for (int i = 0; i < slots; i++) view.setItem(i, s.storage.get(i));
        // Every change made in the screen is copied straight back into the saved storage.
        OPEN_VIEWS.put(p.getUUID(), view);
        view.addListener(c -> {
            for (int i = 0; i < slots; i++) s.storage.set(i, view.getItem(i));
            mgr.setDirty();
            sync(p, s); // keeps the client (and a CPM belly) up to date as you move items in and out
        });
        MenuType<ChestMenu> type = switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
        p.openMenu(new SimpleMenuProvider((id, inv, player) -> new ChestMenu(type, id, inv, view, rows),
                Component.literal(Gulp.STORAGE_TITLE)));
    }

    // ------------------------------------------------------------ per-tick

    public static void tickPlayer(StomachManager mgr, ServerPlayer p, Stomach s) {
        if (s.cooldown > 0) s.cooldown--;
        // MCA: the "predator" status fades by 1 every few seconds, so villagers eventually forgive you.
        if (s.predator > 0 && p.tickCount % (GulpServerConfig.MCA_PREDATOR_DECAY_SECONDS.get() * 20) == 0) {
            s.predator--;
            sync(p, s);
            mgr.setDirty();
        }
        // Item storage: in hard mode, gear is broken down and trash dissolves. Works with no mobs inside.
        StomachItems.tick(p, mgr, s, s.hard && hardModeAllowed());
        if (s.contents.isEmpty()) return;

        boolean changed = false;
        boolean dissolving = false;
        boolean hardNow = s.hard && hardModeAllowed();
        int healAmount = s.rank(Perk.HEALING);
        Iterator<CompoundTag> it = s.contents.iterator();
        while (it.hasNext()) {
            CompoundTag e = it.next();
            if (PlayerHolding.isPlayerEntry(e)) {
                // A swallowed player: only held. No digesting, no struggling, no healing.
                if (!PlayerHolding.tickEntry(p, e)) {
                    it.remove();
                    changed = true;
                }
                continue;
            }
            e.putInt("HeldTicks", e.getInt("HeldTicks") + 1); // how long this mob has been inside
            if (hardNow) {
                // Phase 1: the digestion bar fills up.
                int d = e.getInt("Digest");
                int digestTime = e.getInt("DigestTime");
                if (d < digestTime) {
                    d++;
                    e.putInt("Digest", d);
                }
                // Phase 2 (grace period): the bar is full, so the mob now takes damage until it runs out of health.
                if (d >= digestTime) {
                    CompoundTag ent = e.getCompound("Entity");
                    float maxHealth = Math.max(1f, e.getFloat("MaxHealth"));
                    if (!e.getBoolean("Dissolving")) {
                        e.putBoolean("Dissolving", true);
                        if (ent.getFloat("Health") <= 0f) ent.putFloat("Health", maxHealth); // no health recorded: start full
                    }
                    float health = ent.getFloat("Health") - maxHealth / (DISSOLVE_SECONDS * 20f);
                    if (health <= 0f) {
                        it.remove();
                        digest(p, s, e, false); // now it dies and drops its loot
                        changed = true;
                        continue;
                    }
                    ent.putFloat("Health", health);
                    dissolving = true;
                }
                if (p.tickCount % 20 == 0 && p.getHealth() > 4f
                        && p.getRandom().nextFloat() < STRUGGLE_CHANCE_PER_SECOND) {
                    // Hard mode only, and only now and then: the mob fights back.
                    // Bigger mobs hit harder. Armor reduces it, Iron Stomach reduces it by 5% per rank,
                    // and it can never take you below 2 hearts.
                    float strength = Math.min(STRUGGLE_MAX_DAMAGE,
                            STRUGGLE_BASE_DAMAGE + STRUGGLE_PER_VOLUME * e.getFloat("Size"));
                    float damage = strength * Math.max(0f, 1f - 0.05f * s.rank(Perk.IRON))
                            * GulpServerConfig.STRUGGLE_DAMAGE_MULTIPLIER.get().floatValue();
                    damage = Math.min(damage, p.getHealth() - 4f);
                    if (damage > 0f) {
                        p.hurt(p.level().damageSources().generic(), damage);
                        StomachLogic.say(p, Component.empty().append(entryName(e)).append(" struggles inside you!"));
                    }
                }
            } else {
                // Healing Stomach perk
                if (healAmount > 0 && p.tickCount % 100 == 0) {
                    CompoundTag ent = e.getCompound("Entity");
                    float maxHealth = e.getFloat("MaxHealth");
                    float heal = Math.max(1f, maxHealth * 0.01f * healAmount); // 1% of its max HP per rank, at least 1
                    ent.putFloat("Health", Math.min(maxHealth, ent.getFloat("Health") + heal));
                }
            }
        }

        // Soft mode still earns XP: a completely full stomach earns a level's worth every few minutes,
        // so the fuller you are, the faster you level (and a bigger stomach means more to carry).
        if (!hardNow && p.tickCount % 20 == 0) {
            float fill = (float) Math.min(1.0, s.used() / s.capacity());
            gainXp(p, s, fill * Stomach.xpForNext(s.level) / Stomach.SOFT_SECONDS_PER_LEVEL);
        }

        if (p.tickCount % 20 == 0) mgr.setDirty();
        // Big stomachs send a lot of data, so they sync a bit less often.
        int syncEvery = s.contents.size() > 20 ? 20 : (dissolving ? 5 : 10);
        if (changed || p.tickCount % syncEvery == 0) sync(p, s);
    }

    /** @param manual true when the player used the Digest button on the stomach screen */
    private static void digest(ServerPlayer p, Stomach s, CompoundTag entry, boolean manual) {
        ServerLevel w = p.serverLevel();
        // Better Loot perk: 1 Looting level per 2 ranks (an odd rank is a coin flip for the extra level), up to Looting V.
        int lootRank = s.rank(Perk.RICH);
        int looting = lootRank / 2 + ((lootRank % 2 == 1 && p.getRandom().nextFloat() < 0.5f) ? 1 : 0);
        EntityType.create(entry.getCompound("Entity"), w).ifPresent(e -> {
            e.setUUID(UUID.randomUUID());
            e.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f);
            // The mob is never added to the world, so nobody sees it for even a moment. Its loot and XP are
            // intercepted (see ServerEvents) and go straight into your inventory instead of onto the ground.
            if (e instanceof LivingEntity le) {
                McaIntegration.onDigest(p, s, le); // MCA: predator status, witnesses panic
                le.setSilent(true); // no death groan: the Digestion sound plays instead
                digestingPlayer = p;
                digestingMob = le;
                pendingLooting = looting;
                itemsGiven = 0;
                try {
                    // Credit the kill to the player so loot tables apply. Scaled to the mob's health so even a
                    // giant modded mob dies from it.
                    le.hurt(w.damageSources().playerAttack(p), Math.max(10000f, le.getMaxHealth() * 2f));
                    if (le.isAlive()) le.kill(); // something blocked the hit: make sure it can't walk away
                } catch (RuntimeException ex) {
                    // A modded mob's death code threw an error. Never let that crash the server: let it die normally.
                    LOGGER.warn("Vore Mod: {} threw an error while being digested; letting it die normally instead.", le.getType(), ex);
                    try {
                        w.addFreshEntity(le);
                        le.kill();
                    } catch (RuntimeException ex2) {
                        LOGGER.warn("Vore Mod: that mob also couldn't be killed normally.", ex2);
                    }
                } finally {
                    pendingLooting = 0;
                    digestingPlayer = null;
                    digestingMob = null;
                }
                if (le.isAlive()) {
                    // Something refused to die. Let it out rather than lose it.
                    le.setSilent(false);
                    w.addFreshEntity(le);
                }
            }
        });
        if (itemsGiven > 0) {
            p.level().playSound(null, p.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.25f, 1.3f);
            itemsGiven = 0;
        }
        // Hearty Meal perk: heal 0.5 HP per rank for every mob digested.
        int hearty = s.rank(Perk.HEARTY);
        if (hearty > 0) p.heal(0.5f * hearty);
        sound(p, manual ? GulpSound.DIGEST_BUTTON : GulpSound.DIGEST);
        // Gourmet perk: +5% XP per rank, digesting only.
        gainXp(p, s, Stomach.digestXp(entry.getFloat("MaxHealth")) * (1f + 0.05f * s.rank(Perk.GOURMET)));
    }

    /** @return false if the mob couldn't be recreated (so the caller keeps it instead of losing it) */
    public static boolean release(ServerPlayer p, CompoundTag entry) {
        if (PlayerHolding.isPlayerEntry(entry)) return PlayerHolding.releaseEntry(p, entry);
        ServerLevel w = p.serverLevel();
        Entity e = EntityType.create(entry.getCompound("Entity"), w).orElse(null);
        if (e == null) return false;
        Vec3 fwd = p.getViewVector(1.0f).scale(1.5);
        float yaw = p.getYRot() + 180f;
        e.moveTo(p.getX() + fwd.x, p.getY() + 0.5, p.getZ() + fwd.z, yaw, 0f);
        if (!w.noCollision(e)) {
            // Not enough room in front of you (a wall, say): put it where you're standing instead of inside a block.
            e.moveTo(p.getX(), p.getY() + 0.1, p.getZ(), yaw, 0f);
        }
        e.setDeltaMovement(Vec3.ZERO);
        e.fallDistance = 0f;
        boolean added = w.addFreshEntity(e);
        if (!added) {
            e.setUUID(UUID.randomUUID()); // an old copy with the same id is still around
            added = w.addFreshEntity(e);
        }
        if (added) {
            McaIntegration.onRelease(p, StomachManager.get(p.getServer()).of(p.getUUID()), e, entry.getInt("HeldTicks"));
        }
        return added;
    }

    /** Called when a player dies: let everything out so nothing is lost. */
    public static void releaseAll(ServerPlayer p) {
        StomachManager mgr = StomachManager.get(p.getServer());
        Stomach s = mgr.of(p.getUUID());
        s.contents.removeIf(e -> release(p, e));
        // Stored items drop like an inventory would (unless the keepInventory game rule is on).
        if (!p.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            for (int i = 0; i < s.storage.size(); i++) {
                ItemStack stack = s.storage.get(i);
                if (!stack.isEmpty()) {
                    p.drop(stack, true, false);
                    s.storage.set(i, ItemStack.EMPTY);
                }
            }
        }
        mgr.setDirty();
        sync(p, s);
    }

    private static void levelUp(ServerPlayer p, Stomach s) {
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1f);
        // The message itself is shown by the client, so each player can turn it off in Settings.
        Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new LevelUpPacket(s.level, s.points));
    }

    // ------------------------------------------------------------ helpers

    /** Sends a short action-bar message to a player. Their Settings decide whether it is actually shown. */
    static void say(ServerPlayer p, Component text) {
        Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new MessagePacket(text));
    }

    /** Tells the player (and anyone nearby) to play a sound. Each client chooses what it actually plays. */
    static void sound(ServerPlayer p, GulpSound type) {
        Net.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new SoundPacket(type.ordinal(), p.getX(), p.getY(), p.getZ()));
    }

    /** The name of a swallowed mob as a Component (falls back to the plain text name for older saves). */
    public static Component entryName(CompoundTag entry) {
        if (entry.contains("NameJson")) {
            Component parsed = Component.Serializer.fromJson(entry.getString("NameJson"));
            if (parsed != null) return parsed;
        }
        return Component.literal(entry.getString("Name"));
    }

    private static boolean hardModeAllowed() {
        return GulpServerConfig.HARD_MODE_ENABLED.get();
    }

    /** Adds XP (scaled by the server's xpMultiplier, with fractions rounded randomly) and handles level-ups. */
    static void gainXp(ServerPlayer p, Stomach s, float amount) {
        float scaled = amount * GulpServerConfig.XP_MULTIPLIER.get().floatValue();
        int whole = (int) scaled;
        if (p.getRandom().nextFloat() < scaled - whole) whole++;
        if (whole > 0 && s.addXp(whole) > 0) levelUp(p, s);
    }

    private static String fmt(double d) { return String.format("%.1f", d); }

    public static void sync(ServerPlayer p, Stomach s) {
        for (CompoundTag e : s.contents) {
            if (!e.contains("Uid")) e.putInt("Uid", p.getRandom().nextInt()); // older entries
        }
        Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), SyncPacket.from(s));

        // Tell this player and everyone who can see them how full the belly is.
        float ratio = (float) Math.min(1.0, s.used() / s.capacity());
        Net.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new BellyPacket(p.getUUID(), ratio));
    }
}
