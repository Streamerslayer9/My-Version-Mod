package com.example.gulp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Client-only: plays a Gulp sound using this player's Settings (sound set, on/off, and volume 0-200%). */
public final class ClientSounds {
    private ClientSounds() { }

    private record Choice(SoundEvent event, float volume) { }

    /**
     * Works out which sound file and playback volume to use, or null if this sound should be silent.
     * Minecraft can't play a file louder than it was recorded, so:
     *   0% - 100%   plays the normal file at 0 - 100% volume
     *   100% - 200% plays the +6 dB file at 50 - 100% volume (so 200% is exactly double, and 100% matches exactly)
     * The Minecraft stand-in sounds can't go above 100%.
     */
    private static Choice choose(GulpSound sound) {
        if (!GulpConfig.isOn(sound)) return null;
        float percent = GulpConfig.volume(sound); // 0.0 - 2.0
        if (percent <= 0f) return null;

        if (!GulpConfig.useCustomSounds) {
            SoundEvent vanilla = sound.vanilla();
            return vanilla == null ? null : new Choice(vanilla, Math.min(percent, 1f));
        }
        if (percent <= 1f) return new Choice(sound.custom(), percent);
        SoundEvent loud = sound.loud();
        return loud == null ? new Choice(sound.custom(), 1f) : new Choice(loud, percent / 2f);
    }

    /** A sound happening in the world (swallow, spit out, digestion). */
    public static void playWorld(int type, double x, double y, double z) {
        GulpSound sound = GulpSound.byIndex(type);
        if (sound == null) return;
        Choice c = choose(sound);
        Minecraft mc = Minecraft.getInstance();
        if (c == null || mc.level == null) return;
        mc.level.playLocalSound(x, y, z, c.event(), SoundSource.PLAYERS, c.volume(), 1.0f, false);
    }

    /** Plays a sound flat in your ears so you can hear the volume you just picked on a slider. */
    public static void preview(GulpSound sound) {
        if (sound == GulpSound.SCREEN_LOOP) return; // already playing live while the settings are open
        Choice c = choose(sound);
        if (c == null) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(c.event(), 1.0f, c.volume()));
    }
}
