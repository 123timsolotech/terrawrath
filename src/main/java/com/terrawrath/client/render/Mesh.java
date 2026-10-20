package com.terrawrath.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Procedural geometry helpers: bending tubes, rings, discs, spheres and camera-facing ribbons.
 * All colours are ARGB ints.
 */
public final class Mesh {
    private Mesh() {}

    public static int argb(float a, int rgb) {
        int ai = Mth.clamp((int) (a * 255), 0, 255);
        return ai << 24 | rgb & 0xFFFFFF;
    }

    public static int argb(float a, float r, float g, float b) {
        return Mth.clamp((int) (a * 255), 0, 255) << 24 | Mth.clamp((int) (r * 255), 0, 255) << 16
            | Mth.clamp((int) (g * 255), 0, 255) << 8 | Mth.clamp((int) (b * 255), 0, 255);
    }

    public static int scaleRgb(int rgb, float f) {
        int r = Mth.clamp((int) ((rgb >> 16 & 255) * f), 0, 255);
        int g = Mth.clamp((int) ((rgb >> 8 & 255) * f), 0, 255);
        int b = Mth.clamp((int) ((rgb & 255) * f), 0, 255);
        return r << 16 | g << 8 | b;
    }

    public static int lerpRgb(int a, int b, float t) {
        t = Mth.clamp(t, 0, 1);
        int r = (int) Mth.lerp(t, a >> 16 & 255, b >> 16 & 255);
        int g = (int) Mth.lerp(t, a >> 8 & 255, b >> 8 & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return r << 16 | g << 8 | bl;
    }

    /** Entity-format vertex (position, colour, uv, overlay, light, normal). */
    public static void v(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, int color, float u, float v, int light) {
        vc.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
    }

    /** Position-colour vertex (beam pipeline). */
    public static void pc(VertexConsumer vc, Matrix4f m, float x, float y, float z, int color) {
        vc.addVertex(m, x, y, z).setColor(color);
    }

    /** A function that gives ring centre offsets / radius / colour along a tube parameter s in [0,1]. */
    public interface TubeProfile {
        /** fills out[0]=cx, out[1]=y, out[2]=cz, out[3]=radius */
        void at(float s, float[] out);

        default int color(float s) {
            return 0xFFFFFFFF;
        }
    }

    /**
     * A tube made of stacked rings following an arbitrary centre line (a twisting funnel, plume or jet).
     */
    public static void tube(VertexConsumer vc, PoseStack.Pose pose, TubeProfile profile, int rings, int segments,
                            float uRepeat, float vRepeat, float uOffset, float vOffset, int light) {
        float[] a = new float[4];
        float[] b = new float[4];
        float[] cos = new float[segments + 1];
        float[] sin = new float[segments + 1];
        for (int i = 0; i <= segments; i++) {
            float ang = (float) (i * Math.PI * 2 / segments);
            cos[i] = Mth.cos(ang);
            sin[i] = Mth.sin(ang);
        }
        profile.at(0, a);
        int colA = profile.color(0);
        for (int r = 1; r <= rings; r++) {
            float s1 = r / (float) rings;
            float s0 = (r - 1) / (float) rings;
            profile.at(s1, b);
            int colB = profile.color(s1);
            float v0 = s0 * vRepeat + vOffset, v1 = s1 * vRepeat + vOffset;
            for (int i = 0; i < segments; i++) {
                float u0 = i / (float) segments * uRepeat + uOffset;
                float u1 = (i + 1) / (float) segments * uRepeat + uOffset;
                v(vc, pose, a[0] + cos[i] * a[3], a[1], a[2] + sin[i] * a[3], colA, u0, v0, light);
                v(vc, pose, a[0] + cos[i + 1] * a[3], a[1], a[2] + sin[i + 1] * a[3], colA, u1, v0, light);
                v(vc, pose, b[0] + cos[i + 1] * b[3], b[1], b[2] + sin[i + 1] * b[3], colB, u1, v1, light);
                v(vc, pose, b[0] + cos[i] * b[3], b[1], b[2] + sin[i] * b[3], colB, u0, v1, light);
            }
            float[] t = a;
            a = b;
            b = t;
            colA = colB;
        }
    }

    /**
     * Horizontal annulus (y=0 plane) from rIn to rOut. u = angle (repeated), v = radius. Colours fade in/out.
     */
    public static void annulus(VertexConsumer vc, PoseStack.Pose pose, float rIn, float rOut, int radialSteps, int segments,
                               float uRepeat, float uOffset, float vOffset, ColorAt color, int light) {
        for (int r = 0; r < radialSteps; r++) {
            float t0 = r / (float) radialSteps, t1 = (r + 1) / (float) radialSteps;
            float r0 = Mth.lerp(t0, rIn, rOut), r1 = Mth.lerp(t1, rIn, rOut);
            int c0 = color.at(t0), c1 = color.at(t1);
            for (int i = 0; i < segments; i++) {
                float a0 = (float) (i * Math.PI * 2 / segments), a1 = (float) ((i + 1) * Math.PI * 2 / segments);
                float u0 = i / (float) segments * uRepeat + uOffset, u1 = (i + 1) / (float) segments * uRepeat + uOffset;
                float cos0 = Mth.cos(a0), sin0 = Mth.sin(a0), cos1 = Mth.cos(a1), sin1 = Mth.sin(a1);
                v(vc, pose, cos0 * r0, 0, sin0 * r0, c0, u0, t0 + vOffset, light);
                v(vc, pose, cos1 * r0, 0, sin1 * r0, c0, u1, t0 + vOffset, light);
                v(vc, pose, cos1 * r1, 0, sin1 * r1, c1, u1, t1 + vOffset, light);
                v(vc, pose, cos0 * r1, 0, sin0 * r1, c1, u0, t1 + vOffset, light);
            }
        }
    }

    public interface ColorAt {
        int at(float t);
    }

    /** UV sphere. */
    public static void sphere(VertexConsumer vc, PoseStack.Pose pose, float radius, int lat, int lon, int color,
                              float uRepeat, float vRepeat, float uOffset, float vOffset, int light) {
        for (int i = 0; i < lat; i++) {
            float t0 = (float) (Math.PI * i / lat), t1 = (float) (Math.PI * (i + 1) / lat);
            float y0 = Mth.cos(t0) * radius, y1 = Mth.cos(t1) * radius;
            float r0 = Mth.sin(t0) * radius, r1 = Mth.sin(t1) * radius;
            for (int j = 0; j < lon; j++) {
                float p0 = (float) (Math.PI * 2 * j / lon), p1 = (float) (Math.PI * 2 * (j + 1) / lon);
                float u0 = j / (float) lon * uRepeat + uOffset, u1 = (j + 1) / (float) lon * uRepeat + uOffset;
                float v0 = i / (float) lat * vRepeat + vOffset, v1 = (i + 1) / (float) lat * vRepeat + vOffset;
                v(vc, pose, Mth.cos(p0) * r0, y0, Mth.sin(p0) * r0, color, u0, v0, light);
                v(vc, pose, Mth.cos(p1) * r0, y0, Mth.sin(p1) * r0, color, u1, v0, light);
                v(vc, pose, Mth.cos(p1) * r1, y1, Mth.sin(p1) * r1, color, u1, v1, light);
                v(vc, pose, Mth.cos(p0) * r1, y1, Mth.sin(p0) * r1, color, u0, v1, light);
            }
        }
    }

    /** Camera-facing quad of half-size {@code size} at the pose origin (pose must already face the camera). */
    public static void billboard(VertexConsumer vc, PoseStack.Pose pose, float size, int color, int light) {
        v(vc, pose, -size, -size, 0, color, 0, 1, light);
        v(vc, pose, size, -size, 0, color, 1, 1, light);
        v(vc, pose, size, size, 0, color, 1, 0, light);
        v(vc, pose, -size, size, 0, color, 0, 0, light);
    }

    /**
     * A ribbon segment from A to B that always faces the camera (for bolts and trails). Positions are relative
     * to the pose origin; {@code cam} is the camera position in the same space.
     */
    public static void ribbon(VertexConsumer vc, Matrix4f m, Vector3f a, Vector3f b, Vector3f cam, float wa, float wb, int ca, int cb) {
        float dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
        float mx = (a.x + b.x) * 0.5F - cam.x, my = (a.y + b.y) * 0.5F - cam.y, mz = (a.z + b.z) * 0.5F - cam.z;
        // side = dir x view
        float sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
        float len = Mth.sqrt(sx * sx + sy * sy + sz * sz);
        if (len < 1.0E-5F) {
            return;
        }
        sx /= len;
        sy /= len;
        sz /= len;
        pc(vc, m, a.x - sx * wa, a.y - sy * wa, a.z - sz * wa, ca);
        pc(vc, m, a.x + sx * wa, a.y + sy * wa, a.z + sz * wa, ca);
        pc(vc, m, b.x + sx * wb, b.y + sy * wb, b.z + sz * wb, cb);
        pc(vc, m, b.x - sx * wb, b.y - sy * wb, b.z - sz * wb, cb);
    }

    /** Beam-format camera-facing ring (photon ring, shockwave glow) in the pose's XY plane. */
    public static void flatRing(VertexConsumer vc, Matrix4f m, float rIn, float rOut, int segments, int cIn, int cMid, int cOut) {
        float rMid = (rIn + rOut) * 0.5F;
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (i * Math.PI * 2 / segments), a1 = (float) ((i + 1) * Math.PI * 2 / segments);
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            pc(vc, m, c0 * rIn, s0 * rIn, 0, cIn);
            pc(vc, m, c1 * rIn, s1 * rIn, 0, cIn);
            pc(vc, m, c1 * rMid, s1 * rMid, 0, cMid);
            pc(vc, m, c0 * rMid, s0 * rMid, 0, cMid);
            pc(vc, m, c0 * rMid, s0 * rMid, 0, cMid);
            pc(vc, m, c1 * rMid, s1 * rMid, 0, cMid);
            pc(vc, m, c1 * rOut, s1 * rOut, 0, cOut);
            pc(vc, m, c0 * rOut, s0 * rOut, 0, cOut);
        }
    }

    /** Smooth periodic noise for wobble without allocation. */
    public static float wave(float x, float seed) {
        return (Mth.sin(x * 1.31F + seed) + Mth.sin(x * 2.17F + seed * 1.7F) * 0.5F + Mth.sin(x * 4.03F + seed * 0.3F) * 0.25F) / 1.75F;
    }
}
