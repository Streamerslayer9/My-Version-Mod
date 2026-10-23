package com.example.gulp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Read-only view of the swallower's item storage, for the players sitting in their stomach. Refreshes by itself. */
public class SharedStorageScreen extends Screen {
    private static final int COLS = 9;
    private static final int SLOT = 18;

    private final String host;
    private List<ItemStack> items;
    private int refreshTimer;

    private SharedStorageScreen(String host, List<ItemStack> items) {
        super(Component.literal(host + "'s Stomach"));
        this.host = host;
        this.items = items;
    }

    /** Opens the screen, or just updates the items if it is already open. */
    public static void show(String host, List<ItemStack> items) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof SharedStorageScreen open) {
            open.items = items;
        } else if (mc.screen == null) {
            mc.setScreen(new SharedStorageScreen(host, items));
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        // The server only answers while we are still swallowed; ask again every 2 seconds so changes show up.
        if (ClientState.heldMode() == 0) {
            onClose();
            return;
        }
        if (++refreshTimer >= 40) {
            refreshTimer = 0;
            Net.send(7);
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ClientModEvents.SCREEN_KEY.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int rows = Math.max(1, (items.size() + COLS - 1) / COLS);
        int w = COLS * SLOT + 14;
        int h = rows * SLOT + 34;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        g.fill(x, y, x + w, y + h, 0xFFC6C6C6);
        g.drawString(font, host + "'s Stomach (view only)", x + 7, y + 6, 0xFF404040, false);

        ItemStack hovered = ItemStack.EMPTY;
        for (int i = 0; i < Math.max(items.size(), 1); i++) {
            int sx = x + 7 + (i % COLS) * SLOT;
            int sy = y + 20 + (i / COLS) * SLOT;
            g.fill(sx, sy, sx + SLOT, sy + SLOT, 0xFF8B8B8B);
            g.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, 0xFF3F3F3F);
            if (i >= items.size()) continue;
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) continue;
            g.renderItem(stack, sx + 1, sy + 1);
            g.renderItemDecorations(font, stack, sx + 1, sy + 1);
            if (mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT) hovered = stack;
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (!hovered.isEmpty()) g.renderTooltip(font, hovered, mouseX, mouseY);
    }
}
