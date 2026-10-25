package com.example.gulp;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The "new look" perk menu: every perk is a node around a central hub, with a progress ring showing its ranks.
 * There are deliberately NO lines between the nodes, because nothing here unlocks anything else: you can buy any
 * perk, any time you have points. The coloured dotted orbit just groups the perks by type.
 * Click a node to buy 1 rank (Shift = 5, Ctrl = as many as you can afford). Hover for details.
 * The old list-style menu is still available as the "Legacy" menu (see PerksScreen).
 */
public class PerkTreeScreen extends Screen {
    private static final int W = 320;
    private static final int H = 272;

    private static final int GROWTH = 0xFF5BD6A0;
    private static final int LOOT = 0xFF58A6FF;
    private static final int SURVIVAL = 0xFFFF6B6B;
    private static final int GOLD = 0xFFFFD34D;
    private static final int DIM = 0xFF353541;

    private static final int NODE_R = 13;   // the dark disc behind each icon
    private static final int RING_R = 17;   // the progress ring around it
    private static final int SEGMENTS = 48; // dots in a progress ring
    private static final int RX = 104;      // nodes sit on an ellipse this wide...
    private static final int RY = 60;       // ...and this tall
    private static final int ORBIT_RX = 138; // decorative dotted orbit (it does not touch the nodes)
    private static final int ORBIT_RY = 84;
    private static final int CENTER_Y = 106;
    private static final int DETAIL_Y = 192;
    private static final int DETAIL_H = 52;

    private final List<Perk> order = new ArrayList<>();
    private final int[] starX = new int[45];
    private final int[] starY = new int[45];

    public PerkTreeScreen() {
        super(Component.literal("Stomach Perks"));
        // Perks of the same kind sit side by side, so each kind forms an arc of the orbit.
        order.add(Perk.ROOMY);
        order.add(Perk.GOURMET);
        order.add(Perk.QUICK);
        order.add(Perk.RICH);
        order.add(Perk.STRIP);
        order.add(Perk.UNIVERSAL);
        order.add(Perk.HEARTY);
        order.add(Perk.IRON);
        order.add(Perk.HEALING);
        order.add(Perk.MENDING);
        for (Perk p : Perk.values()) {
            if (!order.contains(p)) order.add(p); // a perk added later still shows up
        }
        Random random = new Random(7); // a fixed sprinkle of faint background stars
        for (int i = 0; i < starX.length; i++) {
            starX[i] = 4 + random.nextInt(W - 8);
            starY[i] = 24 + random.nextInt(H - 52);
        }
    }

    // ------------------------------------------------------------------ layout

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }
    private int centerX() { return left() + W / 2; }
    private int centerY() { return top() + CENTER_Y; }

    private double angle(int i) { return -Math.PI / 2 + 2 * Math.PI * i / order.size(); }
    private int nodeX(int i) { return centerX() + (int) Math.round(Math.cos(angle(i)) * RX); }
    private int nodeY(int i) { return centerY() + (int) Math.round(Math.sin(angle(i)) * RY); }

    private static int colorOf(Perk perk) {
        return switch (perk) {
            case HEALING, IRON, HEARTY, UNIVERSAL, MENDING -> SURVIVAL;
            case RICH, STRIP -> LOOT;
            default -> GROWTH;
        };
    }

    // ------------------------------------------------------------------ drawing helpers

    private static void disc(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy));
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    /** A ring of small dots starting at the top, the first `lit` of them in litColor and the rest in dimColor. */
    private static void ring(GuiGraphics g, int cx, int cy, int radius, int segments, int lit, int litColor, int dimColor) {
        for (int i = 0; i < segments; i++) {
            double a = -Math.PI / 2 + 2 * Math.PI * i / segments;
            int px = cx + (int) Math.round(Math.cos(a) * radius);
            int py = cy + (int) Math.round(Math.sin(a) * radius);
            g.fill(px - 1, py - 1, px + 1, py + 1, i < lit ? litColor : dimColor);
        }
    }

    private static int brighten(int color) {
        int r = (color >> 16) & 0xFF, gr = (color >> 8) & 0xFF, b = color & 0xFF;
        r += (255 - r) * 35 / 100;
        gr += (255 - gr) * 35 / 100;
        b += (255 - b) * 35 / 100;
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    /** What a perk does at a given rank, in a few words. */
    private static String effect(Perk perk, int rank) {
        return switch (perk) {
            case ROOMY -> "+" + rank * 6 + "% capacity";
            case HEALING -> rank + "% max HP / 5s";
            case RICH -> "Looting " + (rank % 2 == 0 ? String.valueOf(rank / 2) : (rank / 2) + " or " + (rank / 2 + 1));
            case QUICK -> String.format("-%.2fs, -%.2fs up", rank * 0.075, rank * 0.15);
            case IRON -> "-" + rank * 5 + "% struggle damage";
            case GOURMET -> "+" + rank * 5 + "% digest XP";
            case HEARTY -> String.format("+%.1f HP per digest", rank * 0.5);
            case UNIVERSAL -> String.format("+%.1f Hunger Bars per %d items", StomachItems.hungerBars(rank), StomachItems.DIGEST_THRESHOLD);
            case STRIP -> rank == 0 ? "Locked" : rank >= 3 ? "No levels lost" : "-" + (3 - rank) + " level(s) per enchant";
            case MENDING -> rank == 0 ? "No repair" : "+" + rank * 2 + " durability / 5s per item";
            default -> "";
        };
    }

    // ------------------------------------------------------------------ widgets and input

    @Override
    protected void init() {
        ScreenLoop.start();
        int x = left();
        int y = top();
        addRenderableWidget(Button.builder(Component.literal("Legacy"), btn -> {
            GulpConfig.legacyPerkMenu = true;
            GulpConfig.save();
            this.minecraft.setScreen(new PerksScreen());
        }).bounds(x + 92, y + 2, 64, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> this.minecraft.setScreen(new StomachScreen()))
                .bounds(x + 8, y + H - 24, 56, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), btn -> onClose())
                .bounds(x + W - 64, y + H - 24, 56, 20).build());
    }

    private int hoveredNode(double mouseX, double mouseY) {
        for (int i = 0; i < order.size(); i++) {
            double dx = mouseX - nodeX(i);
            double dy = mouseY - nodeY(i);
            if (dx * dx + dy * dy <= (RING_R + 2) * (RING_R + 2)) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int i = hoveredNode(mouseX, mouseY);
            if (i >= 0) {
                // Click = 1 rank, Shift+click = 5 ranks, Ctrl+click = as many as you can afford.
                int count = hasControlDown() ? 100 : hasShiftDown() ? 5 : 1;
                Net.send(6, order.get(i).ordinal() | (count << 8));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = left();
        int y = top();
        int cx = centerX();
        int cy = centerY();
        int n = order.size();
        int hovered = hoveredNode(mouseX, mouseY);

        // Panel
        g.fill(x, y, x + W, y + H, 0xE6141418);
        g.fill(x, y, x + W, y + 1, 0xFF666666);
        g.fill(x, y + H - 1, x + W, y + H, 0xFF666666);
        g.fill(x, y, x + 1, y + H, 0xFF666666);
        g.fill(x + W - 1, y, x + W, y + H, 0xFF666666);

        // Faint stars
        for (int i = 0; i < starX.length; i++) g.fill(x + starX[i], y + starY[i], x + starX[i] + 1, y + starY[i] + 1, 0x40FFFFFF);

        // Decorative orbit, coloured by perk type. It passes outside the nodes and connects nothing.
        for (int k = 0; k < 120; k++) {
            double a = -Math.PI / 2 + 2 * Math.PI * k / 120;
            int px = cx + (int) Math.round(Math.cos(a) * ORBIT_RX);
            int py = cy + (int) Math.round(Math.sin(a) * ORBIT_RY);
            int sector = (int) Math.floor((a + Math.PI / 2) / (2 * Math.PI) * n + 0.5) % n;
            g.fill(px, py, px + 2, py + 2, (colorOf(order.get(sector)) & 0x00FFFFFF) | 0x70000000);
        }
        for (int k = 0; k < 60; k++) {
            double a = 2 * Math.PI * k / 60;
            int px = cx + (int) Math.round(Math.cos(a) * 40);
            int py = cy + (int) Math.round(Math.sin(a) * 28);
            g.fill(px, py, px + 1, py + 1, 0xFF373742);
        }

        // Hub: your unspent points
        int points = ClientState.points();
        disc(g, cx, cy, 24, 0xFF1E1E28);
        ring(g, cx, cy, 25, 64, points > 0 ? 64 : 0, GOLD, 0xFF2C2C38);
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy - 12, 0);
        pose.scale(2f, 2f, 1f);
        g.drawCenteredString(font, String.valueOf(points), 0, 0, points > 0 ? GOLD : 0xFF8A8A94);
        pose.popPose();
        g.drawCenteredString(font, points == 1 ? "point" : "points", cx, cy + 8, 0xFFAAAAAA);

        // Nodes
        for (int i = 0; i < n; i++) {
            Perk perk = order.get(i);
            int nx = nodeX(i);
            int ny = nodeY(i);
            int rank = ClientState.rank(perk);
            int max = perk.maxRank;
            boolean maxed = rank >= max;
            boolean canBuy = points > 0 && !maxed;
            int color = maxed ? GOLD : colorOf(perk);
            boolean hot = i == hovered;

            disc(g, nx, ny, NODE_R, hot ? 0xFF2D2D3A : 0xFF1B1B24);
            int lit = rank == 0 ? 0 : Math.max(1, Math.round(SEGMENTS * rank / (float) max));
            ring(g, nx, ny, RING_R, SEGMENTS, lit, hot ? brighten(color) : color, DIM);
            g.renderItem(PerksScreen.iconFor(perk), nx - 8, ny - 8);

            // Rank label sitting on the bottom of the ring
            String label = rank + "/" + max;
            int tw = font.width(label);
            g.fill(nx - tw / 2 - 2, ny + RING_R - 4, nx + tw / 2 + 3, ny + RING_R + 5, 0xFF111118);
            g.drawString(font, label, nx - tw / 2, ny + RING_R - 3,
                    maxed ? GOLD : rank > 0 ? 0xFFFFFFFF : 0xFF888888, false);

            // A little "+" when you can buy a rank right now
            if (canBuy) {
                int bx = nx + NODE_R - 2;
                int by = ny - NODE_R - 4;
                g.fill(bx, by, bx + 8, by + 8, 0xFF2E7D32);
                g.fill(bx + 2, by + 4, bx + 6, by + 5, 0xFFFFFFFF);
                g.fill(bx + 3, by + 2, bx + 4, by + 7, 0xFFFFFFFF);
            }
        }

        // Header
        g.drawString(font, "Stomach Perks", x + 8, y + 8, 0xFFFFFFFF, true);
        String hint = "Click +1   Shift +5   Ctrl max";
        g.drawString(font, hint, x + W - 8 - font.width(hint), y + 8, 0xFF787882, false);

        // Legend
        String[] names = {"Growth", "Loot", "Survival"};
        int[] colors = {GROWTH, LOOT, SURVIVAL};
        for (int i = 0; i < names.length; i++) {
            g.fill(x + 8, y + 27 + i * 10, x + 13, y + 32 + i * 10, colors[i]);
            g.drawString(font, names[i], x + 16, y + 25 + i * 10, 0xFFAAAAAA, false);
        }

        // Details of the perk under the mouse: title, description (wraps to 2 lines), then Current / Next on their own lines
        g.fill(x + 8, y + DETAIL_Y, x + W - 8, y + DETAIL_Y + DETAIL_H, 0xFF0A0A0E);
        if (hovered >= 0) {
            Perk perk = order.get(hovered);
            int rank = ClientState.rank(perk);
            g.drawString(font, perk.title + "  " + rank + "/" + perk.maxRank, x + 12, y + DETAIL_Y + 3,
                    rank >= perk.maxRank ? GOLD : colorOf(perk), false);
            List<FormattedCharSequence> desc = font.split(Component.literal(perk.description), W - 24);
            for (int l = 0; l < Math.min(2, desc.size()); l++) {
                g.drawString(font, desc.get(l), x + 12, y + DETAIL_Y + 13 + l * 9, 0xFFA0A0A0, false);
            }
            g.drawString(font, font.plainSubstrByWidth("Current: " + effect(perk, rank), W - 24),
                    x + 12, y + DETAIL_Y + 32, 0xFFFFFFFF, false);
            if (ClientState.level() < perk.minLevel) {
                g.drawString(font, "Unlocks at stomach level " + perk.minLevel, x + 12, y + DETAIL_Y + 41, 0xFFE0A050, false);
            } else if (rank < perk.maxRank) {
                g.drawString(font, font.plainSubstrByWidth("Next: " + effect(perk, rank + 1), W - 24),
                        x + 12, y + DETAIL_Y + 41, 0xFF9DE0A8, false);
            } else {
                g.drawString(font, "Max rank", x + 12, y + DETAIL_Y + 41, GOLD, false);
            }
        } else {
            g.drawCenteredString(font, "Hover a perk to see what it does.", x + W / 2, y + DETAIL_Y + 22, 0xFF787882);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }
}
