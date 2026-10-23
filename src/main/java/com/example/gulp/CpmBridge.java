package com.example.gulp;

/**
 * The link to Custom Player Models. It holds NO CPM types, so it is safe to load whether or not CPM is installed:
 * CpmPlugin (the only class that touches CPM) fills in `client` when CPM starts, and everything else goes through here.
 */
public final class CpmBridge {
    /** The two things we need from CPM's client API. */
    public interface Client {
        boolean play(String animation, int value);

        int maxValue(String animation);
    }

    private static volatile Client client;

    private CpmBridge() { }

    static void setClient(Client c) { client = c; }

    /** True when CPM is installed and has started. */
    public static boolean available() { return client != null; }

    /** The biggest value the local player's model accepts for this animation, or -1 if the model doesn't have it. */
    public static int maxValue(String animation) {
        Client c = client;
        if (c == null) return -1;
        try {
            return c.maxValue(animation);
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Sets the animation's value on the local player's model. @return false if the model doesn't have it */
    public static boolean play(String animation, int value) {
        Client c = client;
        if (c == null) return false;
        try {
            return c.play(animation, value);
        } catch (Throwable t) {
            return false;
        }
    }
}
