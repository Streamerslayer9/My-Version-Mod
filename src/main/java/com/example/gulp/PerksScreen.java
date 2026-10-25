package com.example.gulp;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Spend perk points (1 per level-up). Icons are plain in-game items. */
public class PerksScreen extends Screen {
    private static final int W = 250;
    private static final int ROW_H = 30;
    private static final int HEADER = 24;
    private static final int FOOTER = 28;
    private static final int FULL_H = HEADER + Perk.values().length * ROW_H + FOOTER;
    private static final int TRACK_W = 6; // the scroll bar on the right edge of the list

    private String lastSig = "";
    private final List<Button> buyButtons = new ArrayList<>();
    private int scroll;            // how many pixels the list is scrolled down
    private boolean draggingBar;

    public PerksScreen() {
        super(Component.literal("Stomach Perks"));
    }

    private int left() { return (width - W) / 2; }
    /** The whole window, shortened to fit when the screen is small (the list then scrolls). */
    private int panelH() { return Math.min(FULL_H, Math.max(HEADER + FOOTER + ROW_H * 3, height - 16)); }
    private int top() { return (height - panelH()) / 2; }
    private int viewH() { return panelH() - HEADER - FOOTER; }
    private int contentH() { return Perk.values().length * ROW_H; }
    private int maxScroll() { return Math.max(0, contentH() - viewH()); }
    private void clampScroll() { scroll = Math.max(0, Math.min(maxScroll(), scroll)); }

    /** Swap these for your own pixel art later. */
    static ItemStack iconFor(Perk perk) {
        return switch (perk) {
            case ROOMY -> new ItemStack(Items.BARREL);
            case HEALING -> new ItemStack(Items.GOLDEN_APPLE);
            case RICH -> new ItemStack(Items.EMERALD);
            case QUICK -> new ItemStack(Items.ENDER_EYE);
            case IRON -> new ItemStack(Items.IRON_CHESTPLATE);
            case GOURMET -> new ItemStack(Items.COOKED_BEEF);
            case HEARTY -> new ItemStack(Items.GOLDEN_CARROT);
            case UNIVERSAL -> new ItemStack(Items.COMPOSTER);
            case STRIP -> new ItemStack(Items.ENCHANTED_BOOK);
            case MENDING -> new ItemStack(Items.ANVIL);
        };
    }

    @Override
    protected void init() {
        ScreenLoop.start();
        lastSig = ClientState.signature();
        int x = left();
        int y = top();
        clampScroll();
        buyButtons.clear();
        Perk[] perks = Perk.values();
        for (int i = 0; i < perks.length; i++) {
            Perk perk = perks[i];
            int idx = i;
            int ry = y + HEADER + i * ROW_H - scroll;
            // Click = 1 rank, Shift+click = 5 ranks, Ctrl+click = as many as you can afford.
            Button buy = Button.builder(Component.literal("+"), btn -> {
                        int count = hasControlDown() ? 100 : hasShiftDown() ? 5 : 1;
                        Net.send(6, idx | (count << 8));
                    })
                    .bounds(x + W - 40, ry + 5, 26, 20).build();
            buy.active = ClientState.points() > 0 && ClientState.rank(perk) < perk.maxRank;
            buyButtons.add(buy);
            addRenderableWidget(buy);
        }
        addRenderableWidget(Button.builder(Component.literal("New look"), btn -> {
            GulpConfig.legacyPerkMenu = false;
            GulpConfig.save();
            this.minecraft.setScreen(new PerkTreeScreen());
        }).bounds(x + 92, y + 2, 64, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> this.minecraft.setScreen(new StomachScreen()))
                .bounds(x + 8, y + panelH() - 28, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), btn -> onClose())
                .bounds(x + W - 68, y + panelH() - 28, 60, 20).build());
    }

    @Override
    public void tick() {
        if (!lastSig.equals(ClientState.signature())) {
            clearWidgets();
            init();
        }
    }

    // ------------------------------------------------------------------ scrolling

    private int trackX() { return left() + W - TRACK_W - 3; }
    private int trackTop() { return top() + HEADER; }

    private int thumbH() {
        return Math.max(16, (int) ((long) viewH() * viewH() / contentH()));
    }

    private int thumbY() {
        int travel = viewH() - thumbH();
        return trackTop() + (maxScroll() == 0 ? 0 : (int) ((long) travel * scroll / maxScroll()));
    }

    /** Moves the list so the scroll bar's thumb is centred on the mouse. */
    private void dragTo(double mouseY) {
        int travel = viewH() - thumbH();
        if (travel <= 0) return;
        double t = (mouseY - trackTop() - thumbH() / 2.0) / travel;
        scroll = (int) Math.round(t * maxScroll());
        clampScroll();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (maxScroll() > 0) {
            scroll -= (int) Math.round(delta * (ROW_H / 2.0));
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && maxScroll() > 0 && mouseX >= trackX() && mouseX <= trackX() + TRACK_W
                && mouseY >= trackTop() && mouseY <= trackTop() + viewH()) {
            draggingBar = true;
            dragTo(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (draggingBar) {
            dragTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingBar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        clampScroll();
        int x = left();
        int y = top();
        int h = panelH();

        g.fill(x, y, x + W, y + h, 0xE6141414);
        g.fill(x, y, x + W, y + 1, 0xFF666666);
        g.fill(x, y + h - 1, x + W, y + h, 0xFF666666);
        g.fill(x, y, x + 1, y + h, 0xFF666666);
        g.fill(x + W - 1, y, x + W, y + h, 0xFF666666);

        g.drawString(font, "Stomach Perks", x + 8, y + 8, 0xFFFFFF, true);
        String pts = "Perk points: " + ClientState.points();
        g.drawString(font, pts, x + W - 8 - font.width(pts), y + 8,
                ClientState.points() > 0 ? 0xFFE64A : 0x999999, true);

        int viewTop = y + HEADER;
        int viewBottom = viewTop + viewH();

        // The rows are only drawn inside the list area, so scrolled-out rows are cut off cleanly.
        g.enableScissor(x + 1, viewTop, x + W - 1, viewBottom);
        Perk[] perks = Perk.values();
        for (int i = 0; i < perks.length; i++) {
            Perk perk = perks[i];
            int ry = viewTop + i * ROW_H - scroll;
            if (ry + ROW_H < viewTop || ry > viewBottom) continue;
            g.fill(x + 6, ry + 1, x + W - 12, ry + ROW_H - 2, 0x40FFFFFF);
            g.renderItem(iconFor(perk), x + 12, ry + 7);

            g.drawString(font, perk.title, x + 36, ry + 2, 0xFFFFFF, true);
            String rank = ClientState.rank(perk) + "/" + perk.maxRank;
            g.drawString(font, rank, x + W - 50 - font.width(rank), ry + 2,
                    ClientState.rank(perk) >= perk.maxRank ? 0x55FF55 : 0xBBBBBB, true);

            List<FormattedCharSequence> lines = font.split(Component.literal(perk.description), W - 86);
            for (int l = 0; l < Math.min(2, lines.size()); l++) {
                g.drawString(font, lines.get(l), x + 36, ry + 12 + l * 9, 0x999999, false);
            }
        }
        g.disableScissor();

        // The + buttons follow their rows; a button that isn't fully inside the list is hidden.
        for (int i = 0; i < buyButtons.size(); i++) {
            Button b = buyButtons.get(i);
            int by = viewTop + i * ROW_H - scroll + 5;
            b.setY(by);
            b.visible = by >= viewTop && by + b.getHeight() <= viewBottom;
        }

        // Scroll bar (only when the list doesn't fit)
        if (maxScroll() > 0) {
            int tx = trackX();
            g.fill(tx, viewTop, tx + TRACK_W, viewBottom, 0xFF000000);
            g.fill(tx + 1, viewTop + 1, tx + TRACK_W - 1, viewBottom - 1, 0xFF262626);
            int ty = thumbY();
            boolean hot = draggingBar || (mouseX >= tx && mouseX <= tx + TRACK_W && mouseY >= ty && mouseY <= ty + thumbH());
            g.fill(tx + 1, ty, tx + TRACK_W - 1, ty + thumbH(), hot ? 0xFFDDDDDD : 0xFF9A9A9A);
        }

        String hint = maxScroll() > 0 ? "Shift: +5   Ctrl: max   Scroll for more" : "Shift: +5   Ctrl: max";
        g.drawString(font, hint, x + (W - font.width(hint)) / 2, y + h - 22, 0x777777, false);

        super.render(g, mouseX, mouseY, partialTick);
    }
}
