package com.terrawrath.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.terrawrath.client.ClientFX;
import com.terrawrath.client.DisasterClientState;
import com.terrawrath.client.render.visual.AuroraVisual;
import com.terrawrath.client.render.visual.BlizzardVisual;
import com.terrawrath.client.render.visual.HorizonVisual;
import com.terrawrath.client.render.visual.SinkholeVisual;
import com.terrawrath.client.render.visual.StormVisual;
import com.terrawrath.client.render.visual.TornadoVisual;
import com.terrawrath.client.render.visual.TsunamiVisual;
import com.terrawrath.client.render.visual.VolcanoVisual;
import com.terrawrath.entity.DisasterEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Draws the big procedural disaster geometry: funnels, plumes, waves, cloud decks, snow walls, black holes.
 */
public class DisasterRenderer extends EntityRenderer<DisasterEntity, DisasterRenderState> {
    public DisasterRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0;
    }

    @Override
    public DisasterRenderState createRenderState() {
        return new DisasterRenderState();
    }

    @Override
    protected boolean affectedByCulling(DisasterEntity entity) {
        return false;
    }

    @Override
    protected AABB getBoundingBoxForCulling(DisasterEntity entity, float partialTicks) {
        return entity.getBoundingBox().inflate(600);
    }

    @Override
    public void extractRenderState(DisasterEntity entity, DisasterRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        DisasterClientState cs = DisasterClientState.of(entity);
        state.type = entity.disasterType();
        state.phase = entity.phase();
        state.progress = cs.initialised ? cs.progress(partialTicks) : entity.progress();
        state.presence = DisasterClientState.presence(state.phase, state.progress);
        state.intensity = entity.intensity();
        state.radius = cs.initialised ? cs.radius(partialTicks) : entity.radius();
        state.a = cs.initialised ? cs.a(partialTicks) : entity.a();
        state.b = cs.initialised ? cs.b(partialTicks) : entity.b();
        state.c = cs.initialised ? cs.c(partialTicks) : entity.c();
        state.d = cs.initialised ? cs.d(partialTicks) : entity.d();
        state.time = entity.tickCount + partialTicks;
        state.flash = Math.min(1, ClientFX.flash(partialTicks) * 0.5F + ClientFX.lightning(partialTicks));
        state.ground = entity.level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(entity.getX()), Mth.floor(entity.getZ()));
        state.isInvisible = false;
        state.displayFireAnimation = false;
    }

    @Override
    public void submit(DisasterRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (ShaderCompat.shadowPass()) {
            return;
        }
        state.camX = camera.pos.x - state.x;
        state.camY = camera.pos.y - state.y;
        state.camZ = camera.pos.z - state.z;
        switch (state.type) {
            case TORNADO -> TornadoVisual.submit(state, poseStack, collector, camera);
            case VOLCANO -> VolcanoVisual.submit(state, poseStack, collector, camera);
            case TSUNAMI -> TsunamiVisual.submit(state, poseStack, collector, camera);
            case SUPERSTORM -> StormVisual.submit(state, poseStack, collector, camera);
            case BLIZZARD -> BlizzardVisual.submit(state, poseStack, collector, camera);
            case SINKHOLE -> SinkholeVisual.submit(state, poseStack, collector, camera);
            case EVENT_HORIZON -> HorizonVisual.submit(state, poseStack, collector, camera);
            case METEOR_SHOWER -> AuroraVisual.submit(state, poseStack, collector, camera);
            default -> {
            }
        }
    }
}
