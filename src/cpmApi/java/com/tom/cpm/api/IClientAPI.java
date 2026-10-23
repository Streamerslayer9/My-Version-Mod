package com.tom.cpm.api;

/** STAND-IN for CPM's real interface (compile only, never in the jar): only the two methods this mod calls. */
public interface IClientAPI {
    boolean playAnimation(String name, int value);

    int getAnimationMaxValue(String name);
}
