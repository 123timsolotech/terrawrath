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
 * The whiteout wall: towering curtains of wind-driven snow rolling outward, with a drift of ground snow.
 */
public final class BlizzardVisual {
    private BlizzardVisual() {}

    public static void submit(DisasterRenderState s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam) {
        final float radius = s.a;
        final float p = s.presence;
        if (radius < 2 || p <= 0.01F || s.phase == com.terrawrath.entity.DisasterEntity.WARNING) {
            return;
        }
        final float t = s.time;
        int light = LightCoordsUtil.pack(12, 15);
        final float height = 105 + 30 * s.intensity;
        for (int k = 0; k < 3; k++) {
            final float rr = radius - k * 6;
            if (rr < 2) {
                continue;
            }
            final float a = p * (k == 0 ? 1.0F : k == 1 ? 0.8F : 0.6F);
            final int kk = k;
            final int segs = Math.max(64, Math.min(180, (int) (rr * 1.1F)));
            final float uRep = Math.max(4, rr / 8);
            c.submitCustomGeometry(ps, TWRenderTypes.smoke(TWRenderTypes.tex("snow_wall")), (pose, vc) ->
                curtain(vc, pose, rr, height, segs, 12, uRep, t, kk, a, light));
        }
        // ground drift spilling ahead of the wall
        ps.pushPose();
        ps.translate(0, 0.6F, 0);
        final float rIn = Math.max(0, radius - 30);
        c.submitCustomGeometry(ps, TWRenderTypes.smoke(TWRenderTypes.tex("snow_wall")), (pose, vc) ->
            Mesh.annulus(vc, pose, rIn, radius + 6, 4, Math.max(48, Math.min(160, (int) radius)), Math.max(6, radius / 6), t * 0.004F, t * 0.02F,
                tt -> Mesh.argb(p * 0.7F * Mth.sin(tt * Mth.PI), 0xF0F6FF), light));
        ps.popPose();
    }

    /** A billowing curtain of snow: wavy top edge, rippling surface, dense grey base fading to white. */
    private static void curtain(com.mojang.blaze3d.vertex.VertexConsumer vc, com.mojang.blaze3d.vertex.PoseStack.Pose pose, float r, float height,
                                int segs, int rows, float uRep, float t, int layer, float alpha, int light) {
        float uOff = t * (0.012F + layer * 0.006F);
        float vOff = t * (0.03F + layer * 0.01F);
        for (int i = 0; i < segs; i++) {
            float a0 = i * Mth.TWO_PI / segs, a1 = (i + 1) * Mth.TWO_PI / segs;
            float top0 = height * (0.62F + 0.38F * (0.5F + 0.5F * Mesh.wave(a0 * 6 + t * 0.01F, layer * 3)));
            float top1 = height * (0.62F + 0.38F * (0.5F + 0.5F * Mesh.wave(a1 * 6 + t * 0.01F, layer * 3)));
            for (int j = 0; j < rows; j++) {
                float f0 = j / (float) rows, f1 = (j + 1) / (float) rows;
                float y00 = -8 + f0 * top0, y10 = -8 + f0 * top1, y01 = -8 + f1 * top0, y11 = -8 + f1 * top1;
                float b00 = r + billow(a0, y00, t, layer), b10 = r + billow(a1, y10, t, layer);
                float b01 = r + billow(a0, y01, t, layer), b11 = r + billow(a1, y11, t, layer);
                int c0 = color(f0, alpha), c1 = color(f1, alpha);
                float u0 = i / (float) segs * uRep + uOff, u1 = (i + 1) / (float) segs * uRep + uOff;
                Mesh.v(vc, pose, Mth.cos(a0) * b00, y00, Mth.sin(a0) * b00, c0, u0, f0 * 3 + vOff, light);
                Mesh.v(vc, pose, Mth.cos(a1) * b10, y10, Mth.sin(a1) * b10, c0, u1, f0 * 3 + vOff, light);
                Mesh.v(vc, pose, Mth.cos(a1) * b11, y11, Mth.sin(a1) * b11, c1, u1, f1 * 3 + vOff, light);
                Mesh.v(vc, pose, Mth.cos(a0) * b01, y01, Mth.sin(a0) * b01, c1, u0, f1 * 3 + vOff, light);
            }
        }
    }

    private static float billow(float ang, float y, float t, int layer) {
        return Mth.sin(ang * 9 + y * 0.06F + t * 0.05F + layer) * 3.5F + Mth.sin(ang * 23 - t * 0.08F) * 1.2F + y * 0.08F;
    }

    private static int color(float f, float alpha) {
        float a = Math.min(1, alpha * (1.12F - (float) Math.pow(f, 1.8)));
        return Mesh.argb(a, Mesh.lerpRgb(0xA4B2BF, 0xF4F7FA, (float) Math.sqrt(f)));
    }

    static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
