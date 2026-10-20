package com.terrawrath.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.terrawrath.TerraWrath;
import com.terrawrath.entity.ShockwaveEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * Expanding glowing ring hugging the ground, a rising dust wall behind it and a blinding core flash.
 */
public class ShockwaveRenderer extends EntityRenderer<ShockwaveEntity, ShockwaveRenderer.State> {
    private static final Identifier GLOW = TerraWrath.id("textures/particle/glow.png");
    private static final int FULL = LightCoordsUtil.FULL_BRIGHT;

    public static class State extends EntityRenderState {
        public float maxRadius;
        public float power;
        public int color;
        public float progress;
        public float time;
    }

    public ShockwaveRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    protected boolean affectedByCulling(ShockwaveEntity entity) {
        return false;
    }

    @Override
    public void extractRenderState(ShockwaveEntity entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.maxRadius = entity.maxRadius();
        state.power = entity.power();
        state.color = entity.color();
        state.time = entity.tickCount + partialTicks;
        state.progress = Mth.clamp(state.time / Math.max(1, entity.life()), 0, 1);
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam) {
        if (ShaderCompat.shadowPass()) {
            return;
        }
        final float p = s.progress;
        final float ease = 1 - (1 - p) * (1 - p) * (1 - p);
        final float r = Math.max(0.5F, s.maxRadius * ease);
        final float fade = (float) Math.pow(1 - p, 1.4);
        final float band = 2 + r * 0.09F;
        final float t = s.time;
        ps.pushPose();
        ps.translate(0, 0.3F, 0);
        c.submitCustomGeometry(ps, TWRenderTypes.glow(TWRenderTypes.tex("ring")), (pose, vc) -> {
            Mesh.annulus(vc, pose, Math.max(0, r - band), r + band, 1, 96, 6, t * 0.01F, 0, tt -> Mesh.argb(fade, s.color), FULL);
            Mesh.annulus(vc, pose, Math.max(0, r - band * 0.35F), r + band * 0.35F, 1, 96, 6, -t * 0.01F, 0, tt -> Mesh.argb(fade, 0xFFFFFF), FULL);
        });
        ps.popPose();
        // dust wall riding the ring
        final float wallH = (4 + s.maxRadius * 0.07F) * (1 - p);
        Mesh.TubeProfile wall = new Mesh.TubeProfile() {
            @Override
            public void at(float sn, float[] out) {
                out[0] = 0;
                out[1] = sn * wallH;
                out[2] = 0;
                out[3] = r * (1 - sn * 0.06F);
            }

            @Override
            public int color(float sn) {
                return Mesh.argb(0.55F * fade * (1 - sn), 0x9A8468);
            }
        };
        c.submitCustomGeometry(ps, TWRenderTypes.smoke(TWRenderTypes.tex("cloud")), (pose, vc) ->
            Mesh.tube(vc, pose, wall, 3, 72, 8, 1, t * 0.004F, -t * 0.02F, LightCoordsUtil.pack(12, 15)));
        // core flash
        if (p < 0.35F && s.power > 0) {
            ps.pushPose();
            ps.translate(0, 2, 0);
            ps.rotate(cam.orientation);
            final float f = (1 - p / 0.35F) * Math.min(1, s.power);
            c.submitCustomGeometry(ps, TWRenderTypes.glow(GLOW), (pose, vc) -> {
                Mesh.billboard(vc, pose, 6 + s.maxRadius * 0.35F, Mesh.argb(f, s.color), FULL);
                Mesh.billboard(vc, pose, 3 + s.maxRadius * 0.12F, Mesh.argb(f, 0xFFFFFF), FULL);
            });
            ps.popPose();
        }
    }
}
