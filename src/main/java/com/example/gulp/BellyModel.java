package com.example.gulp;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Cuboids that copy the vanilla player torso texture, so the belly uses whatever skin the player has.
 * Two styles are baked, and the player picks one in the Settings screen:
 *   "full"  = the whole 12-pixel torso (the original look)
 *   "low"   = only the lower stomach strip
 * Each style has a torso layer ("belly") and an outer clothing layer ("jacket").
 *
 * The torso is 12 pixels tall (0 = shoulders, 12 = waist).
 */
public class BellyModel {
    /** Stomach-only style: how many pixels down from the shoulders it starts (0-12). */
    public static final int BELLY_TOP = 5;
    /** Stomach-only style: how tall it is, in pixels. Smaller = a more compact stomach. */
    public static final int BELLY_HEIGHT = 6;

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Classic: whole torso
        root.addOrReplaceChild("belly_full",
                CubeListBuilder.create().texOffs(16, 16)
                        .addBox(-4f, 0f, -2f, 8f, 12f, 4f, new CubeDeformation(0.02f)),
                PartPose.ZERO);
        root.addOrReplaceChild("jacket_full",
                CubeListBuilder.create().texOffs(16, 32)
                        .addBox(-4f, 0f, -2f, 8f, 12f, 4f, new CubeDeformation(0.27f)),
                PartPose.ZERO);

        // Stomach only: texture offset is shifted down by BELLY_TOP so we sample the same rows
        // of the skin that sit at that height on the real torso.
        root.addOrReplaceChild("belly_low",
                CubeListBuilder.create().texOffs(16, 16 + BELLY_TOP)
                        .addBox(-4f, (float) BELLY_TOP, -2f, 8f, (float) BELLY_HEIGHT, 4f, new CubeDeformation(0.02f)),
                PartPose.ZERO);
        root.addOrReplaceChild("jacket_low",
                CubeListBuilder.create().texOffs(16, 32 + BELLY_TOP)
                        .addBox(-4f, (float) BELLY_TOP, -2f, 8f, (float) BELLY_HEIGHT, 4f, new CubeDeformation(0.27f)),
                PartPose.ZERO);

        // Meshes for Minecraft Comes Alive's clothing layer. MCA draws its outfit a little further out than its skin
        // (0.0625 vs 0), so these are a little bigger than the ones above, or the outfit would clip through the belly.
        root.addOrReplaceChild("belly_full_clothing",
                CubeListBuilder.create().texOffs(16, 16)
                        .addBox(-4f, 0f, -2f, 8f, 12f, 4f, new CubeDeformation(0.09f)),
                PartPose.ZERO);
        root.addOrReplaceChild("jacket_full_clothing",
                CubeListBuilder.create().texOffs(16, 32)
                        .addBox(-4f, 0f, -2f, 8f, 12f, 4f, new CubeDeformation(0.33f)),
                PartPose.ZERO);
        root.addOrReplaceChild("belly_low_clothing",
                CubeListBuilder.create().texOffs(16, 16 + BELLY_TOP)
                        .addBox(-4f, (float) BELLY_TOP, -2f, 8f, (float) BELLY_HEIGHT, 4f, new CubeDeformation(0.09f)),
                PartPose.ZERO);
        root.addOrReplaceChild("jacket_low_clothing",
                CubeListBuilder.create().texOffs(16, 32 + BELLY_TOP)
                        .addBox(-4f, (float) BELLY_TOP, -2f, 8f, (float) BELLY_HEIGHT, 4f, new CubeDeformation(0.33f)),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 64);
    }
}
