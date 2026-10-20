package com.terrawrath.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.terrawrath.TerraWrath;
import com.terrawrath.entity.BoltEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Giant branching lightning: a jagged main channel with recursive forks, drawn as layered camera-facing
 * ribbons (white-hot core, coloured body, wide soft glow) that strobe and fade.
 */
public class BoltRenderer extends EntityRenderer<BoltEntity, BoltRenderer.State> {
    private static final Identifier GLOW = TerraWrath.id("textures/particle/glow.png");

    public static class State extends EntityRenderState {
        public int seed;
        public int color;
        public float height;
        public float width;
        public float age;
    }

    private record Seg(Vector3f a, Vector3f b, float w) {}

    public BoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    protected boolean affectedByCulling(BoltEntity entity) {
        return false;
    }

    @Override
    public void extractRenderState(BoltEntity entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.seed = entity.seed();
        state.color = entity.color();
        state.height = entity.height();
        state.width = entity.width();
        state.age = entity.tickCount + partialTicks;
    }

    private static float brightness(float age) {
        if (age < 2.5F) {
            return 1;
        }
        if (age < 4) {
            return 0.25F;
        }
        if (age < 6) {
            return 0.9F;
        }
        return Mth.clamp(1 - (age - 6) / 6F, 0, 1) * 0.8F;
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam) {
        if (ShaderCompat.shadowPass()) {
            return;
        }
        float bright = brightness(s.age);
        if (bright <= 0.01F) {
            return;
        }
        List<Seg> segs = new ArrayList<>();
        RandomSource r = RandomSource.createThreadLocalInstance(s.seed);
        build(segs, r, new Vector3f(0, s.height, 0), new Vector3f(0, 0, 0), s.width * 1.0F, 0, s.height);
        final Vector3f camPos = new Vector3f(cam.pos.x() > 0 ? 0 : 0, 0, 0);
        camPos.set((float) (cam.pos.x - s.x), (float) (cam.pos.y - s.y), (float) (cam.pos.z - s.z));
        final int body = s.color;
        final float b = bright;
        c.submitCustomGeometry(ps, TWRenderTypes.beam(), (pose, vc) -> {
            Matrix4f m = pose.pose();
            for (Seg g : segs) {
                ribbon(vc, m, g, camPos, g.w * 4.5F, Mesh.argb(0.16F * b, body));
            }
            for (Seg g : segs) {
                ribbon(vc, m, g, camPos, g.w * 1.3F, Mesh.argb(0.55F * b, body));
            }
            for (Seg g : segs) {
                ribbon(vc, m, g, camPos, g.w * 0.38F, Mesh.argb(1.0F * b, 0xFFFFFF));
            }
        });
        // ground flash where it hits
        ps.pushPose();
        ps.translate(0, 0.5F, 0);
        ps.rotate(cam.orientation);
        c.submitCustomGeometry(ps, TWRenderTypes.glow(GLOW), (pose, vc) -> {
            Mesh.billboard(vc, pose, 9 * s.width, Mesh.argb(0.85F * b, body), LightCoordsUtil.FULL_BRIGHT);
            Mesh.billboard(vc, pose, 3 * s.width, Mesh.argb(b, 0xFFFFFF), LightCoordsUtil.FULL_BRIGHT);
        });
        ps.popPose();
    }

    private static void ribbon(VertexConsumer vc, Matrix4f m, Seg g, Vector3f cam, float w, int col) {
        Mesh.ribbon(vc, m, g.a, g.b, cam, w, w, col, col);
    }

    /** Recursive jagged channel from {@code top} toward {@code bottom}, spawning forks. */
    private static void build(List<Seg> out, RandomSource r, Vector3f top, Vector3f bottom, float width, int depth, float height) {
        int steps = depth == 0 ? 22 : 9;
        Vector3f prev = new Vector3f(top);
        float jag = depth == 0 ? height * 0.045F : height * 0.035F;
        for (int i = 1; i <= steps; i++) {
            float f = i / (float) steps;
            Vector3f next = new Vector3f(top).lerp(bottom, f);
            if (i < steps) {
                next.add((r.nextFloat() - 0.5F) * jag * 2, (r.nextFloat() - 0.5F) * jag * 0.6F, (r.nextFloat() - 0.5F) * jag * 2);
            }
            float w = width * (1 - f * 0.35F);
            out.add(new Seg(prev, next, w));
            if (depth < 2 && i < steps - 2 && r.nextFloat() < (depth == 0 ? 0.32F : 0.18F)) {
                Vector3f branchEnd = new Vector3f(next).add(
                    (r.nextFloat() - 0.5F) * height * 0.5F,
                    -height * (0.12F + r.nextFloat() * 0.25F),
                    (r.nextFloat() - 0.5F) * height * 0.5F);
                if (branchEnd.y < 0) {
                    branchEnd.y = r.nextFloat() * 4;
                }
                build(out, r, next, branchEnd, w * 0.5F, depth + 1, height * 0.5F);
            }
            prev = next;
        }
    }
}
