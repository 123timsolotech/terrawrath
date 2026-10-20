package com.terrawrath.client.render.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.terrawrath.TerraWrath;
import com.terrawrath.client.render.DisasterRenderState;
import com.terrawrath.client.render.Mesh;
import com.terrawrath.client.render.TWRenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * Lava fountain, crater glow and a towering ash column with an anvil top, lit orange from below.
 */
public final class VolcanoVisual {
    private static final Identifier GLOW_TEX = TerraWrath.id("textures/particle/glow.png");

    private VolcanoVisual() {}

    public static void submit(DisasterRenderState s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam) {
        final float e = Mth.clamp(s.b, 0, 1);
        final float crater = Math.max(4, s.d);
        final float t = s.time;
        int light = LightCoordsUtil.pack(10, 15);
        int full = LightCoordsUtil.FULL_BRIGHT;
        if (e <= 0.01F) {
            // growing mountain: a faint ominous glow at the summit
            if (s.phase != 0 && s.a > 20) {
                ps.pushPose();
                ps.translate(0, 2, 0);
                ps.rotate(cam.orientation);
                final float pulse = 0.25F + 0.1F * Mth.sin(t * 0.2F);
                c.submitCustomGeometry(ps, TWRenderTypes.glow(GLOW_TEX), (pose, vc) -> Mesh.billboard(vc, pose, crater * 2.5F, Mesh.argb(pulse, 0xFF5A1F), full));
                ps.popPose();
            }
            return;
        }

        // ---- lava fountain (three nested additive jets)
        final float fountain = (34 + 18 * s.intensity) * e * (0.8F + 0.22F * Mth.sin(t * 0.21F) + 0.14F * Mth.sin(t * 0.53F + 1.3F));
        float[][] jets = {{0.7F, 1.0F}, {1.0F, 0.9F}, {1.5F, 0.6F}, {2.2F, 0.3F}};
        int[] jetColors = {0xFFF4C8, 0xFFC060, 0xFF7A20, 0xFF4A10};
        for (int j = 0; j < jets.length; j++) {
            final float rf = jets[j][0], ja = jets[j][1] * e;
            final int col = jetColors[j];
            final int jj = j;
            Mesh.TubeProfile jet = new Mesh.TubeProfile() {
                @Override
                public void at(float sn, float[] out) {
                    out[0] = Mth.sin(t * 0.13F + sn * 3 + jj) * sn * 1.5F;
                    out[1] = -2 + sn * fountain;
                    out[2] = Mth.cos(t * 0.11F + sn * 3 + jj) * sn * 1.5F;
                    float r = crater * 0.42F * (1 - sn * 0.5F) + sn * sn * crater * 0.9F;
                    out[3] = r * rf;
                }

                @Override
                public int color(float sn) {
                    return Mesh.argb(ja * (1 - smooth(0.7F, 1.0F, sn)), col);
                }
            };
            c.submitCustomGeometry(ps, TWRenderTypes.glow(TWRenderTypes.tex("lava")), (pose, vc) ->
                Mesh.tube(vc, pose, jet, 14, 20, 2, 3, t * 0.01F, -t * 0.09F * (1 + jj * 0.3F), full));
        }

        // ---- crater flare
        ps.pushPose();
        ps.translate(0, 3, 0);
        ps.rotate(cam.orientation);
        final float flare = e * (0.55F + 0.15F * Mth.sin(t * 0.33F));
        c.submitCustomGeometry(ps, TWRenderTypes.glow(GLOW_TEX), (pose, vc) -> {
            Mesh.billboard(vc, pose, crater * 4.0F, Mesh.argb(flare, 0xFF6A1F), full);
            Mesh.billboard(vc, pose, crater * 1.6F, Mesh.argb(flare, 0xFFE0A0), full);
        });
        ps.popPose();

        // ---- ash column
        final float plumeH = (150 + 50 * s.intensity) * smooth(0, 1, e * 1.2F);
        final float drift = 30;
        for (int k = 0; k < 2; k++) {
            final float rf = k == 0 ? 1.0F : 0.78F;
            final int base = k == 0 ? 0x171413 : 0x231E1C;
            final float al = (k == 0 ? 0.92F : 0.96F) * e;
            final int kk = k;
            Mesh.TubeProfile plume = new Mesh.TubeProfile() {
                @Override
                public void at(float sn, float[] out) {
                    out[0] = sn * sn * drift + Mesh.wave(sn * 5 + t * 0.01F, kk) * 4 * sn;
                    out[1] = 4 + sn * plumeH;
                    out[2] = sn * sn * drift * 0.4F + Mesh.wave(sn * 4 + t * 0.012F, kk + 3) * 4 * sn;
                    float r = crater * 1.8F + sn * 62 + (sn > 0.7F ? (float) Math.pow((sn - 0.7F) / 0.3F, 1.6) * 85 : 0);
                    r *= 1 + 0.12F * Mesh.wave(sn * 7 - t * 0.02F, kk * 2);
                    out[3] = r * rf;
                }

                @Override
                public int color(float sn) {
                    float lit = 1 - smooth(0, 0.3F, sn);
                    int rgb = Mesh.lerpRgb(base, 0x9A4520, lit * 0.6F);
                    rgb = Mesh.scaleRgb(rgb, 1 + s.flash * 1.5F);
                    return Mesh.argb(al * smooth(0, 0.05F, sn) * (1 - smooth(0.92F, 1.0F, sn)), rgb);
                }
            };
            c.submitCustomGeometry(ps, TWRenderTypes.smoke(TWRenderTypes.tex("cloud")), (pose, vc) ->
                Mesh.tube(vc, pose, plume, 40, 36, 3, 6, t * 0.004F * (kk + 1), -t * 0.004F, light));
        }

        // ---- lava underglow on the base of the plume (additive)
        Mesh.TubeProfile underglow = new Mesh.TubeProfile() {
            @Override
            public void at(float sn, float[] out) {
                out[0] = 0;
                out[1] = 2 + sn * plumeH * 0.45F;
                out[2] = 0;
                out[3] = (crater * 1.8F + sn * 62 * 0.45F) * 1.04F;
            }

            @Override
            public int color(float sn) {
                return Mesh.argb(e * 0.5F * (1 - sn) * (0.85F + 0.15F * Mth.sin(t * 0.4F)), 0xFF5A1F);
            }
        };
        c.submitCustomGeometry(ps, TWRenderTypes.glow(TWRenderTypes.tex("cloud")), (pose, vc) ->
            Mesh.tube(vc, pose, underglow, 8, 32, 3, 2, t * 0.006F, -t * 0.01F, full));

        // ---- anvil cloud spreading at the top of the column
        ps.pushPose();
        ps.translate(drift, plumeH + 2, drift * 0.4F);
        final float anvilA = e;
        c.submitCustomGeometry(ps, TWRenderTypes.smoke(TWRenderTypes.tex("swirl")), (pose, vc) ->
            Mesh.annulus(vc, pose, 0, 115 + 40 * s.intensity, 8, 44, 2, t * 0.0012F, 0,
                tt -> Mesh.argb(anvilA * 0.88F * (1 - tt * tt), Mesh.scaleRgb(0x2B2725, 1 + s.flash)), light));
        ps.popPose();
    }

    static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
