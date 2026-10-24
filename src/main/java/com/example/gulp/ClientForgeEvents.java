package com.example.gulp;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.social.SocialInteractionsScreen;
import net.minecraftforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only game events (Forge event bus): key presses. */
@Mod.EventBusSubscriber(modid = Gulp.ID, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private ClientForgeEvents() { }

    /** Leaving a world or server: stop the music and forget the old stomach so it doesn't leak into the next one. */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientState.reset();
        CpmDriver.reset();
        ScreenLoop.stop();
    }

    /** Ticks left in which a Social Interactions screen (vanilla's P key) opened by the dev chord is closed again. */
    private static int closeSocialTicks;

    /** Dev shortcut: 1 and P pressed together = one instant stomach level (the server checks devLevelUpKey / operator). */
    @SubscribeEvent
    public static void onKey(InputEvent.Key e) {
        if (e.getAction() != GLFW.GLFW_PRESS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return; // not while typing in chat or in a menu
        int key = e.getKey();
        if (key != GLFW.GLFW_KEY_1 && key != GLFW.GLFW_KEY_P) return;
        int other = key == GLFW.GLFW_KEY_1 ? GLFW.GLFW_KEY_P : GLFW.GLFW_KEY_1;
        if (InputConstants.isKeyDown(mc.getWindow().getWindow(), other)) {
            Net.send(14);
            closeSocialTicks = 4; // vanilla opens the Social Interactions screen on P: shut it again
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (closeSocialTicks > 0) {
            closeSocialTicks--;
            if (mc.screen instanceof SocialInteractionsScreen) mc.setScreen(null);
        }
        CpmDriver.tick(); // Custom Player Models belly sliders (does nothing without CPM)

        while (ClientModEvents.SWALLOW_KEY.consumeClick()) {
            // Use what the player actually sees on their screen, so wandering mobs and lag don't cause misses.
            Entity target = TargetFinder.find(mc.player, TargetFinder.REACH);
            Net.send(0, target == null ? -1 : target.getId());
        }
        while (ClientModEvents.STRUGGLE_KEY.consumeClick()) Net.send(11);
        while (ClientModEvents.MODE_KEY.consumeClick()) Net.send(1);
        while (ClientModEvents.RELEASE_KEY.consumeClick()) Net.send(mc.player.isShiftKeyDown() ? 3 : 2);
        while (ClientModEvents.SCREEN_KEY.consumeClick()) {
            if (ClientState.heldMode() > 0) { // swallowed: look at the swallower's item storage instead
                if (mc.screen == null) Net.send(7);
                continue;
            }
            // Pred / Both get the stomach menu. Prey / Neither have no stomach, so the key opens Settings (to change role).
            if (mc.screen == null) mc.setScreen(ClientState.role().canEat() ? new StomachScreen() : new SettingsScreen());
        }
    }
}
