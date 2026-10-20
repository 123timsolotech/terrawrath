package com.terrawrath.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.terrawrath.entity.DebrisEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.BlockDisplayEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;

/**
 * Real tumbling blocks flying through the air.
 */
public class DebrisRenderer extends EntityRenderer<DebrisEntity, DebrisRenderer.State> {
    private static final BlockDisplayContext CONTEXT = BlockDisplayContext.create();
    private final BlockModelResolver resolver;

    public static class State extends BlockDisplayEntityRenderState {
        public float spinX, spinY, spinZ;
        public float scale = 1;
        public boolean burning;
    }

    public DebrisRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.resolver = context.getBlockModelResolver();
        this.shadowRadius = 0.3F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DebrisEntity entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        resolver.update(state.blockModel, entity.block(), CONTEXT);
        float age = entity.tickCount + partialTicks;
        float seed = entity.spinSeed();
        state.spinX = age * (9 + seed * 14);
        state.spinY = age * (6 + seed * 11) + seed * 360;
        state.spinZ = age * (4 + seed * 7);
        state.scale = entity.scale();
        state.burning = entity.burning();
        state.displayFireAnimation = entity.burning();
        if (entity.burning()) {
            state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        }
    }

    @Override
    public void submit(State state, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
        ps.pushPose();
        ps.translate(0, 0.45F, 0);
        ps.rotate(Axis.YP.rotationDegrees(state.spinY));
        ps.rotate(Axis.XP.rotationDegrees(state.spinX));
        ps.rotate(Axis.ZP.rotationDegrees(state.spinZ));
        ps.scale(state.scale * 0.92F, state.scale * 0.92F, state.scale * 0.92F);
        ps.translate(-0.5F, -0.5F, -0.5F);
        state.blockModel.submit(ps, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        ps.popPose();
        super.submit(state, ps, collector, camera);
    }
}
