package com.terrawrath.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.terrawrath.TerraWrath;
import com.terrawrath.entity.MeteorEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * A burning rock: dark core with glowing cracks, a layered fire envelope, a long flaming trail and,
 * for the colossal meteor, a shadow that grows on the ground beneath it.
 */
public class MeteorRenderer extends EntityRenderer<MeteorEntity, MeteorRenderer.State> {
    private static final Identifier GLOW = TerraWrath.id("textures/particle/glow.png");
    private static final int FULL = LightCoordsUtil.FULL_BRIGHT;

    public static class State extends EntityRenderState {
        public int kind;
        public float size;
        public final Vector3f dir = new Vector3f(0, -1, 0);
        public final Vector3f toTarget = new Vector3f();
        public float closeness;
        public float time;
    }

    public MeteorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    protected boolean affectedByCulling(MeteorEntity entity) {
        return false;
    }

    @Override
    public void extractRenderState(MeteorEntity entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.kind = entity.kind();
        state.size = entity.size();
        state.time = entity.tickCount + partialTicks;
        Vector3fc v = entity.launchVelocity();
        if (state.kind == MeteorEntity.KIND_LAVA_BOMB) {
            double vx = entity.getX() - entity.xOld, vy = entity.getY() - entity.yOld, vz = entity.getZ() - entity.zOld;
            state.dir.set((float) vx, (float) vy, (float) vz);
            if (state.dir.lengthSquared() < 1.0E-4F) {
                state.dir.set(v);
            }
        } else {
            state.dir.set(v);
        }
        if (state.dir.lengthSquared() > 1.0E-6F) {
            state.dir.normalize();
        }
        Vector3fc tgt = entity.target();
        state.toTarget.set((float) (tgt.x() - state.x), (float) (tgt.y() - state.y), (float) (tgt.z() - state.z));
        float dist = state.toTarget.length();
        state.closeness = Mth.clamp(1 - dist / 170F, 0, 1);
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam) {
        if (ShaderCompat.shadowPass()) {
            return;
        }
        final float size = s.size;
        final float t = s.time;
        final boolean colossal = s.kind == MeteorEntity.KIND_COLOSSAL;
        final boolean bomb = s.kind == MeteorEntity.KIND_LAVA_BOMB;
        final float core = size * 0.5F;

        // ---- trail: oriented along -direction
        ps.pushPose();
        Quaternionf face = new Quaternionf().rotationTo(0, 1, 0, -s.dir.x, -s.dir.y, -s.dir.z);
        ps.rotate(face);
        final float trailLen = bomb ? size * 7 : colossal ? size * 14 : size * 26;
        float[][] layers = bomb ? new float[][]{{1.1F, 0.8F}, {2.0F, 0.35F}} : new float[][]{{0.9F, 0.95F}, {1.6F, 0.55F}, {2.6F, 0.22F}};
        int[] cols = {0xFFF1C2, 0xFF9A3A, 0xFF4A12};
        for (int k = 0; k < layers.length; k++) {
            final float rf = layers[k][0], la = layers[k][1];
            final int col = cols[Math.min(k, cols.length - 1)];
            final int kk = k;
            Mesh.TubeProfile trail = new Mesh.TubeProfile() {
                @Override
                public void at(float sn, float[] out) {
                    out[0] = Mth.sin(sn * 9 - t * 0.6F + kk) * sn * core * 0.4F;
                    out[1] = sn * trailLen * (1 + kk * 0.15F);
                    out[2] = Mth.cos(sn * 7 - t * 0.5F + kk) * sn * core * 0.4F;
                    out[3] = core * rf * (1 + sn * 1.6F) * (1 - (float) Math.pow(sn, 3));
                }

                @Override
                public int color(float sn) {
                    int rgb = Mesh.lerpRgb(col, 0x7A1A05, sn);
                    return Mesh.argb(la * (1 - sn) * (1 - sn * 0.5F), rgb);
                }
            };
            c.submitCustomGeometry(ps, TWRenderTypes.glow(TWRenderTypes.tex("lava")), (pose, vc) ->
                Mesh.tube(vc, pose, trail, 18, 16, 1, 4, t * 0.02F, -t * 0.12F, FULL));
        }
        // smoky outer trail
        if (!bomb) {
            Mesh.TubeProfile smoke = new Mesh.TubeProfile() {
                @Override
                public void at(float sn, float[] out) {
                    out[0] = 0;
                    out[1] = core + sn * trailLen * 1.6F;
                    out[2] = 0;
                    out[3] = core * (1.4F + sn * 3.5F);
                }

                @Override
                public int color(float sn) {
                    return Mesh.argb(0.55F * (1 - sn) * Mth.clamp(sn * 6, 0, 1), 0x3A3330);
                }
            };
            c.submitCustomGeometry(ps, TWRenderTypes.smoke(TWRenderTypes.tex("cloud")), (pose, vc) ->
                Mesh.tube(vc, pose, smoke, 12, 16, 2, 3, t * 0.01F, -t * 0.03F, LightCoordsUtil.pack(12, 15)));
        }
        ps.popPose();

        // ---- the rock itself
        ps.pushPose();
        ps.rotate(Axis.YP.rotation(t * 0.07F));
        ps.rotate(Axis.XP.rotation(t * 0.05F));
        c.submitCustomGeometry(ps, RenderTypes.entityCutout(TWRenderTypes.tex("rock")), (pose, vc) ->
            Mesh.sphere(vc, pose, core, 10, 14, 0xFFFFFFFF, 2, 1, 0, 0, FULL));
        c.submitCustomGeometry(ps, TWRenderTypes.glow(TWRenderTypes.tex("rock_glow")), (pose, vc) ->
            Mesh.sphere(vc, pose, core * 1.04F, 10, 14, Mesh.argb(1, 0xFFC27A), 2, 1, 0, 0, FULL));
        c.submitCustomGeometry(ps, TWRenderTypes.glow(TWRenderTypes.tex("lava")), (pose, vc) -> {
            Mesh.sphere(vc, pose, core * 1.3F, 10, 14, Mesh.argb(0.45F, 0xFF8A2A), 2, 1, t * 0.02F, t * 0.03F, FULL);
            Mesh.sphere(vc, pose, core * 1.75F, 10, 14, Mesh.argb(0.2F, 0xFF5A1F), 2, 1, -t * 0.015F, t * 0.02F, FULL);
        });
        ps.popPose();

        // ---- bloom
        ps.pushPose();
        ps.rotate(cam.orientation);
        final float bloom = colossal ? 1.0F : 0.75F;
        c.submitCustomGeometry(ps, TWRenderTypes.glow(GLOW), (pose, vc) -> {
            Mesh.billboard(vc, pose, core * 5.5F, Mesh.argb(0.55F * bloom, 0xFF7A2E), FULL);
            Mesh.billboard(vc, pose, core * 2.4F, Mesh.argb(0.9F * bloom, 0xFFE8B0), FULL);
        });
        ps.popPose();

        // ---- the colossal meteor's shadow and impact marker
        if (colossal) {
            ps.pushPose();
            ps.translate(s.toTarget.x, s.toTarget.y + 0.15F, s.toTarget.z);
            final float close = s.closeness;
            final float shadowR = size * (1.2F + 3.5F * close);
            c.submitCustomGeometry(ps, TWRenderTypes.sheer(GLOW), (pose, vc) -> {
                Mesh.v(vc, pose, -shadowR, 0, -shadowR, Mesh.argb(0.35F + 0.6F * close, 0x000000), 0, 0, FULL);
                Mesh.v(vc, pose, -shadowR, 0, shadowR, Mesh.argb(0.35F + 0.6F * close, 0x000000), 0, 1, FULL);
                Mesh.v(vc, pose, shadowR, 0, shadowR, Mesh.argb(0.35F + 0.6F * close, 0x000000), 1, 1, FULL);
                Mesh.v(vc, pose, shadowR, 0, -shadowR, Mesh.argb(0.35F + 0.6F * close, 0x000000), 1, 0, FULL);
            });
            final float pulse = 0.5F + 0.5F * Mth.sin(t * (0.25F + close * 0.6F));
            final float ringR = size * (2.0F + 1.5F * close);
            c.submitCustomGeometry(ps, TWRenderTypes.glow(TWRenderTypes.tex("ring")), (pose, vc) ->
                Mesh.annulus(vc, pose, ringR * 0.9F, ringR * 1.1F, 1, 64, 4, t * 0.01F, 0, tt -> Mesh.argb(0.8F * pulse, 0xFF3A1A), FULL));
            ps.popPose();
        }
    }
}
