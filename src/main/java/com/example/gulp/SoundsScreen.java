package com.example.gulp;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Sound settings: custom/Minecraft sound set, plus an ON/OFF button and a 0-200% volume slider for each sound. */
public class SoundsScreen extends Screen {
    private static final int W = 250;
    private static final int H = 206;

    private Button setButton;
    private final Button[] toggles = new Button[GulpSound.values().length];
    private final VolumeSlider[] sliders = new VolumeSlider[GulpSound.values().length];

    public SoundsScreen() {
        super(Component.literal("Gulp Sounds"));
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    /** Updates labels and greys out whatever doesn't apply right now. */
    private void refresh() {
        setButton.setMessage(Component.literal("Sounds: " + (GulpConfig.useCustomSounds ? "Custom" : "Minecraft")));
        for (GulpSound sound : GulpSound.values()) {
            int i = sound.ordinal();
            // The screen music only exists in the custom sound set.
            boolean available = sound != GulpSound.SCREEN_LOOP || GulpConfig.useCustomSounds;
            toggles[i].setMessage(Component.literal(GulpConfig.isOn(sound) ? "ON" : "OFF"));
            toggles[i].active = available;
            sliders[i].active = available && GulpConfig.isOn(sound);
        }
    }

    @Override
    protected void init() {
        ScreenLoop.start();
        int x = left();
        int y = top();

        setButton = Button.builder(Component.empty(), btn -> {
            GulpConfig.useCustomSounds = !GulpConfig.useCustomSounds;
            GulpConfig.save();
            ScreenLoop.refresh();
            refresh();
        }).bounds(x + 15, y + 22, W - 30, 20).build();
        addRenderableWidget(setButton);

        for (GulpSound sound : GulpSound.values()) {
            int i = sound.ordinal();
            int ry = y + 50 + i * 24;
            toggles[i] = Button.builder(Component.empty(), btn -> {
                GulpConfig.setOn(sound, !GulpConfig.isOn(sound));
                GulpConfig.save();
                if (sound == GulpSound.SCREEN_LOOP) ScreenLoop.refresh();
                refresh();
            }).bounds(x + 15, ry, 40, 20).build();
            sliders[i] = new VolumeSlider(x + 59, ry, W - 30 - 44, 20, sound);
            addRenderableWidget(toggles[i]);
            addRenderableWidget(sliders[i]);
        }

        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> this.minecraft.setScreen(new SettingsScreen()))
                .bounds(x + 8, y + H - 28, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), btn -> onClose())
                .bounds(x + W - 68, y + H - 28, 60, 20).build());

        refresh();
    }

    @Override
    public void removed() {
        GulpConfig.save();
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

        g.drawString(font, "Sounds", x + 8, y + 8, 0xFFFFFF, true);

        super.render(g, mouseX, mouseY, partialTick);
    }
}
