package com.terrawrath.client.gui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.terrawrath.TerraWrath;
import com.terrawrath.client.DisasterClientState;
import com.terrawrath.disaster.DisasterType;
import com.terrawrath.entity.DisasterEntity;
import java.lang.reflect.Field;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2f;
import org.joml.Vector3fc;

/**
 * Gravitational lensing around the Event Horizon core. The finished frame (world, post effects and any shader
 * pack) is copied right before the GUI draws; a polar mesh around the black hole then redraws that image
 * with every vertex sampling where its light really came from: beta = theta - thetaE^2 / theta. Light from
 * behind the hole wraps around it into an Einstein ring, the disk's far side arches over the shadow and the
 * sky near the edge is mirrored. The bending grows as you approach the core. One draw call.
 */
public final class LensEffect {
    private static final int RINGS = 44;
    private static final int SEGMENTS = 120;
    private static final Matrix3x2f IDENTITY = new Matrix3x2f();

    private static GpuTexture copy;
    private static GpuTextureView copyView;
    private static int copyW, copyH;
    private static boolean failed;
    private static float strength;
    /** Set while the lens is queued this frame: the frame is then copied right before the GUI draws. */
    private static boolean wanted;
    private static int grabbedFrames;
    private static Field stateField;

    private LensEffect() {}

    public static void render(Minecraft mc, GuiGraphicsExtractor gg, int w, int h, float pt) {
        if (failed || mc.level == null || mc.player == null) {
            return;
        }
        DisasterEntity hole = null;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof DisasterEntity d && d.disasterType() == DisasterType.EVENT_HORIZON && d.phase() == DisasterEntity.ACTIVE) {
                hole = d;
            }
        }
        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 cp = camera.position();
        float target = 0;
        float rh = 0;
        double dist = 0;
        if (hole != null) {
            DisasterClientState cs = DisasterClientState.of(hole);
            rh = Math.max(0.5F, cs.initialised ? cs.a(pt) : hole.a());
            dist = cp.distanceTo(hole.position());
            double near = rh * 1.6, far = rh * 14;
            target = (float) Math.pow(Mth.clamp(1 - (dist - near) / (far - near), 0, 1), 1.3);
        }
        strength += (target - strength) * 0.08F;
        if (strength < 0.02F || hole == null || dist < rh * 1.05) {
            return;
        }
        Vec3 c = hole.position();
        Vector3fc f = camera.forwardVector();
        if ((c.x - cp.x) * f.x() + (c.y - cp.y) * f.y() + (c.z - cp.z) * f.z() < rh) {
            return; // behind the camera
        }
        // project with the real view/projection (includes shake, sway and FOV changes)
        Vec3 ndc = mc.gameRenderer.projectPointToScreen(c);
        Vector3fc up = camera.upVector();
        Vec3 ndcEdge = mc.gameRenderer.projectPointToScreen(c.add(up.x() * rh, up.y() * rh, up.z() * rh));
        // which way is NDC y? compare with a plain pinhole projection and take the matching convention
        double ddx = c.x - cp.x, ddy = c.y - cp.y, ddz = c.z - cp.z;
        double zc = ddx * f.x() + ddy * f.y() + ddz * f.z();
        double yc = ddx * up.x() + ddy * up.y() + ddz * up.z();
        double tanHalf = Math.tan(Math.toRadians(Math.max(10, camera.getFov())) / 2);
        double approxY = h / 2.0 * (1 - yc / (zc * tanHalf));
        double ySign = Math.abs((1 - ndc.y) / 2 * h - approxY) <= Math.abs((1 + ndc.y) / 2 * h - approxY) ? 1 : -1;
        double cx = (ndc.x + 1) / 2 * w, cy = (1 - ySign * ndc.y) / 2 * h;
        double ex = (ndcEdge.x + 1) / 2 * w, ey = (1 - ySign * ndcEdge.y) / 2 * h;
        double shadow = Math.hypot(ex - cx, ey - cy);
        if (cx < -w * 0.5 || cx > w * 1.5 || cy < -h * 0.5 || cy > h * 1.5 || shadow < 2) {
            return;
        }
        if (!prepare(mc)) {
            return;
        }
        wanted = true;
        if (grabbedFrames == 0) {
            return; // the copy hook has not run yet: never sample an empty texture
        }
        double thetaE = shadow * 1.55;
        double k = strength * thetaE * thetaE;
        double region = Math.min(k / 0.35, Math.hypot(w, h) * 0.75);
        if (region <= shadow * 1.05) {
            return;
        }
        submit(gg, new LensMesh(copyView, (float) cx, (float) cy, (float) shadow, (float) region, (float) k, w, h,
            (float) (mc.getWindow().getGuiScale() / copyW), (float) (mc.getWindow().getGuiScale() / copyH)));
    }

    private static void submit(GuiGraphicsExtractor gg, GuiElementRenderState state) {
        try {
            if (stateField == null) {
                stateField = GuiGraphicsExtractor.class.getDeclaredField("guiRenderState");
                stateField.setAccessible(true);
            }
            ((GuiRenderState) stateField.get(gg)).addGuiElement(state);
        } catch (Throwable t) {
            failed = true;
            TerraWrath.LOG.warn("TerraWrath: gravitational lensing disabled: {}", t.toString());
        }
    }

    /** The lens as one textured mesh: rings around the shadow out to where the bending fades away. */
    private record LensMesh(GpuTextureView view, float cx, float cy, float shadow, float region, float k, int w, int h,
                            float uScale, float vScale) implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer vc) {
            float r0 = shadow * 0.985F;
            float[] rr = new float[RINGS + 1];
            for (int i = 0; i <= RINGS; i++) {
                float t = i / (float) RINGS;
                rr[i] = r0 + (region - r0) * t * t; // dense near the Einstein ring
            }
            float[] cos = new float[SEGMENTS + 1], sin = new float[SEGMENTS + 1];
            for (int j = 0; j <= SEGMENTS; j++) {
                float a = j * Mth.TWO_PI / SEGMENTS;
                cos[j] = Mth.cos(a);
                sin[j] = Mth.sin(a);
            }
            for (int i = 0; i < RINGS; i++) {
                float ra = rr[i], rb = rr[i + 1];
                float ba = beta(ra), bb = beta(rb);
                for (int j = 0; j < SEGMENTS; j++) {
                    vertex(vc, ra, ba, cos[j], sin[j]);
                    vertex(vc, rb, bb, cos[j], sin[j]);
                    vertex(vc, rb, bb, cos[j + 1], sin[j + 1]);
                    vertex(vc, ra, ba, cos[j + 1], sin[j + 1]);
                }
            }
        }

        private float beta(float theta) {
            double fade = 1 - smooth(region * 0.6, region, theta);
            return (float) (theta - k / Math.max(0.5, theta) * fade);
        }

        private void vertex(VertexConsumer vc, float r, float b, float cs, float sn) {
            float x = cx + cs * r, y = cy + sn * r;
            float sx = cx + cs * b, sy = cy + sn * b;
            vc.addVertexWith2DPose(IDENTITY, x, y).setUv(sx * uScale, 1 - sy * vScale).setColor(0xFFFFFFFF);
        }

        @Override
        public RenderPipeline pipeline() {
            return RenderPipelines.GUI_TEXTURED;
        }

        @Override
        public TextureSetup textureSetup() {
            return TextureSetup.singleTexture(view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        }

        @Override
        public ScreenRectangle scissorArea() {
            return null;
        }

        @Override
        public ScreenRectangle bounds() {
            int x0 = (int) Math.max(0, cx - region), y0 = (int) Math.max(0, cy - region);
            int x1 = (int) Math.min(w, cx + region), y1 = (int) Math.min(h, cy + region);
            return new ScreenRectangle(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0));
        }
    }

    /** Called from GameRenderer.render() just before the GUI is drawn. */
    public static void grabFrame() {
        if (!wanted || failed || copy == null) {
            return;
        }
        wanted = false;
        try {
            GpuTexture src = Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTexture();
            if (src == null || src.getWidth(0) != copyW || src.getHeight(0) != copyH) {
                return;
            }
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(src, copy, 0, 0, 0, 0, 0, copyW, copyH);
            grabbedFrames++;
        } catch (Throwable t) {
            failed = true;
            TerraWrath.LOG.warn("TerraWrath: gravitational lensing disabled: {}", t.toString());
        }
    }

    /** Makes sure the copy texture matches the main render target. */
    private static boolean prepare(Minecraft mc) {
        try {
            RenderTarget main = mc.gameRenderer.mainRenderTarget();
            GpuTexture src = main.getColorTexture();
            if (src == null) {
                return false;
            }
            int sw = src.getWidth(0), sh = src.getHeight(0);
            if (copy == null || copy.isClosed() || copyW != sw || copyH != sh || copy.getFormat() != src.getFormat()) {
                release();
                copy = RenderSystem.getDevice().createTexture("terrawrath_lens", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    src.getFormat(), sw, sh, 1, 1);
                copyView = RenderSystem.getDevice().createTextureView(copy);
                copyW = sw;
                copyH = sh;
                grabbedFrames = 0;
            }
            return true;
        } catch (Throwable t) {
            failed = true;
            TerraWrath.LOG.warn("TerraWrath: gravitational lensing disabled: {}", t.toString());
            return false;
        }
    }

    private static void release() {
        if (copyView != null) {
            copyView.close();
            copyView = null;
        }
        if (copy != null) {
            copy.close();
            copy = null;
        }
    }

    private static double smooth(double e0, double e1, double x) {
        double t = Mth.clamp((x - e0) / (e1 - e0), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
