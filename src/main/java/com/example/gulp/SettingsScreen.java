package com.example.gulp;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Client-side settings. Saved to config/gulp-client.properties. */
public class SettingsScreen extends Screen {
    private static final int W = 250;
    private static final int H = 272;

    private Button bellyButton, levelUpButton, actionButton, roleButton, perkMenuButton, cpmButton, instantButton, roleTagButton;
    private Role shownRole;

    public SettingsScreen() {
        super(Component.literal("Gulp Settings"));
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    private void refresh() {
        bellyButton.setMessage(Component.literal("Belly style: " + GulpConfig.bellyStyle.label));
        levelUpButton.setMessage(Component.literal("Level-up messages: " + (GulpConfig.levelUpMessages ? "ON" : "OFF")));
        actionButton.setMessage(Component.literal("Action messages: " + (GulpConfig.actionMessages ? "ON" : "OFF")));
        cpmButton.setMessage(Component.literal("CPM belly sliders: " + (GulpConfig.cpmSliders ? "ON" : "OFF")
                + (CpmBridge.available() ? "" : " (CPM not found)")));
        roleTagButton.setMessage(Component.literal("Role in name tags: " + (GulpConfig.showRoleTags ? "ON" : "OFF")));
        instantButton.setMessage(Component.literal("Instant digest button: " + (ClientState.isInstantDigest() ? "ON" : "OFF")));
        perkMenuButton.setMessage(Component.literal("Perk menu: " + (GulpConfig.legacyPerkMenu ? "Legacy" : "New look")));
        Role role = ClientState.role();
        roleButton.setMessage(Component.literal("Role: ").append(Component.literal(role.label).withStyle(st -> st.withColor(role.color & 0xFFFFFF))));
        if (role != shownRole) { // the tooltip is only rebuilt when the role changes
            shownRole = role;
            roleButton.setTooltip(Tooltip.create(Component.literal(role.description + "\n\nClick to switch: Pred, Prey, Both, Neither.")));
        }
    }

    @Override
    protected void init() {
        ScreenLoop.start();
        int x = left();
        int y = top();

        bellyButton = Button.builder(Component.empty(), btn -> {
            GulpConfig.bellyStyle = GulpConfig.bellyStyle.next();
            GulpConfig.save();
            refresh();
        }).bounds(x + 15, y + 22, W - 30, 20).build();

        levelUpButton = Button.builder(Component.empty(), btn -> {
            GulpConfig.levelUpMessages = !GulpConfig.levelUpMessages;
            GulpConfig.save();
            refresh();
        }).bounds(x + 15, y + 46, W - 30, 20).build();

        // Your role is kept on the server (it decides who can swallow whom), not in your local settings file.
        roleButton = Button.builder(Component.empty(), btn -> Net.send(8, ClientState.role().next().ordinal()))
                .bounds(x + 15, y + 118, W - 30, 20).build();

        // The short action-bar messages when you do something: Gulp! Swallowed Cow, Too big for your stomach, ...
        actionButton = Button.builder(Component.empty(), btn -> {
            GulpConfig.actionMessages = !GulpConfig.actionMessages;
            GulpConfig.save();
            refresh();
        }).bounds(x + 15, y + 70, W - 30, 20).build();

        perkMenuButton = Button.builder(Component.empty(), btn -> {
            GulpConfig.legacyPerkMenu = !GulpConfig.legacyPerkMenu;
            GulpConfig.save();
            refresh();
        }).bounds(x + 15, y + 142, W - 30, 20).build();

        // Custom Player Models: your model's belly animations follow how full you are.
        cpmButton = Button.builder(Component.empty(), btn -> {
            GulpConfig.cpmSliders = !GulpConfig.cpmSliders;
            GulpConfig.save();
            refresh();
        }).bounds(x + 15, y + 166, W - 30, 20).build();

        // Whether the Digest button shows next to each mob on the stomach screen. Kept on the server, off by default.
        instantButton = Button.builder(Component.empty(), btn -> Net.send(17, ClientState.isInstantDigest() ? 0 : 1))
                .bounds(x + 15, y + 190, W - 30, 20).build();
        instantButton.setTooltip(Tooltip.create(Component.literal(
                "Shows a Digest button next to each mob in your stomach that digests it right away. Off by default.")));
        addRenderableWidget(instantButton);
        // A coloured [Pred] / [Prey] / [Both] / [Neither] after every player's name above their head. Only on your screen.
        roleTagButton = Button.builder(Component.empty(), btn -> {
            GulpConfig.showRoleTags = !GulpConfig.showRoleTags;
            GulpConfig.save();
            ClientRoleTags.refreshAll();
            refresh();
        }).bounds(x + 15, y + 214, W - 30, 20).build();
        addRenderableWidget(roleTagButton);
        addRenderableWidget(bellyButton);
        addRenderableWidget(cpmButton);
        addRenderableWidget(actionButton);
        addRenderableWidget(perkMenuButton);
        addRenderableWidget(roleButton);
        addRenderableWidget(levelUpButton);
        addRenderableWidget(Button.builder(Component.literal("Sounds..."), btn -> this.minecraft.setScreen(new SoundsScreen()))
                .bounds(x + 15, y + 94, W - 30, 20).build());

        // With a role that can't swallow there is no stomach menu, so Back just closes.
        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> {
                    if (ClientState.role().canEat()) this.minecraft.setScreen(new StomachScreen()); else onClose();
                })
                .bounds(x + 8, y + H - 28, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), btn -> onClose())
                .bounds(x + W - 68, y + H - 28, 60, 20).build());

        refresh();
    }

    @Override
    public void tick() {
        refresh(); // the role label follows what the server says
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = left();
        int y = top();

        g.fill(x, y, x + W, y + H, 0xE6141414);
        g.fill(x, y, x + W, y + 1, 0xFF666666);
        g.fill(x, y + H - 1, x + W, y + H, 0xFF666666);
        g.fill(x, y, x + 1, y + H, 0xFF666666);
        g.fill(x + W - 1, y, x + W, y + H, 0xFF666666);

        g.drawString(font, "Settings", x + 8, y + 8, 0xFFFFFF, true);

        super.render(g, mouseX, mouseY, partialTick);
    }
}
