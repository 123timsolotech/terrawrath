package com.terrawrath.client.render.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.terrawrath.client.render.DisasterRenderState;
import com.terrawrath.client.render.Mesh;
import com.terrawrath.client.render.TWRenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * Meteor shower sky: subtle northern lights — slow, folding green curtains with faint violet crowns,
 * hanging high above the impact zone. They die away when the colossal meteor arrives.
 */
public final class AuroraVisual {
    private static final int FULL = LightCoordsUtil.FULL_BRIGHT;

    private AuroraVisual() {}

    public static void submit(DisasterRenderState s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam) {
        final float vis = s.presence * (1 - Mth.clamp((s.a - 0.2F) / 0.5F, 0, 1));
        if (vis <= 0.01F) {
            return;
        }
        final float t = s.time;
        ps.pushPose();
        ps.translate(0, 100, 0);
        c.submitCustomGeometry(ps, TWRenderTypes.glow(TWRenderTypes.tex("aurora")), (pose, vc) -> {
            curtain(vc, pose, t, 0, -40, 30, 360, 66, 0.75F * vis, 0.25F);
            curtain(vc, pose, t, 1, 90, -70, 320, 52, 0.6F * vis, 1.4F);
            curtain(vc, pose, t, 2, -120, 110, 280, 54, 0.4F * vis, -0.7F);
            curtain(vc, pose, t, 3, 150, 90, 240, 40, 0.32F * vis, 2.3F);
        });
        ps.popPose();
    }

    /** One folding curtain: bright green lower edge, rays rising and fading into violet. */
    private static void curtain(VertexConsumer vc, PoseStack.Pose pose, float t, int k, float cx, float cz, float length, float height,
                                float alpha, float heading) {
        int n = 72;
        float dx = Mth.cos(heading), dz = Mth.sin(heading);
        float px = -dz, pz = dx;
        float sway = t * 0.004F + k * 1.7F;
        float scroll = t * 0.0012F * (k % 2 == 0 ? 1 : -1);
        float prevX = 0, prevZ = 0, prevY = 0, prevH = 0, prevU = 0, prevA = 0;
        for (int i = 0; i <= n; i++) {
            float s = i / (float) n;
            float along = (s - 0.5F) * length;
            float fold = Mth.sin(s * 5.1F + sway) * 22 + Mth.sin(s * 11.3F - sway * 1.6F) * 7;
            float x = cx + dx * along + px * fold;
            float z = cz + dz * along + pz * fold;
            float y = Mth.sin(s * 3.3F + k + t * 0.002F) * 6;
            float h = height * (0.75F + 0.25F * Mth.sin(s * 7.7F + sway * 0.8F));
            float u = s * length / 60F + scroll;
            float edge = smooth(0, 0.12F, s) * (1 - smooth(0.88F, 1, s));
            float pulse = 0.8F + 0.2F * Mth.sin(s * 13 + t * 0.03F + k);
            float a = alpha * edge * pulse;
            if (i > 0) {
                int lowA = Mesh.argb(prevA, 0x5CFF9E), lowB = Mesh.argb(a, 0x5CFF9E);
                int topA = Mesh.argb(prevA * 0.7F, 0x8C6CFF), topB = Mesh.argb(a * 0.7F, 0x8C6CFF);
                Mesh.v(vc, pose, prevX, prevY, prevZ, lowA, prevU, 1, FULL);
                Mesh.v(vc, pose, x, y, z, lowB, u, 1, FULL);
                Mesh.v(vc, pose, x, y + h, z, topB, u, 0, FULL);
                Mesh.v(vc, pose, prevX, prevY + prevH, prevZ, topA, prevU, 0, FULL);
            }
            prevX = x;
            prevZ = z;
            prevY = y;
            prevH = h;
            prevU = u;
            prevA = a;
        }
    }

    static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
