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
 * A vast rotating black-purple supercell: layered spiral cloud decks, a lowered mesocyclone and rain shafts.
 * The whole deck lights up violet with every lightning flash.
 */
public final class StormVisual {
    private StormVisual() {}

    public static void submit(DisasterRenderState s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam) {
        final float p = s.presence;
        if (p <= 0.01F) {
            return;
        }
        final float t = s.time;
        final float base = (float) (s.c - s.y);
        final float flash = s.flash;
        final float rot = 1 + s.b * 0.6F;
        int light = LightCoordsUtil.pack(10, 15);
        final float deck = 240 + 40 * s.intensity;

        ps.pushPose();
        ps.translate(0, base, 0);
        // main deck
        c.submitCustomGeometry(ps, TWRenderTypes.smoke(TWRenderTypes.tex("swirl")), (pose, vc) ->
            Mesh.annulus(vc, pose, 0, deck, 12, 64, 1, t * 0.0011F * rot, 0,
                tt -> Mesh.argb(p * 0.96F * (1 - smooth(0.7F, 1, tt)), lit(0x261A38, flash, tt)), light));
        // upper deck, counter-sheared
        ps.translate(0, 9, 0);
        c.submitCustomGeometry(ps, TWRenderTypes.smoke(TWRenderTypes.tex("swirl")), (pose, vc) ->
            Mesh.annulus(vc, pose, 0, deck * 1.15F, 10, 64, 1, t * 0.0007F * rot + 0.37F, 0.5F,
                tt -> Mesh.argb(p * 0.95F * (1 - smooth(0.75F, 1, tt)), lit(0x46326A, flash, tt)), light));
        // low scud racing around
        ps.translate(0, -22, 0);
        c.submitCustomGeometry(ps, TWRenderTypes.smoke(TWRenderTypes.tex("cloud")), (pose, vc) ->
            Mesh.annulus(vc, pose, 25, deck * 0.7F, 6, 48, 4, t * 0.0026F * rot, 0,
                tt -> Mesh.argb(p * 0.7F * Mth.sin(tt * Mth.PI), lit(0x4A3A66, flash, tt)), light));
        ps.popPose();

        // mesocyclone: lowered rotating wall cloud at the centre
        Mesh.TubeProfile wall = new Mesh.TubeProfile() {
            @Override
            public void at(float sn, float[] out) {
                out[0] = Mth.sin(t * 0.01F) * 3;
                out[1] = base - 30 + sn * 31;
                out[2] = Mth.cos(t * 0.012F) * 3;
                out[3] = 42 + sn * sn * 30 + Mth.sin(t * 0.05F + sn * 5) * 2;
            }

            @Override
            public int color(float sn) {
                return Mesh.argb(p * 0.85F * smooth(0, 0.25F, sn), lit(0x1E1629, flash, 0.2F));
            }
        };
        c.submitCustomGeometry(ps, TWRenderTypes.smoke(TWRenderTypes.tex("swirl")), (pose, vc) ->
            Mesh.tube(vc, pose, wall, 6, 48, 3, 1, t * 0.004F * rot, 0, light));

        // rain shafts hanging from the deck
        for (int k = 0; k < 2; k++) {
            final float r = k == 0 ? 85 : 150;
            final float a = p * (k == 0 ? 0.3F : 0.2F);
            final float wind = s.b;
            Mesh.TubeProfile shaft = new Mesh.TubeProfile() {
                @Override
                public void at(float sn, float[] out) {
                    out[0] = Mth.cos(s.a) * (1 - sn) * 18 * wind;
                    out[1] = -6 + sn * (base - 4);
                    out[2] = Mth.sin(s.a) * (1 - sn) * 18 * wind;
                    out[3] = r;
                }

                @Override
                public int color(float sn) {
                    return Mesh.argb(a * smooth(0, 0.2F, sn) * (1 - smooth(0.85F, 1, sn)), lit(0x6E6A80, flash, 0.5F));
                }
            };
            final int kk = k;
            c.submitCustomGeometry(ps, TWRenderTypes.smoke(TWRenderTypes.tex("snow_wall")), (pose, vc) ->
                Mesh.tube(vc, pose, shaft, 6, 56, 14, 3, t * 0.002F * (kk + 1), t * 0.06F, light));
        }
    }

    private static int lit(int rgb, float flash, float tt) {
        if (flash <= 0) {
            return rgb;
        }
        int violet = Mesh.lerpRgb(rgb, 0xB8A0FF, Math.min(1, flash * (1.2F - tt * 0.6F)));
        return violet;
    }

    static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
