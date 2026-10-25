package com.example.gulp;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/** Client-only setup (mod event bus): keys, belly model, HUD overlay. */
@Mod.EventBusSubscriber(modid = Gulp.ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() { }

    public static final ModelLayerLocation BELLY_LAYER =
            new ModelLayerLocation(new ResourceLocation(Gulp.ID, "belly"), "main");

    public static final KeyMapping SWALLOW_KEY = new KeyMapping("key.gulp.swallow", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.category.gulp");
    public static final KeyMapping MODE_KEY = new KeyMapping("key.gulp.mode", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.category.gulp");
    public static final KeyMapping RELEASE_KEY = new KeyMapping("key.gulp.release", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.category.gulp");
    /** Inventory screen only: swallow the hovered item. Same default key (V) as swallowing in the world, with no clash. */
    public static final KeyMapping INVENTORY_SWALLOW_KEY = new KeyMapping("key.gulp.inventory_swallow",
            KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.category.gulp");

    /** Swallowed players only: struggle to break free (hard mode) or rub the stomach walls (soft mode). */
    public static final KeyMapping STRUGGLE_KEY = new KeyMapping("key.gulp.struggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.category.gulp");

    public static final KeyMapping SCREEN_KEY = new KeyMapping("key.gulp.screen", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.category.gulp");

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent e) {
        GulpConfig.load();
        ClientState.soundListener = ClientSounds::playWorld;
        ClientState.messageListener = text -> {
            if (!GulpConfig.actionMessages) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.displayClientMessage(text, true);
        };
        ClientState.levelUpListener = (level, points) -> {
            if (!GulpConfig.levelUpMessages) return;
            Minecraft mc = Minecraft.getInstance();
            mc.gui.getChat().addMessage(Component.literal("Stomach reached level " + level + "! You have " + points
                    + " perk point" + (points == 1 ? "" : "s") + " to spend (open the stomach screen)."));
        };
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent e) {
        e.register(SWALLOW_KEY);
        e.register(MODE_KEY);
        e.register(RELEASE_KEY);
        e.register(STRUGGLE_KEY);
        e.register(SCREEN_KEY);
        e.register(INVENTORY_SWALLOW_KEY);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(BELLY_LAYER, BellyModel::createLayer);
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers e) {
        for (String skin : e.getSkins()) {
            EntityRenderer<? extends Player> renderer = e.getSkin(skin);
            if (renderer instanceof PlayerRenderer playerRenderer) {
                playerRenderer.addLayer(new BellyLayer(playerRenderer, e.getEntityModels()));
            }
        }
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("stomach", (gui, graphics, partialTick, width, height) -> GulpHud.render(graphics, width, height));
    }
}
