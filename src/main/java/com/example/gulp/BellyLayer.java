package com.example.gulp;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.player.PlayerModelPart;

import java.util.List;

/** Draws a stretched copy of the player's own torso (skin + clothing) bulging forward as the stomach fills. */
public class BellyLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    // ---- Tweak these to change how big the belly gets ----
    /** How many times deeper the torso gets when completely full (1 = no change). */
    private static final float MAX_EXTRA_DEPTH = 1.6f;
    /** How much wider the torso gets when completely full. */
    private static final float MAX_EXTRA_WIDTH = 0.18f;

    private final ModelPart bellyFull, jacketFull, bellyLow, jacketLow;
    private final ModelPart bellyFullClothing, jacketFullClothing, bellyLowClothing, jacketLowClothing;
    private final RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent;
    /** Minecraft Comes Alive's skin and clothing layers on this renderer (found on first use; empty without MCA). */
    private List<Object> mcaLayers;

    public BellyLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        ModelPart root = models.bakeLayer(ClientModEvents.BELLY_LAYER);
        this.bellyFull = root.getChild("belly_full");
        this.jacketFull = root.getChild("jacket_full");
        this.bellyLow = root.getChild("belly_low");
        this.jacketLow = root.getChild("jacket_low");
        this.bellyFullClothing = root.getChild("belly_full_clothing");
        this.jacketFullClothing = root.getChild("jacket_full_clothing");
        this.bellyLowClothing = root.getChild("belly_low_clothing");
        this.jacketLowClothing = root.getChild("jacket_low_clothing");
        this.parent = parent;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;

        float shown = ClientState.smoothedBelly(player.getUUID());
        if (shown < 0.02f) return;

        if (GulpConfig.bellyStyle == GulpConfig.BellyStyle.OFF) return;
        // Your own Custom Player Model already has a belly animation: let it be the belly instead of ours.
        if (player == Minecraft.getInstance().player && CpmDriver.localModelHandlesBelly()) return;

        boolean classic = GulpConfig.bellyStyle == GulpConfig.BellyStyle.CLASSIC;
        ModelPart belly = classic ? bellyFull : bellyLow;
        ModelPart jacket = classic ? jacketFull : jacketLow;

        // sqrt so even a small meal is visible
        float bulge = (float) Math.sqrt(shown);
        float depthScale = 1f + bulge * MAX_EXTRA_DEPTH;
        float widthScale = 1f + bulge * MAX_EXTRA_WIDTH;

        poseStack.pushPose();
        // Follow the torso (including sneaking tilt)
        getParentModel().body.translateAndRotate(poseStack);
        // Scale forward from the back of the torso so the stomach pushes OUT the front
        poseStack.translate(0f, 0f, 2f / 16f);
        poseStack.scale(widthScale, 1f, depthScale);
        poseStack.translate(0f, 0f, -2f / 16f);

        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0f);
        boolean showJacket = player.isModelPartShown(PlayerModelPart.JACKET);

        // Minecraft Comes Alive: when it gives this player its villager look, the visible skin and outfit are MCA's own
        // textures, so stretch THOSE (skin first, then clothing, like MCA does) so the outfit grows with the belly.
        if (mcaLayers == null) mcaLayers = McaSkins.findLayers(parent);
        List<McaSkins.Pass> mca = McaSkins.passes(player, mcaLayers, partialTick);
        if (mca != null && !mca.isEmpty()) {
            for (McaSkins.Pass pass : mca) {
                VertexConsumer mcaBuffer = buffer.getBuffer(RenderType.entityCutoutNoCull(pass.texture()));
                ModelPart body = pass.clothing() ? (classic ? bellyFullClothing : bellyLowClothing) : belly;
                ModelPart wear = pass.clothing() ? (classic ? jacketFullClothing : jacketLowClothing) : jacket;
                body.render(poseStack, mcaBuffer, packedLight, overlay, pass.r(), pass.g(), pass.b(), 1f);
                if (showJacket) wear.render(poseStack, mcaBuffer, packedLight, overlay, pass.r(), pass.g(), pass.b(), 1f);
            }
        } else {
            VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(player.getSkinTextureLocation()));
            belly.render(poseStack, vc, packedLight, overlay);
            if (showJacket) jacket.render(poseStack, vc, packedLight, overlay);
        }
        poseStack.popPose();
    }
}
