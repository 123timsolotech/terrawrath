package com.terrawrath.client.render.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.terrawrath.client.render.DisasterRenderState;
import com.terrawrath.client.render.Mesh;
import com.terrawrath.client.render.TWRenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * Dust boiling up out of the abyss, and a darkness at the bottom that makes the pit feel bottomless.
 */
public final class SinkholeVisual {
    private SinkholeVisual() {}

    public static void submit(DisasterRenderState s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam) {
        final float open = s.a;
        if (open < 1.5F) {
            return;
        }
        final float depth = Math.max(10, s.c);
        final float t = s.time;
        final float p = s.phase == 2 ? 1 - s.progress : 1;
        int light = LightCoordsUtil.pack(6, 15);
        for (int k = 0; k < 2; k++) {
            final int kk = k;
            final float a = p * (k == 0 ? 0.42F : 0.25F);
            Mesh.TubeProfile dust = new Mesh.TubeProfile() {
                @Override
                public void at(float sn, float[] out) {
                    out[0] = Mesh.wave(sn * 3 + t * 0.02F, kk) * 2;
                    out[1] = -depth + sn * (depth + 32 + kk * 10);
                    out[2] = Mesh.wave(sn * 3 + t * 0.017F, kk + 5) * 2;
                    out[3] = open * (0.75F + kk * 0.25F) + sn * sn * (10 + kk * 8);
                }

                @Override
                public int color(float sn) {
                    return Mesh.argb(a * Mth.sin(Mth.clamp(sn, 0, 1) * Mth.PI) * (1 - sn * 0.3F), 0x8F7D63);
                }
            };
            c.submitCustomGeometry(ps, TWRenderTypes.smoke(TWRenderTypes.tex("cloud")), (pose, vc) ->
                Mesh.tube(vc, pose, dust, 12, 36, 3, 3, t * (0.006F + kk * 0.004F), -t * 0.012F, light));
        }
        // darkness thickening with depth so the pit reads as bottomless
        Mesh.TubeProfile abyss = new Mesh.TubeProfile() {
            @Override
            public void at(float sn, float[] out) {
                out[0] = 0;
                out[1] = -depth + 1 + sn * (depth - 3);
                out[2] = 0;
                out[3] = open * (0.92F + sn * 0.1F);
            }

            @Override
            public int color(float sn) {
                return Mesh.argb(p * 0.85F * (1 - sn) * (1 - sn), 0x060504);
            }
        };
        c.submitCustomGeometry(ps, TWRenderTypes.sheer(TWRenderTypes.tex("white")), (pose, vc) ->
            Mesh.tube(vc, pose, abyss, 10, 40, 1, 1, 0, 0, light));
        ps.pushPose();
        ps.translate(0, -depth + 1.5F, 0);
        c.submitCustomGeometry(ps, TWRenderTypes.sheer(TWRenderTypes.tex("cloud")), (pose, vc) ->
            Mesh.annulus(vc, pose, 0, open * 1.05F, 4, 36, 2, t * 0.003F, 0, tt -> Mesh.argb(0.95F * p * (1 - tt * tt * tt), 0x030202), light));
        ps.popPose();
    }
}
