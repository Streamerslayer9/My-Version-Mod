package com.example.gulp;

import net.minecraft.client.gui.screens.Screen;

/** Picks which perk menu to open: the new look, or the legacy list (a setting you can change in either menu). */
public final class PerkMenus {
    private PerkMenus() { }

    public static Screen create() {
        return GulpConfig.legacyPerkMenu ? new PerksScreen() : new PerkTreeScreen();
    }
}
