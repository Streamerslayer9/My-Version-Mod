package com.example.gulp;

import com.mojang.logging.LogUtils;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Minecraft Comes Alive (MCA Reborn), client side: lets the belly use the same skin and clothes MCA draws.
 *
 * When MCA gives a player its villager look, it draws extra layers over the normal player model: a skin layer (its own
 * texture, tinted by the villager's skin tone) and a clothing layer (the outfit). They come from MCA's own textures, not
 * from the player's Minecraft skin, so a belly drawn from the normal skin pokes through the outfit. This finds those two
 * layers and tells the belly which textures (and tints) to draw its stretched torso with, in MCA's order, so the outfit
 * grows with the belly.
 *
 * Like the rest of the MCA support it reaches MCA by name instead of depending on it, and if anything about MCA isn't what
 * is expected it just steps aside (the normal belly is drawn) and logs one line.
 */
public final class McaSkins {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** One texture to draw the belly with. `clothing` passes are drawn a little further out than the skin pass. */
    public record Pass(ResourceLocation texture, float r, float g, float b, boolean clothing) { }

    private static boolean resolved;
    private static boolean ok;
    private static Method useVillagerRenderer, getVillager, getSkin, getColor, canUse;
    private static Class<?> skinLayerClass, clothingLayerClass;
    private static final Map<Class<?>, Method> HAS_CUSTOM_SKIN = new HashMap<>();
    private static Field layersField;

    private McaSkins() { }

    private static boolean ready() {
        if (resolved) return ok;
        resolved = true;
        try {
            if (!ModList.get().isLoaded("mca")) return false;
            // MCA's Forge build renames its classes (forge.net.mca...); mcaClass tries both spellings.
            Class<?> client = McaIntegration.mcaClass("net.mca.MCAClient");
            useVillagerRenderer = client.getMethod("useVillagerRenderer", UUID.class);
            getVillager = McaIntegration.mcaClass("net.mca.client.model.CommonVillagerModel").getMethod("getVillager", Entity.class);
            skinLayerClass = McaIntegration.mcaClass("net.mca.client.render.layer.SkinLayer");
            clothingLayerClass = McaIntegration.mcaClass("net.mca.client.render.layer.ClothingLayer");
            Class<?> base = McaIntegration.mcaClass("net.mca.client.render.layer.VillagerLayer");
            getSkin = byName(base, "getSkin", 1);
            getColor = byName(base, "getColor", 2);
            canUse = byName(base, "canUse", 1);
            layersField = ObfuscationReflectionHelper.findField(LivingEntityRenderer.class, "layers");
            ok = true;
        } catch (Throwable t) {
            ok = false;
            LOGGER.warn("Vore Mod: MCA is installed but its skin layers aren't what I expected, so the belly will use the normal skin ({}).", t.toString());
        }
        return ok;
    }

    private static Method byName(Class<?> c, String name, int parameters) throws NoSuchMethodException {
        for (Method m : c.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == parameters) return m;
        }
        throw new NoSuchMethodException(c.getName() + "." + name);
    }

    /** MCA's skin and clothing layers on a player renderer (skin first, then clothing), or an empty list. */
    public static List<Object> findLayers(Object renderer) {
        List<Object> found = new ArrayList<>();
        if (!ready() || !(renderer instanceof LivingEntityRenderer<?, ?>)) return found;
        try {
            Object layers = layersField.get(renderer);
            if (layers instanceof List<?> list) {
                for (Object layer : list) {
                    if (skinLayerClass.isInstance(layer)) found.add(0, layer);   // skin always before clothing
                    else if (clothingLayerClass.isInstance(layer)) found.add(layer);
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("Vore Mod: couldn't read the player renderer's layers: {}", t.toString());
        }
        return found;
    }

    /**
     * The textures MCA is drawing on this player's torso right now, or null if MCA isn't giving this player its villager
     * look (then the belly should just use the normal Minecraft skin).
     */
    public static List<Pass> passes(AbstractClientPlayer player, List<Object> mcaLayers, float partialTick) {
        if (mcaLayers == null || mcaLayers.isEmpty() || !ready()) return null;
        try {
            // The same two checks MCA's own layers make before drawing anything.
            if (!(Boolean) useVillagerRenderer.invoke(null, player.getUUID())) return null;
            Object villager = getVillager.invoke(null, player);
            Method hasCustomSkin = HAS_CUSTOM_SKIN.computeIfAbsent(villager.getClass(), c -> {
                try {
                    return byName(c, "hasCustomSkin", 0);
                } catch (NoSuchMethodException e) {
                    return null;
                }
            });
            if (hasCustomSkin != null && (Boolean) hasCustomSkin.invoke(villager)) return null;

            List<Pass> passes = new ArrayList<>();
            for (Object layer : mcaLayers) {
                Object texture = getSkin.invoke(layer, player);
                if (!(texture instanceof ResourceLocation location) || !(Boolean) canUse.invoke(layer, location)) continue;
                float[] color = (float[]) getColor.invoke(layer, player, partialTick);
                passes.add(new Pass(location, color[0], color[1], color[2], clothingLayerClass.isInstance(layer)));
            }
            return passes;
        } catch (Throwable t) {
            return null;
        }
    }
}
