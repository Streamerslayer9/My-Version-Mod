package com.example.gulp;

import com.tom.cpm.api.CPMPlugin;
import com.tom.cpm.api.IClientAPI;
import com.tom.cpm.api.ICPMPlugin;
import com.tom.cpm.api.ICommonAPI;

/**
 * Custom Player Models (CPM) finds this class by its annotation, but only if CPM is installed, so nothing here is
 * ever loaded without it. It just hands CPM's client API to CpmBridge; CpmDriver does the real work.
 * (The id is written out instead of using Gulp.ID so this class needs nothing but CPM to load.)
 */
@CPMPlugin
public class CpmPlugin implements ICPMPlugin {
    @Override
    public void initClient(IClientAPI api) {
        CpmBridge.setClient(new CpmBridge.Client() {
            @Override
            public boolean play(String animation, int value) {
                return api.playAnimation(animation, value);
            }

            @Override
            public int maxValue(String animation) {
                return api.getAnimationMaxValue(animation);
            }
        });
    }

    @Override
    public void initCommon(ICommonAPI api) {
        // Nothing needed: the belly is driven from each player's own client.
    }

    @Override
    public String getOwnerModId() {
        return "gulp";
    }
}
