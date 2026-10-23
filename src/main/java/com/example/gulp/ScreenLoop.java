package com.example.gulp;

import net.minecraft.client.Minecraft;

/** Starts/stops the stomach-screen loop. Screens call start() when they open; Settings calls refresh(). */
public final class ScreenLoop {
    private static ScreenLoopSound current;

    private ScreenLoop() { }

    private static boolean enabled() {
        return GulpConfig.useCustomSounds && GulpConfig.isOn(GulpSound.SCREEN_LOOP);
    }

    public static void start() {
        if (!enabled()) return;
        if (current == null || current.isStopped()) {
            current = new ScreenLoopSound();
            Minecraft.getInstance().getSoundManager().play(current);
        }
    }

    public static void stop() {
        if (current != null) {
            Minecraft.getInstance().getSoundManager().stop(current);
            current = null;
        }
    }

    /** Call after a sound setting changes while a Gulp screen is open. */
    public static void refresh() {
        if (enabled()) start();
        else stop();
    }
}
