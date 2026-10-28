package com.example.gulp;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import net.minecraftforge.event.TickEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only: adds a Back button to the item storage screen so you can return to the stomach screen. */
@Mod.EventBusSubscriber(modid = Gulp.ID, value = Dist.CLIENT)
public final class ClientScreenEvents {
    private ClientScreenEvents() { }

    /**
     * In the inventory screen, pressing the "swallow hovered item" key (default V) while the mouse is over an item
     * swallows that stack into your item storage; hold Ctrl to swallow just one. (Swallowing dropped items in the world
     * with V still works as before.)
     */
    @SubscribeEvent
    public static void onInventoryKey(ScreenEvent.KeyPressed.Pre e) {
        if (!(e.getScreen() instanceof InventoryScreen screen)) return;
        if (!ClientModEvents.INVENTORY_SWALLOW_KEY.isActiveAndMatches(InputConstants.getKey(e.getKeyCode(), e.getScanCode()))) return;
        Slot slot = screen.getSlotUnderMouse();
        // Only your own inventory and hotbar (not armor, the offhand or the crafting grid).
        if (slot == null || !slot.hasItem() || !(slot.container instanceof Inventory) || slot.getSlotIndex() >= 36) return;
        Net.send(9, slot.index | (Screen.hasControlDown() ? 1 << 16 : 0));
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post e) {
        // The item storage is a normal chest-style screen with a special title.
        if (!(e.getScreen() instanceof ContainerScreen screen)) return;
        if (!Gulp.STORAGE_TITLE.equals(screen.getTitle().getString())) return;

        int width = 52;
        int x = screen.getGuiLeft() - width - 4;             // just left of the storage window
        if (x < 2) x = screen.getGuiLeft() + screen.getXSize() + 4; // no room on the left: put it on the right

        Button back = Button.builder(Component.literal("Back"), btn -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            mc.player.closeContainer();          // tells the server the storage is closed (anything on your cursor goes back to you)
            mc.setScreen(new StomachScreen());   // and back to the stomach screen
        }).bounds(x, screen.getGuiTop(), width, 20).build();
        e.addListener(back);

        // Enchant Stripper panel under Back: two item boxes (source and target, drawn in onStorageRender, clicked in onBoxClick), and two bars.
        int rank = ClientState.rank(Perk.STRIP);
        int lost = Math.max(0, 3 - rank);
        int barY = screen.getGuiTop() + PANEL_OFFSET_Y + BOX + 4;
        Button strip = new SmallTextButton(x, barY, width, 14, Component.literal("Strip"), btn -> Net.send(12));
        Button transfer = new SmallTextButton(x, barY + 16, width, 14, Component.literal("Transfer"), btn -> Net.send(13));
        final SmallTextButton[] ref = new SmallTextButton[1];
        SmallTextButton destroy = new SmallTextButton(x, barY + 32, width, 14, Component.literal("Destroy"), btn -> {
            if (ref[0].confirmed()) Net.send(16); // the first click only arms it
        });
        ref[0] = destroy;
        destroy.active = rank >= 1;
        destroy.setTooltip(Tooltip.create(Component.literal(rank < 1
                ? "Needs the Enchant Stripper perk (stomach level 50)."
                : "Permanently delete all enchantments on the item in the first box. Nothing is made. Click twice to confirm.")));
        e.addListener(destroy);
        strip.active = rank >= 1;
        transfer.active = rank >= 1 && !ClientState.isHard();
        strip.setTooltip(Tooltip.create(Component.literal(rank < 1
                ? "Needs the Enchant Stripper perk (stomach level 50)."
                : "Strip the enchantments off the gear in the first box onto a plain book in the second box (the book is used up). Levels lost per enchantment: " + lost + ".")));
        transfer.setTooltip(Tooltip.create(Component.literal(rank < 1
                ? "Needs the Enchant Stripper perk (stomach level 50)."
                : ClientState.isHard()
                ? "Soft mode only."
                : "Move the enchantments from the first box onto the item in the second box. Levels lost per enchantment: " + lost + ".")));
        e.addListener(strip);
        e.addListener(transfer);

        // Digest buttons, top right of the title row. They only act on the first slot (the server checks too).
        int bw = 34;
        int bh = 12;
        int by = screen.getGuiTop() + 3;
        int right = screen.getGuiLeft() + screen.getXSize() - 7;
        // The buttons ALWAYS say "Eat", whatever the item is (food, blocks, gear, junk).
        Button digest64 = new SmallTextButton(right - bw, by, bw, bh, Component.literal("Eat 64"), btn -> Net.send(10, 64));
        digest64.setTooltip(Tooltip.create(Component.literal("Eat up to 64 of the item in the first slot. Or just right-click any item in the storage to eat it (hold to keep eating, shift for the whole stack).")));
        Button digest1 = new SmallTextButton(right - bw * 2 - 2, by, bw, bh, Component.literal("Eat 1"), btn -> Net.send(10, 1));
        digest1.setTooltip(Tooltip.create(Component.literal("Eat 1 of the item in the first slot. Or just right-click any item in the storage to eat it.")));
        e.addListener(digest1);
        e.addListener(digest64);
    }

    /** A vanilla button whose label is drawn a little smaller, so "Eat 64" fits inside the small button. */
    private static final class SmallTextButton extends Button {
        private static final float TEXT_SCALE = 0.8f;

        private long armedUntil; // for buttons that need a second click to confirm

        SmallTextButton(int x, int y, int w, int h, Component label, Button.OnPress onPress) {
            super(x, y, w, h, label, onPress, Button.DEFAULT_NARRATION);
        }

        /** First call arms the button for 3 seconds (it shows "Sure?"); a second call within that time returns true. */
        boolean confirmed() {
            long now = System.currentTimeMillis();
            if (now < armedUntil) {
                armedUntil = 0;
                return true;
            }
            armedUntil = now + 3000;
            return false;
        }

        // Replaces the vanilla label drawing (called by Button.renderWidget) with a scaled, centered label.
        public void renderString(GuiGraphics g, Font font, int color) {
            boolean armed = System.currentTimeMillis() < armedUntil;
            Component label = armed ? Component.literal("Sure?") : getMessage();
            if (armed) color = 0xFFFF5555;
            float cx = getX() + getWidth() / 2f;
            float cy = getY() + (getHeight() - font.lineHeight * TEXT_SCALE) / 2f + 0.5f;
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0f);
            g.pose().scale(TEXT_SCALE, TEXT_SCALE, 1f);
            g.drawString(font, label, -font.width(label) / 2, 0, color);
            g.pose().popPose();
        }
    }

    // ------------------------------------------------------------------ Iron Gut progress tracker

    private static final int BAR_W = 90;
    // Enchant Stripper panel layout (matches the sketch: two boxes, then two bars, under the Back button)
    private static final int PANEL_OFFSET_Y = 24;
    private static final int BOX = 24;
    private static final int BLUE = 0xFF3A78C8;

    private static int panelX(ContainerScreen screen) {
        int x = screen.getGuiLeft() - 52 - 4;
        return x < 2 ? screen.getGuiLeft() + screen.getXSize() + 4 : x;
    }

    /** Which Enchant Stripper box (0 or 1) the mouse is over, or -1. */
    private static int boxAt(ContainerScreen screen, double mx, double my) {
        int px = panelX(screen);
        int py = screen.getGuiTop() + PANEL_OFFSET_Y;
        for (int i = 0; i < 2; i++) {
            int bx = px + i * (BOX + 4);
            if (mx >= bx && mx < bx + BOX && my >= py && my < py + BOX) return i;
        }
        return -1;
    }

    /** Clicking a box works like clicking a slot: the server does the pick up / put down / swap with your cursor item. */
    @SubscribeEvent
    public static void onBoxClick(ScreenEvent.MouseButtonPressed.Pre e) {
        if (!(e.getScreen() instanceof ContainerScreen screen)) return;
        if (!Gulp.STORAGE_TITLE.equals(screen.getTitle().getString())) return;
        int box = boxAt(screen, e.getMouseX(), e.getMouseY());
        if (box < 0 || (e.getButton() != 0 && e.getButton() != 1)) return;
        Net.send(15, box | (e.getButton() << 4) | (Screen.hasShiftDown() ? 1 << 5 : 0));
        e.setCanceled(true);
    }

    private static boolean isStorage(ContainerScreen screen) {
        return Gulp.STORAGE_TITLE.equals(screen.getTitle().getString());
    }

    // ---- right-click an item in the stomach storage to eat it; hold to keep eating
    private static int rightHoldSlot = -1;
    private static int rightHoldTicks;

    @SubscribeEvent
    public static void onSlotRightClick(ScreenEvent.MouseButtonPressed.Pre e) {
        if (!GulpConfig.rightClickEat || e.getButton() != 1) return;
        if (!(e.getScreen() instanceof ContainerScreen screen) || !isStorage(screen)) return;
        if (!screen.getMenu().getCarried().isEmpty()) return;            // holding an item: right-click works as usual
        if (boxAt(screen, e.getMouseX(), e.getMouseY()) >= 0) return;    // the Enchant Stripper boxes have their own clicks
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || slot.index >= screen.getMenu().getRowCount() * 9 || !slot.hasItem()) return; // storage slots only
        Net.send(18, slot.index | (Screen.hasShiftDown() ? 1 << 16 : 0));
        rightHoldSlot = slot.index;
        rightHoldTicks = 0;
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onSlotRightRelease(ScreenEvent.MouseButtonReleased.Pre e) {
        if (e.getButton() == 1 && rightHoldSlot >= 0) {
            rightHoldSlot = -1;
            e.setCanceled(true);
        }
    }

    /** While right-click is held on a storage slot: after a short pause, eat one more every 2 ticks until it's gone or released. */
    @SubscribeEvent
    public static void onHoldTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || rightHoldSlot < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof ContainerScreen screen) || !isStorage(screen)
                || GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) != GLFW.GLFW_PRESS) {
            rightHoldSlot = -1;
            return;
        }
        if (!screen.getMenu().getSlot(rightHoldSlot).hasItem()) { // eaten up
            rightHoldSlot = -1;
            return;
        }
        rightHoldTicks++;
        if (rightHoldTicks >= 8 && (rightHoldTicks - 8) % 2 == 0) {
            Net.send(18, rightHoldSlot | (Screen.hasShiftDown() ? 1 << 16 : 0));
        }
    }

    @SubscribeEvent
    public static void onBoxRelease(ScreenEvent.MouseButtonReleased.Pre e) {
        if (!(e.getScreen() instanceof ContainerScreen screen)) return;
        if (!Gulp.STORAGE_TITLE.equals(screen.getTitle().getString())) return;
        if (boxAt(screen, e.getMouseX(), e.getMouseY()) >= 0) e.setCanceled(true);
    }

    /** One of the two boxes: blue frame (brighter when hovered), the item inside, and a tooltip when hovered. */
    private static void renderBox(GuiGraphics g, Font font, int bx, int by, ItemStack stack, String empty, int mx, int my) {
        boolean hover = mx >= bx && mx < bx + BOX && my >= by && my < by + BOX;
        g.fill(bx, by, bx + BOX, by + BOX, hover ? 0xFF6AA8F0 : BLUE);
        g.fill(bx + 1, by + 1, bx + BOX - 1, by + BOX - 1, 0xFF101A2A);
        if (!stack.isEmpty()) {
            g.renderItem(stack, bx + 4, by + 4);
            g.renderItemDecorations(font, stack, bx + 4, by + 4);
        }
        if (mx >= bx && mx < bx + BOX && my >= by && my < by + BOX) {
            if (stack.isEmpty()) g.renderTooltip(font, Component.literal(empty), mx, my);
            else g.renderTooltip(font, stack, mx, my);
        }
    }

    /** Just under the storage window (or just above it when there's no room below). */
    private static int trackerY(ContainerScreen screen) {
        int y = screen.getGuiTop() + screen.getYSize() + 4;
        return y + 22 > screen.height ? Math.max(2, screen.getGuiTop() - 24) : y;
    }

    /**
     * Draws "Digestion Progress: X / 32 Items" with a thin bar, and the payout tooltip when the mouse is over it.
     * The numbers come from ClientState (synced from the server every time the stomach changes).
     */
    @SubscribeEvent
    public static void onStorageRender(ScreenEvent.Render.Post e) {
        if (!(e.getScreen() instanceof ContainerScreen screen)) return;
        if (!Gulp.STORAGE_TITLE.equals(screen.getTitle().getString())) return;

        Font font = Minecraft.getInstance().font;
        GuiGraphics g = e.getGuiGraphics();

        // Enchant Stripper boxes: the source (left) and the target (right). You click items into them like slots.
        int px = panelX(screen);
        int py = screen.getGuiTop() + PANEL_OFFSET_Y;
        renderBox(g, font, px, py, ClientState.stripSlot(0), "Source box: click the enchanted gear in here", e.getMouseX(), e.getMouseY());
        renderBox(g, font, px + BOX + 4, py, ClientState.stripSlot(1), "Target box: click the item to enchant in here (Strip puts the book here)", e.getMouseX(), e.getMouseY());

        int progress = ClientState.digestProgress();
        int threshold = StomachItems.DIGEST_THRESHOLD;
        String text = "Digestion Progress: " + progress + " / " + threshold + " Items";

        int w = Math.max(font.width(text), BAR_W);
        int x = screen.getGuiLeft() + (screen.getXSize() - w) / 2;
        int y = trackerY(screen);
        g.drawString(font, text, x, y, 0xFFFFFF, true);
        // thin progress bar under the text
        int by = y + 11;
        g.fill(x, by, x + w, by + 4, 0xFF000000);
        g.fill(x + 1, by + 1, x + w - 1, by + 3, 0xFF333333);
        int fill = (int) ((w - 2) * Math.min(1f, progress / (float) threshold));
        g.fill(x + 1, by + 1, x + 1 + fill, by + 3, 0xFF5DB85D);

        if (e.getMouseX() >= x && e.getMouseX() <= x + w && e.getMouseY() >= y && e.getMouseY() <= by + 4) {
            float bars = StomachItems.hungerBars(ClientState.rank(Perk.UNIVERSAL));
            String tip = String.format("Eats %d items to restore hunger. Current Payout: %.1f Hunger Bars per %d items.",
                    threshold, bars, threshold);
            g.renderTooltip(font, font.split(Component.literal(tip), 190), e.getMouseX(), e.getMouseY());
        }
    }
}
