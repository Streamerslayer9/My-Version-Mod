package com.example.gulp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** The looping stomach-screen music. Stops by itself as soon as none of the Gulp screens is open. */
public class ScreenLoopSound extends AbstractTickableSoundInstance {
    /**
     * The loop's volume at 100% in Settings (0.0 - 1.0). Kept low so it sits in the background as ambience.
     * The Settings slider scales this from 0% to 200% (so at most twice this value).
     */
    private static final float BASE_VOLUME = 0.25f;

    public ScreenLoopSound() {
        super(Gulp.SCREEN_LOOP.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.looping = true;
        this.delay = 0;
        this.volume = currentVolume();
        this.relative = true;                         // plays flat in your ears, not from a spot in the world
        this.attenuation = SoundInstance.Attenuation.NONE;
    }

    private static float currentVolume() {
        return Math.min(1f, BASE_VOLUME * GulpConfig.volume(GulpSound.SCREEN_LOOP));
    }

    /** Lets the loop exist while the slider is at 0%, so dragging it back up brings the music back. */
    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        Object screen = Minecraft.getInstance().screen;
        if (!(screen instanceof StomachScreen || screen instanceof PerksScreen || screen instanceof PerkTreeScreen
                || screen instanceof SettingsScreen || screen instanceof SoundsScreen)) {
            stop();
            return;
        }
        this.volume = currentVolume(); // follows the slider live
    }
}
