package com.terrawrath.client.render.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.terrawrath.TerraWrath;
import com.terrawrath.client.render.DisasterRenderState;
import com.terrawrath.client.render.Mesh;
import com.terrawrath.client.render.TWRenderTypes;
import com.terrawrath.disaster.DisasterType;
import com.terrawrath.entity.DisasterEntity;
import com.terrawrath.client.render.ShaderCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * SIGNATURE VISUAL — a colossal black hole: a dark core ringed by a clean white photon ring, a lensed glowing
 * halo, a huge smooth swirling accretion disk (white-gold at the core, through orange and rose, into violet
 * haze), a needle-thin jet reaching the ground and, at the end, a supernova that swallows the sky in white.
 */
public final class HorizonVisual {
    private static final Identifier GLOW = TerraWrath.id("textures/particle/glow.png");
    private static final Identifier WHITE = TWRenderTypes.tex("white");
    private static final Identifier ACCRETION = TWRenderTypes.tex("accretion");
    private static final int FULL = LightCoordsUtil.FULL_BRIGHT;

    // accretion disk colour ramp, inner edge -> outer edge
    private static final float[] DISK_T = {0.0F, 0.07F, 0.2F, 0.4F, 0.66F, 1.0F};
    private static final int[] DISK_C = {0xFFF6E4, 0xFFCC80, 0xFF9050, 0xEC6264, 0xA23E80, 0x3C1652};
    private static final float[] DISK_A = {1.0F, 0.95F, 0.85F, 0.62F, 0.32F, 0.0F};
    // wide violet haze around the disk
    private static final float[] HAZE_T = {0.0F, 0.3F, 0.65F, 1.0F};
    private static final int[] HAZE_C = {0xC0507A, 0x8A3A8A, 0x5A2A7A, 0x2A1240};
    private static final float[] HAZE_A = {0.0F, 0.5F, 0.32F, 0.0F};

    private HorizonVisual() {}

    public static void submit(DisasterRenderState s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam) {
        final float horizonMax = Math.max(3, s.radius / 6F);
        final float rh = Math.max(0.05F, s.a);
        final float form = Mth.clamp(rh / horizonMax, 0, 1);
        final float collapse = s.b;
        final float flare = s.c;
        final float t = s.time;
        final float spin = 1 + collapse * 6;

        if (s.phase == DisasterEntity.ENDING) {
            submitSupernova(s, ps, c, cam, horizonMax);
            return;
        }
        // Sky-scale trick: when the hole is far away, draw it shrunk toward the camera along the same view rays.
        // It looks identical (same direction and angular size) but stays inside the fog-free range of the
        // render distance and of any shader pack's own fog.
        final boolean shaders = ShaderCompat.shaderPack();
        final float reach = horizonMax * (9.5F + 1.5F * s.intensity) * 1.45F;
        final double dist = Math.sqrt(s.camX * s.camX + s.camY * s.camY + s.camZ * s.camZ);
        final double fogFree = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16 * (shaders ? 0.5 : 0.8);
        final float project = (float) Math.min(1, fogFree / Math.max(1, dist + reach));
        ps.pushPose();
        if (project < 0.999F) {
            ps.translate(s.camX, s.camY, s.camZ);
            ps.scale(project, project, project);
            ps.translate(-s.camX, -s.camY, -s.camZ);
        }
        submitHole(s, ps, c, cam, horizonMax, rh, form, collapse, flare, t, spin, shaders);
        ps.popPose();
    }

    private static void submitHole(DisasterRenderState s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam, float horizonMax,
                                   float rh, float form, float collapse, float flare, float t, float spin, boolean shaders) {
        final boolean warning = s.phase == DisasterEntity.WARNING;

        // ---- before ignition: a violet star swelling in the darkening sky
        if (warning) {
            ps.pushPose();
            ps.rotate(cam.orientation);
            final float p = s.progress;
            c.submitCustomGeometry(ps, TWRenderTypes.glow(GLOW), (pose, vc) -> {
                Mesh.billboard(vc, pose, 6 + p * p * 40, Mesh.argb(0.3F + 0.45F * p, 0x9B6BFF), FULL);
                Mesh.billboard(vc, pose, 2 + p * 7, Mesh.argb(0.95F, 0xFFFFFF), FULL);
            });
            ps.popPose();
        }
        final float diskAlpha = warning ? smooth(0.7F, 1.0F, s.progress) * 0.3F : Math.min(1, 0.3F + form);
        final float diskR0 = Math.max(rh, horizonMax * form) * 1.45F;
        final float diskR1 = horizonMax * (9.5F + 1.5F * s.intensity) * (0.3F + 0.7F * form) * (1 - 0.55F * collapse);

        // ---- the core (writes depth, so everything behind it is hidden)
        if (rh > 0.3F) {
            c.submitCustomGeometry(ps, RenderTypes.entitySolid(WHITE), (pose, vc) ->
                Mesh.sphere(vc, pose, rh, 24, 40, 0xFF1C1226, 1, 1, 0, 0, 0));
        }

        // ---- accretion disk + violet haze (nearly face-on to the ground, slowly precessing)
        if (diskAlpha > 0.005F) {
            ps.pushPose();
            ps.rotate(Axis.YP.rotation(t * 0.0006F));
            ps.rotate(Axis.XP.rotation(0.12F + 0.03F * Mth.sin(t * 0.003F)));
            final float camAng = (float) Math.atan2(s.camZ, s.camX);
            final float a = diskAlpha * (1 + flare * 0.5F);
            c.submitCustomGeometry(ps, TWRenderTypes.glow(ACCRETION), (pose, vc) -> {
                if (!shaders) {
                    disk(vc, pose, diskR1 * 0.25F, diskR1 * 1.45F, t * 0.0025F * spin, 0.4F, camAng, a * 0.55F, HAZE_T, HAZE_C, HAZE_A, 0.15F, 0.37F, 20, 120);
                }
                disk(vc, pose, diskR0, diskR1, t * 0.011F * spin, 0.55F, camAng, a, DISK_T, DISK_C, DISK_A, 0.25F, 0.0F, shaders ? 20 : 30, shaders ? 112 : 160);
            });
            ps.popPose();
        }

        // ---- seen edge-on the flat disk would vanish to a line: a glare band keeps it blazing
        if (diskAlpha > 0.005F) {
            double horiz = Math.sqrt(s.camX * s.camX + s.camZ * s.camZ);
            float sinE = (float) (Math.abs(s.camY) / Math.max(1, Math.sqrt(horiz * horiz + s.camY * s.camY)));
            final float edgeOn = Mth.clamp(1 - sinE / 0.4F, 0, 1) * diskAlpha;
            if (edgeOn > 0.01F) {
                ps.pushPose();
                ps.rotate(cam.orientation);
                ps.scale(1, 0.1F + sinE * 0.6F, 1);
                c.submitCustomGeometry(ps, TWRenderTypes.glow(GLOW), (pose, vc) -> {
                    Mesh.billboard(vc, pose, diskR1 * 1.05F, Mesh.argb(edgeOn * 0.55F, 0xE0684C), FULL);
                    Mesh.billboard(vc, pose, diskR1 * 0.55F, Mesh.argb(edgeOn * 0.75F, 0xFFC27A), FULL);
                    Mesh.billboard(vc, pose, diskR1 * 0.22F, Mesh.argb(edgeOn * 0.8F, 0xFFF0D8), FULL);
                });
                ps.popPose();
            }
        }

        // ---- camera-facing layers: lensed halo, photon ring, bloom
        ps.pushPose();
        ps.rotate(cam.orientation);
        if (rh > 0.3F) {
            final float halo = diskAlpha * (1 + flare * 0.6F);
            final float flicker = collapse > 0 ? 0.8F + 0.2F * Mth.sin(t * 2.1F) : 1;
            c.submitCustomGeometry(ps, TWRenderTypes.glow(ACCRETION), (pose, vc) ->
                ring(vc, pose, rh * 1.02F, rh * 2.9F, 14, 128, 2, -t * 0.006F * spin, tt -> {
                    float k = (float) Math.pow(1 - tt, 1.7);
                    return Mesh.argb(Mth.clamp(halo * 0.95F * k * flicker, 0, 1), ramp(tt * 0.75F, DISK_T, DISK_C));
                }));
            c.submitCustomGeometry(ps, TWRenderTypes.glow(WHITE), (pose, vc) ->
                ring(vc, pose, rh * 0.985F, rh * 1.5F, 12, 160, 1, 0, tt -> {
                    float peak = tt < 0.1F ? smooth(0, 0.1F, tt) : (float) Math.pow(1 - (tt - 0.1F) / 0.9F, 2.4);
                    return Mesh.argb(Mth.clamp(halo * peak * flicker, 0, 1), Mesh.lerpRgb(0xFFFFFF, 0xFFD8A8, tt * 1.4F));
                }));
        }
        final float bloom = (0.2F + 0.3F * form + flare * 0.8F) * (warning ? s.progress : 1);
        c.submitCustomGeometry(ps, TWRenderTypes.glow(GLOW), (pose, vc) -> {
            Mesh.billboard(vc, pose, horizonMax * 13 * (0.4F + 0.6F * form), Mesh.argb(bloom * 0.32F, 0x8E4BCF), FULL);
            Mesh.billboard(vc, pose, horizonMax * 5.5F, Mesh.argb(bloom * 0.4F, 0xFF9A5A), FULL);
            if (flare > 0) {
                Mesh.billboard(vc, pose, horizonMax * (1.5F + flare * 6), Mesh.argb(flare * 0.95F, 0xFFFFFF), FULL);
            }
        });
        ps.popPose();

        // ---- needle-thin relativistic jet: up into space and down to the ground
        if (s.phase == DisasterEntity.ACTIVE && form > 0.2F) {
            final float up = 320 * smooth(0.2F, 1, form) * (1 + collapse * 0.4F);
            final float down = Math.max(10, (float) (s.y - s.ground) + 2) * smooth(0.2F, 0.7F, form);
            final float jetA = smooth(0.2F, 0.8F, form) * (0.85F + 0.15F * Mth.sin(t * 0.7F));
            float[][] layers = {{0.22F, 1.0F}, {0.6F, 0.6F}, {1.6F, 0.28F}};
            int[] cols = {0xFFFFFF, 0xE0D4FF, 0x9B6BFF};
            for (int dir = -1; dir <= 1; dir += 2) {
                final float len = dir > 0 ? up : down;
                final int d = dir;
                for (int k = 0; k < layers.length; k++) {
                    final float rf = layers[k][0], la = layers[k][1] * jetA;
                    final int col = cols[k];
                    Mesh.TubeProfile jet = new Mesh.TubeProfile() {
                        @Override
                        public void at(float sn, float[] out) {
                            out[0] = 0;
                            out[1] = d * (rh * 0.7F + sn * len);
                            out[2] = 0;
                            out[3] = (1.2F + sn * 1.6F) * rf * (1 + 0.12F * Mth.sin(sn * 40 - t * 0.6F));
                        }

                        @Override
                        public int color(float sn) {
                            float knots = 0.8F + 0.2F * Mth.sin(sn * 26 - t * 0.45F);
                            float end = d > 0 ? 1 - smooth(0.7F, 1, sn) : 1;
                            return Mesh.argb(la * knots * end * smooth(0, 0.02F, sn), col);
                        }
                    };
                    c.submitCustomGeometry(ps, TWRenderTypes.glow(TWRenderTypes.tex("beam")), (pose, vc) ->
                        Mesh.tube(vc, pose, jet, 36, 12, 1, 8, 0, -d * t * 0.08F, FULL));
                }
            }
            // where the jet touches the ground it boils
            ps.pushPose();
            ps.translate(0, -down + 1, 0);
            ps.rotate(cam.orientation);
            c.submitCustomGeometry(ps, TWRenderTypes.glow(GLOW), (pose, vc) ->
                Mesh.billboard(vc, pose, 9 + 2 * Mth.sin(t * 0.5F), Mesh.argb(jetA * 0.7F, 0xC9A6FF), FULL));
            ps.popPose();
        }
    }

    /**
     * A smooth flat disk in the XZ plane. Colour and alpha follow a radial ramp; the texture swirls with
     * differential rotation (inner parts orbit faster) and a gentle spiral twist.
     */
    private static void disk(VertexConsumer vc, PoseStack.Pose pose, float r0, float r1, float rot, float twist, float camAng, float alpha,
                             float[] rampT, int[] rampC, float[] rampA, float doppler, float vOffset, int rings, int segs) {
        float[] cos = new float[segs + 1], sin = new float[segs + 1];
        for (int i = 0; i <= segs; i++) {
            float a = i * Mth.TWO_PI / segs;
            cos[i] = Mth.cos(a);
            sin[i] = Mth.sin(a);
        }
        for (int r = 0; r < rings; r++) {
            float t0 = (float) Math.pow(r / (float) rings, 1.35), t1 = (float) Math.pow((r + 1) / (float) rings, 1.35);
            float ra = Mth.lerp(t0, r0, r1), rb = Mth.lerp(t1, r0, r1);
            float sa = rot * (float) Math.pow(r0 / ra, 0.7), sb = rot * (float) Math.pow(r0 / rb, 0.7);
            float wa = twist * (float) Math.log(ra / r0), wb = twist * (float) Math.log(rb / r0);
            int ca = ramp(t0, rampT, rampC), cb = ramp(t1, rampT, rampC);
            float aa = alpha * rampAlpha(t0, rampT, rampA) * smooth(0, 0.035F, t0);
            float ab = alpha * rampAlpha(t1, rampT, rampA) * smooth(0, 0.035F, t1);
            float va = t0 * 1.6F + vOffset, vb = t1 * 1.6F + vOffset;
            for (int i = 0; i < segs; i++) {
                float a0 = i * Mth.TWO_PI / segs, a1 = (i + 1) * Mth.TWO_PI / segs;
                float d0 = 1 + doppler * Mth.sin(a0 - camAng), d1 = 1 + doppler * Mth.sin(a1 - camAng);
                float u0a = (i / (float) segs + wa - sa) * 3, u1a = ((i + 1) / (float) segs + wa - sa) * 3;
                float u0b = (i / (float) segs + wb - sb) * 3, u1b = ((i + 1) / (float) segs + wb - sb) * 3;
                Mesh.v(vc, pose, cos[i] * ra, 0, sin[i] * ra, Mesh.argb(Mth.clamp(aa * d0, 0, 1), ca), u0a, va, FULL);
                Mesh.v(vc, pose, cos[i + 1] * ra, 0, sin[i + 1] * ra, Mesh.argb(Mth.clamp(aa * d1, 0, 1), ca), u1a, va, FULL);
                Mesh.v(vc, pose, cos[i + 1] * rb, 0, sin[i + 1] * rb, Mesh.argb(Mth.clamp(ab * d1, 0, 1), cb), u1b, vb, FULL);
                Mesh.v(vc, pose, cos[i] * rb, 0, sin[i] * rb, Mesh.argb(Mth.clamp(ab * d0, 0, 1), cb), u0b, vb, FULL);
            }
        }
    }

    /** Camera-facing ring in the pose's XY plane with a smooth radial colour profile. */
    private static void ring(VertexConsumer vc, PoseStack.Pose pose, float rIn, float rOut, int steps, int segs, float uRepeat, float uOff, Mesh.ColorAt color) {
        for (int r = 0; r < steps; r++) {
            float t0 = r / (float) steps, t1 = (r + 1) / (float) steps;
            float ra = Mth.lerp(t0, rIn, rOut), rb = Mth.lerp(t1, rIn, rOut);
            int ca = color.at(t0), cb = color.at(t1);
            for (int i = 0; i < segs; i++) {
                float a0 = i * Mth.TWO_PI / segs, a1 = (i + 1) * Mth.TWO_PI / segs;
                float u0 = i / (float) segs * uRepeat + uOff, u1 = (i + 1) / (float) segs * uRepeat + uOff;
                Mesh.v(vc, pose, Mth.cos(a0) * ra, Mth.sin(a0) * ra, 0, ca, u0, t0, FULL);
                Mesh.v(vc, pose, Mth.cos(a1) * ra, Mth.sin(a1) * ra, 0, ca, u1, t0, FULL);
                Mesh.v(vc, pose, Mth.cos(a1) * rb, Mth.sin(a1) * rb, 0, cb, u1, t1, FULL);
                Mesh.v(vc, pose, Mth.cos(a0) * rb, Mth.sin(a0) * rb, 0, cb, u0, t1, FULL);
            }
        }
    }

    private static int ramp(float t, float[] ts, int[] cs) {
        t = Mth.clamp(t, 0, 1);
        for (int i = 0; i < ts.length - 1; i++) {
            if (t <= ts[i + 1]) {
                return Mesh.lerpRgb(cs[i], cs[i + 1], (t - ts[i]) / (ts[i + 1] - ts[i]));
            }
        }
        return cs[cs.length - 1];
    }

    private static float rampAlpha(float t, float[] ts, float[] as) {
        t = Mth.clamp(t, 0, 1);
        for (int i = 0; i < ts.length - 1; i++) {
            if (t <= ts[i + 1]) {
                float k = (t - ts[i]) / (ts[i + 1] - ts[i]);
                k = k * k * (3 - 2 * k);
                return Mth.lerp(k, as[i], as[i + 1]);
            }
        }
        return as[as.length - 1];
    }

    /** Ending: a white shell races outward and swallows everything, leaving a fading violet remnant. */
    private static void submitSupernova(DisasterRenderState s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam, float horizonMax) {
        final float p = s.progress;
        final float t = s.time;
        final float tE = p * DisasterType.EVENT_HORIZON.endTicks;
        final float shell = supernovaRadius(tE);
        final float shellA = 1 - smooth(0.45F, 0.95F, p);
        c.submitCustomGeometry(ps, TWRenderTypes.glow(WHITE), (pose, vc) -> {
            Mesh.sphere(vc, pose, shell, 20, 40, Mesh.argb(shellA * 0.9F, 0xFFFFFF), 1, 1, 0, 0, FULL);
            Mesh.sphere(vc, pose, shell * 0.8F, 16, 32, Mesh.argb(shellA * 0.6F, 0xF0E4FF), 1, 1, 0, 0, FULL);
        });
        final float remnant = horizonMax * 3 + p * 160;
        final float ra = smooth(0.0F, 0.3F, p) * (1 - p) * (1 - p);
        c.submitCustomGeometry(ps, TWRenderTypes.glow(TWRenderTypes.tex("cloud")), (pose, vc) -> {
            Mesh.sphere(vc, pose, remnant, 16, 32, Mesh.argb(ra * 0.5F, 0xA77BFF), 3, 2, t * 0.003F, 0, FULL);
            Mesh.sphere(vc, pose, remnant * 0.72F, 14, 28, Mesh.argb(ra * 0.4F, 0xFF9A5A), 2, 2, -t * 0.004F, 0.3F, FULL);
        });
        ps.pushPose();
        ps.rotate(cam.orientation);
        final float core = s.c;
        c.submitCustomGeometry(ps, TWRenderTypes.glow(GLOW), (pose, vc) -> {
            Mesh.billboard(vc, pose, 40 + p * 160, Mesh.argb(core * 0.95F, 0xFFFFFF), FULL);
            Mesh.billboard(vc, pose, 120 + p * 260, Mesh.argb(core * 0.5F, 0xB98CFF), FULL);
        });
        ps.popPose();
    }

    /** Radius of the supernova shell {@code ticks} after the explosion (fast ease-out). */
    public static float supernovaRadius(float ticks) {
        float k = Mth.clamp(ticks / 28F, 0, 1);
        return 600 * (1 - (1 - k) * (1 - k) * (1 - k));
    }

    static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
