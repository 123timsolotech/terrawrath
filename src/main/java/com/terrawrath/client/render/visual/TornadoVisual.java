package com.terrawrath.client.render.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.terrawrath.client.render.DisasterRenderState;
import com.terrawrath.client.render.Mesh;
import com.terrawrath.client.render.TWRenderTypes;
import com.terrawrath.entity.DisasterEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * A living, twisting funnel: four translucent rotating shells that bend and snake, darkening toward a
 * rotating wall cloud, with a boiling dust skirt at the ground.
 */
public final class TornadoVisual {
    private TornadoVisual() {}

    private record Shell(float radiusFactor, int color, float alpha, float uSpeed, float vSpeed) {}

    private static final Shell[] SHELLS = {
        new Shell(0.42F, 0x1E1B19, 0.98F, 0.075F, -0.016F),
        new Shell(0.66F, 0x332E2A, 0.9F, 0.055F, -0.012F),
        new Shell(0.88F, 0x4E4740, 0.7F, 0.038F, -0.009F),
        new Shell(1.08F, 0x6E6456, 0.42F, 0.026F, -0.006F),
    };

    public static void submit(DisasterRenderState s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam) {
        final float height = Math.max(40, s.a);
        final float r0 = Math.max(2, s.b);
        final float rt = Math.max(10, s.c);
        final float heading = s.d;
        final float t = s.time;
        float form;
        float width = 1;
        float alpha;
        if (s.phase == DisasterEntity.WARNING) {
            form = smooth(0.25F, 1.0F, s.progress);
            alpha = 0.25F + 0.75F * form;
        } else if (s.phase == DisasterEntity.ACTIVE) {
            form = 1;
            alpha = 1;
        } else {
            form = 1;
            width = 1 - 0.8F * s.progress;
            alpha = 1 - s.progress * s.progress;
        }
        float cloudAlpha = s.phase == DisasterEntity.WARNING ? smooth(0, 0.35F, s.progress) : s.phase == DisasterEntity.ENDING ? 1 - s.progress : 1;
        final float bottom = 1 - form;
        final float w = width;
        final float leanX = -Mth.cos(heading) * 16, leanZ = -Mth.sin(heading) * 16;
        int light = LightCoordsUtil.pack(12, 15);

        // ---- funnel shells
        for (int k = 0; k < SHELLS.length; k++) {
            Shell sh = SHELLS[k];
            final int kk = k;
            final float a = alpha * sh.alpha;
            Mesh.TubeProfile profile = new Mesh.TubeProfile() {
                @Override
                public void at(float sn, float[] out) {
                    float sv = bottom + sn * (1 - bottom);
                    float h = sv * height;
                    float snake = sv * (1 - sv * 0.35F);
                    out[0] = leanX * sv * sv + Mth.sin(t * 0.035F + sv * 4.2F) * 6.5F * snake + Mth.sin(t * 0.081F + sv * 9.0F) * 1.4F * sv;
                    out[1] = h;
                    out[2] = leanZ * sv * sv + Mth.cos(t * 0.029F + sv * 3.7F) * 6.5F * snake + Mth.cos(t * 0.067F + sv * 8.1F) * 1.4F * sv;
                    float r = r0 + (rt - r0) * (float) Math.pow(sv, 2.3);
                    r *= 1 + 0.07F * Mth.sin(t * 0.31F + sv * 15 + kk * 1.7F);
                    out[3] = r * sh.radiusFactor * w;
                }

                @Override
                public int color(float sn) {
                    float sv = bottom + sn * (1 - bottom);
                    float shade = Mth.lerp((float) Math.pow(sv, 0.8), 1.0F, 0.42F);
                    float edge = smooth(0, 0.04F, sn) * (1 - smooth(0.9F, 1.0F, sv) * 0.5F);
                    int rgb = Mesh.scaleRgb(sh.color, shade * (1 + 0.6F * s.flash));
                    return Mesh.argb(a * edge, rgb);
                }
            };
            c.submitCustomGeometry(ps, TWRenderTypes.smoke(TWRenderTypes.tex("funnel")), (pose, vc) ->
                Mesh.tube(vc, pose, profile, 40, 30, 3, height / 42F, t * sh.uSpeed, t * sh.vSpeed, light));
        }

        // ---- faint additive rim so the silhouette reads against a night sky (invisible in daylight)
        final float rimA = alpha * (0.34F + 0.5F * s.flash);
        Mesh.TubeProfile rim = new Mesh.TubeProfile() {
            @Override
            public void at(float sn, float[] out) {
                float sv = bottom + sn * (1 - bottom);
                float snake = sv * (1 - sv * 0.35F);
                out[0] = leanX * sv * sv + Mth.sin(t * 0.035F + sv * 4.2F) * 6.5F * snake + Mth.sin(t * 0.081F + sv * 9.0F) * 1.4F * sv;
                out[1] = sv * height;
                out[2] = leanZ * sv * sv + Mth.cos(t * 0.029F + sv * 3.7F) * 6.5F * snake + Mth.cos(t * 0.067F + sv * 8.1F) * 1.4F * sv;
                out[3] = (r0 + (rt - r0) * (float) Math.pow(sv, 2.3)) * 1.12F * w;
            }

            @Override
            public int color(float sn) {
                return Mesh.argb(rimA * smooth(0, 0.05F, sn) * (1 - smooth(0.85F, 1, sn)), 0x6A7690);
            }
        };
        c.submitCustomGeometry(ps, TWRenderTypes.glow(TWRenderTypes.tex("funnel")), (pose, vc) ->
            Mesh.tube(vc, pose, rim, 30, 30, 3, height / 42F, t * 0.02F, -t * 0.005F, LightCoordsUtil.FULL_BRIGHT));

        // ---- boiling dust skirt at the base
        if (form > 0.95F) {
            for (int k = 0; k < 3; k++) {
                final float spread = 1.6F + k * 1.2F;
                final float topY = 10 + k * 6;
                final float a = alpha * (0.55F - k * 0.13F);
                final int kk = k;
                Mesh.TubeProfile skirt = new Mesh.TubeProfile() {
                    @Override
                    public void at(float sn, float[] out) {
                        out[0] = Mth.sin(t * 0.05F + kk) * 1.5F;
                        out[1] = -1 + sn * topY;
                        out[2] = Mth.cos(t * 0.05F + kk) * 1.5F;
                        out[3] = (r0 * (1.1F + spread * (float) Math.pow(sn, 0.6))) * w * (1 + 0.08F * Mth.sin(t * 0.2F + sn * 6 + kk));
                    }

                    @Override
                    public int color(float sn) {
                        return Mesh.argb(a * (1 - sn) * (1 - sn), 0x8A7255);
                    }
                };
                c.submitCustomGeometry(ps, TWRenderTypes.smoke(TWRenderTypes.tex("cloud")), (pose, vc) ->
                    Mesh.tube(vc, pose, skirt, 8, 36, 2, 1, t * (0.03F + kk * 0.012F), -t * 0.01F, light));
            }
        }

        // ---- rotating wall cloud / parent supercell
        ps.pushPose();
        ps.translate(leanX, height, leanZ);
        final float ca = cloudAlpha;
        c.submitCustomGeometry(ps, TWRenderTypes.smoke(TWRenderTypes.tex("swirl")), (pose, vc) ->
            Mesh.annulus(vc, pose, rt * 0.2F, rt * 4.2F, 10, 64, 3, t * 0.0035F, 0,
                tt -> Mesh.argb(ca * 0.95F * (1 - tt * tt), Mesh.scaleRgb(0x34333A, 1 + 0.8F * s.flash)), light));
        ps.translate(0, 8, 0);
        c.submitCustomGeometry(ps, TWRenderTypes.smoke(TWRenderTypes.tex("swirl")), (pose, vc) ->
            Mesh.annulus(vc, pose, 0, rt * 6.0F, 10, 64, 4, t * 0.0019F, 0.3F,
                tt -> Mesh.argb(ca * 0.95F * (1 - tt * tt * tt), Mesh.scaleRgb(0x26252B, 1 + 0.8F * s.flash)), light));
        ps.translate(0, -18, 0);
        // lowered rotating wall cloud the funnel hangs from
        Mesh.TubeProfile collar = new Mesh.TubeProfile() {
            @Override
            public void at(float sn, float[] out) {
                out[0] = 0;
                out[1] = sn * 12;
                out[2] = 0;
                out[3] = rt * (0.7F + 0.9F * sn) * (1 + 0.05F * Mth.sin(t * 0.05F + sn * 3));
            }

            @Override
            public int color(float sn) {
                return Mesh.argb(ca * 0.9F, Mesh.scaleRgb(0x2E2C30, 1 + 0.6F * s.flash));
            }
        };
        c.submitCustomGeometry(ps, TWRenderTypes.smoke(TWRenderTypes.tex("swirl")), (pose, vc) ->
            Mesh.tube(vc, pose, collar, 4, 48, 3, 1, t * 0.006F, 0, light));
        c.submitCustomGeometry(ps, TWRenderTypes.smoke(TWRenderTypes.tex("cloud")), (pose, vc) ->
            Mesh.annulus(vc, pose, rt * 0.3F, rt * 0.75F, 3, 40, 2, t * 0.008F, 0,
                tt -> Mesh.argb(ca * 0.85F, 0x3A3734), light));
        ps.popPose();
    }

    static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
