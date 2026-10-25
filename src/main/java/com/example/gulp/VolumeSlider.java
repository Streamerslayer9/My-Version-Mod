package com.example.gulp;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** A 0% - 200% volume slider for one sound. The middle of the slider is 100%. Moves in steps of 5%. */
public class VolumeSlider extends AbstractSliderButton {
    private final GulpSound sound;

    public VolumeSlider(int x, int y, int width, int height, GulpSound sound) {
        super(x, y, width, height, Component.empty(), GulpConfig.volume(sound) / 2.0);
        this.sound = sound;
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(sound.label + ": " + Math.round(GulpConfig.volume(sound) * 100) + "%"));
    }

    @Override
    protected void applyValue() {
        // 200% / 40 steps = 5% each, and 100% lands exactly in the middle.
        this.value = Math.round(this.value * 40.0) / 40.0;
        GulpConfig.setVolume(sound, (float) (this.value * 2.0));
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        super.onRelease(mouseX, mouseY);
        GulpConfig.save();
        ClientSounds.preview(sound); // let them hear it
    }
}
