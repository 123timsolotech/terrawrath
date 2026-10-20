package com.terrawrath.client.render.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.terrawrath.client.render.DisasterRenderState;
import com.terrawrath.client.render.Mesh;
import com.terrawrath.client.render.TWRenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * A skyscraper of water: a curling breaking-wave profile extruded across hundreds of blocks, with a foaming
 * crest, an overhanging lip and boiling white water at its foot.
 */
public final class TsunamiVisual {
    // cross-section (forward x, normalised height y) from the sea behind to the foot of the face
    private static final float[][] PROFILE = {
        {-90, -0.06F}, {-60, 0.02F}, {-40, 0.1F}, {-26, 0.24F}, {-15, 0.45F}, {-8, 0.68F}, {-3.5F, 0.86F},
        {0, 0.97F}, {2.5F, 1.0F}, {5, 0.97F}, {7.2F, 0.88F}, {8.6F, 0.74F}, {8.4F, 0.62F}, {6.5F, 0.6F},
        {4.6F, 0.53F}, {3.3F, 0.4F}, {2.6F, 0.26F}, {2.6F, 0.12F}, {3.6F, -0.04F},
    };
    // colour per profile point: deep back -> turquoise crest -> pale lip -> darker face -> foam foot
    private static final int[] COLORS = {
        0x0B3550, 0x0E4565, 0x125579, 0x17678C, 0x1D7C9F, 0x2794B2, 0x37ADC4,
        0x5CC8D6, 0x8FDDE6, 0xB4ECF0, 0xC8F2F5, 0xA6E2EA, 0x6EC2D2, 0x3E98B5,
        0x2A82A3, 0x23759A, 0x2D85A8, 0x8FC9DD, 0xE6F7FC,
    };
    private static final float[] ALPHAS = {
        0.0F, 0.85F, 0.9F, 0.9F, 0.9F, 0.88F, 0.85F, 0.8F, 0.78F, 0.78F, 0.8F, 0.82F, 0.88F, 0.9F,
        0.92F, 0.93F, 0.93F, 0.92F, 0.9F,
    };

    private TsunamiVisual() {}

    public static void submit(DisasterRenderState s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam) {
        final float h = s.c;
        if (h < 2) {
            return;
        }
        final float front = s.a;
        final float ang = s.b;
        final float width = Math.max(40, s.d);
        final float t = s.time;
        final float dirX = Mth.cos(ang), dirZ = Mth.sin(ang);
        final float travel = ang + Mth.PI;
        final float xScale = Mth.clamp((float) Math.pow(h / 45F, 0.75), 0.4F, 1.6F) * 1.25F;
        float fade = s.phase == 2 ? 1 - s.progress : 1;
        if (fade <= 0.01F) {
            return;
        }
        int light = LightCoordsUtil.pack(11, 15);

        ps.pushPose();
        ps.translate(dirX * front, -1.5F, dirZ * front);
        ps.rotate(Axis.YP.rotation(-travel));
        final float fa = fade;
        final int laterals = 64;

        // ---- the water body
        c.submitCustomGeometry(ps, TWRenderTypes.smoke(TWRenderTypes.tex("water")), (pose, vc) ->
            sheet(vc, pose, 0, PROFILE.length - 1, laterals, width, h, xScale, t, fa, light, false));
        // ---- foam riding the crest and lip
        c.submitCustomGeometry(ps, TWRenderTypes.sheer(TWRenderTypes.tex("foam")), (pose, vc) ->
            sheet(vc, pose, 6, 13, laterals, width, h, xScale, t, fa, light, true));
        // ---- white water boiling at the foot of the wave
        c.submitCustomGeometry(ps, TWRenderTypes.sheer(TWRenderTypes.tex("foam")), (pose, vc) -> {
            int segs = laterals;
            for (int j = 0; j < segs; j++) {
                float l0 = -width / 2 + width * j / segs, l1 = -width / 2 + width * (j + 1) / segs;
                float e0 = edge(l0, width), e1 = edge(l1, width);
                float b0 = bow(l0, width), b1 = bow(l1, width);
                for (int k = 0; k < 4; k++) {
                    float x0 = 2.5F + k * 4.5F, x1 = x0 + 4.5F;
                    float y0 = h * 0.16F * (1 - k / 4F) + 0.4F, y1 = h * 0.16F * (1 - (k + 1) / 4F) + 0.4F;
                    float a0 = fa * 0.9F * (1 - k / 4F), a1 = fa * 0.9F * (1 - (k + 1) / 4F);
                    float u0 = l0 / 14 + t * 0.01F, u1 = l1 / 14 + t * 0.01F;
                    Mesh.v(vc, pose, (x0 + b0) * xScale, y0 * e0, l0, Mesh.argb(a0 * e0, 0xF2FBFF), u0, k / 4F - t * 0.03F, light);
                    Mesh.v(vc, pose, (x0 + b1) * xScale, y0 * e1, l1, Mesh.argb(a0 * e1, 0xF2FBFF), u1, k / 4F - t * 0.03F, light);
                    Mesh.v(vc, pose, (x1 + b1) * xScale, y1 * e1, l1, Mesh.argb(a1 * e1, 0xF2FBFF), u1, (k + 1) / 4F - t * 0.03F, light);
                    Mesh.v(vc, pose, (x1 + b0) * xScale, y1 * e0, l0, Mesh.argb(a1 * e0, 0xF2FBFF), u0, (k + 1) / 4F - t * 0.03F, light);
                }
            }
        });
        ps.popPose();
    }

    private static float edge(float l, float width) {
        float e = Math.abs(l) / (width / 2);
        return Mth.clamp(1 - e * e * e * e, 0.05F, 1);
    }

    private static float bow(float l, float width) {
        float e = l / (width / 2);
        return -e * e * 22;
    }

    private static void sheet(VertexConsumer vc, com.mojang.blaze3d.vertex.PoseStack.Pose pose, int from, int to, int laterals,
                              float width, float h, float xScale, float t, float fade, int light, boolean foam) {
        float lift = foam ? 0.35F : 0;
        for (int j = 0; j < laterals; j++) {
            float l0 = -width / 2 + width * j / laterals, l1 = -width / 2 + width * (j + 1) / laterals;
            float e0 = edge(l0, width), e1 = edge(l1, width);
            float b0 = bow(l0, width), b1 = bow(l1, width);
            float w0 = Mth.sin(t * 0.09F + l0 * 0.06F) * 0.8F, w1 = Mth.sin(t * 0.09F + l1 * 0.06F) * 0.8F;
            for (int i = from; i < to; i++) {
                float[] p = PROFILE[i], q = PROFILE[i + 1];
                float curl0 = i >= 9 ? w0 : 0, curl1 = i >= 9 ? w1 : 0;
                float curlQ0 = i + 1 >= 9 ? w0 : 0, curlQ1 = i + 1 >= 9 ? w1 : 0;
                float px0 = (p[0] + b0 + curl0) * xScale, py0 = p[1] * h * e0 + lift;
                float px1 = (p[0] + b1 + curl1) * xScale, py1 = p[1] * h * e1 + lift;
                float qx0 = (q[0] + b0 + curlQ0) * xScale, qy0 = q[1] * h * e0 + lift;
                float qx1 = (q[0] + b1 + curlQ1) * xScale, qy1 = q[1] * h * e1 + lift;
                float vp = i / (float) PROFILE.length * 3 + t * (foam ? 0.02F : 0.035F);
                float vq = (i + 1) / (float) PROFILE.length * 3 + t * (foam ? 0.02F : 0.035F);
                float u0 = l0 / (foam ? 12 : 22) + t * 0.002F, u1 = l1 / (foam ? 12 : 22) + t * 0.002F;
                int cp, cq;
                if (foam) {
                    float ap = fade * 0.9F * (i == from ? 0 : 1), aq = fade * 0.9F * (i + 1 == to ? 0.2F : 1);
                    cp = Mesh.argb(ap, 0xF4FCFF);
                    cq = Mesh.argb(aq, 0xF4FCFF);
                } else {
                    cp = Mesh.argb(ALPHAS[i] * fade, COLORS[i]);
                    cq = Mesh.argb(ALPHAS[i + 1] * fade, COLORS[i + 1]);
                }
                Mesh.v(vc, pose, px0, py0, l0, cp, u0, vp, light);
                Mesh.v(vc, pose, px1, py1, l1, cp, u1, vp, light);
                Mesh.v(vc, pose, qx1, qy1, l1, cq, u1, vq, light);
                Mesh.v(vc, pose, qx0, qy0, l0, cq, u0, vq, light);
            }
        }
    }
}
