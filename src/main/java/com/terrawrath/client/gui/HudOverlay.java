package com.terrawrath.client.gui;

import com.terrawrath.TerraWrath;
import com.terrawrath.client.CinematicCamera;
import com.terrawrath.client.ClientFX;
import com.terrawrath.client.DisasterClientState;
import com.terrawrath.disaster.DisasterType;
import com.terrawrath.entity.DisasterEntity;
import com.terrawrath.item.DisasterDetectorItem;
import com.terrawrath.registry.TWRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/**
 * Screen-space effects and cinematic warnings: flash, frost, heat haze, dread vignette, the huge
 * emergency-broadcast warning title, the detector readout and the cinematic letterbox.
 */
public final class HudOverlay {
    private static final Identifier FROST = TerraWrath.id("textures/misc/frost.png");
    private static final Identifier VIGNETTE = TerraWrath.id("textures/misc/vignette.png");
    private static final Identifier HAZE = TerraWrath.id("textures/misc/haze.png");
    private static final Identifier IRIS = TerraWrath.id("textures/misc/iris.png");
    private static final Identifier EDGE_L = TerraWrath.id("textures/misc/edge_l.png");
    private static final Identifier EDGE_R = TerraWrath.id("textures/misc/edge_r.png");
    private static final Identifier RIPPLE = TerraWrath.id("textures/misc/ripple.png");

    private HudOverlay() {}

    public static void render(GuiGraphicsExtractor gg, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        float pt = dt.getGameTimeDeltaPartialTick(false);
        int w = gg.guiWidth(), h = gg.guiHeight();
        float time = mc.level.getGameTime() + pt;

        // ---- gravitational lensing around the black hole (drawn first, everything else stays on top)
        LensEffect.render(mc, gg, w, h, pt);
        // ---- dread vignette (darkens edges while a disaster is near or building up)
        float vig = Math.max(ClientFX.weight() * 0.55F, ClientFX.dread * (0.55F + 0.25F * Mth.sin(time * 0.25F)));
        if (vig > 0.01F) {
            int tint = ClientFX.dread > 0.05F ? Mesh2D.mix(0x000000, ClientFX.dreadColor, 0.25F) : 0x000000;
            gg.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0, 0, w, h, 256, 144, 256, 144, Mesh2D.argb(Math.min(0.9F, vig), tint));
        }
        // ---- heat haze
        if (ClientFX.heat > 0.01F) {
            float a = ClientFX.heat;
            int scroll = (int) (time * 1.5F) % 256;
            gg.blit(RenderPipelines.GUI_TEXTURED, HAZE, 0, 0, 0, scroll, w, h, 256, 256, 256, 256, Mesh2D.argb(a * 0.28F, 0xFFFFFF));
            gg.fill(0, 0, w, h, Mesh2D.argb(a * 0.10F, 0xFF6A1F));
        }
        // ---- frost creeping over the screen edges
        if (ClientFX.frost > 0.01F) {
            gg.blit(RenderPipelines.GUI_TEXTURED, FROST, 0, 0, 0, 0, w, h, 512, 288, 512, 288, Mesh2D.argb(Math.min(1, ClientFX.frost * 1.15F), 0xFFFFFF));
        }
        // ---- event horizon: closing darkness and bent time
        horizonDarkness(mc, gg, w, h, pt);
        dilationOverlay(gg, w, h, pt);
        // ---- flash
        float flash = ClientFX.flash(pt);
        if (flash > 0.01F) {
            gg.fill(0, 0, w, h, Mesh2D.argb(Math.min(0.92F, flash * 0.8F), ClientFX.flashColor()));
        }
        // ---- cinematic letterbox
        if (CinematicCamera.active() && CinematicCamera.letterbox) {
            int bar = (int) (h * 0.12F);
            gg.fill(0, 0, w, bar, 0xFF000000);
            gg.fill(0, h - bar, w, h, 0xFF000000);
        }
        // ---- warning broadcast
        warning(mc, gg, w, h, time, pt);
        boolean hudVisible = !mc.gui.hud.isHidden();
        if (hudVisible) {
            detector(mc, gg, w, h, time);
        }
        if (CinematicCamera.active() && CinematicCamera.age() < 100) {
            float a = Mth.clamp((100 - CinematicCamera.age()) / 30F, 0, 1);
            Component hint = Component.translatable("terrawrath.cinematic.hint");
            gg.centeredText(mc.font, hint, w / 2, h - 30, Mesh2D.argb(a, 0xF4EEE6));
        }
        // ---- supernova white-out (above everything)
        float white = ClientFX.whiteout(pt);
        if (white > 0.002F) {
            gg.fill(0, 0, w, h, Mesh2D.argb(white, 0xFFFFFF));
        }
    }

    private static DisasterEntity nearestWarning(Minecraft mc) {
        DisasterEntity best = null;
        double bestD = Double.MAX_VALUE;
        Vec3 p = mc.player.position();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof DisasterEntity d && d.phase() == DisasterEntity.WARNING) {
                double dd = d.distanceToSqr(p);
                if (dd < bestD && dd < 480 * 480) {
                    bestD = dd;
                    best = d;
                }
            }
        }
        return best;
    }

    private static DisasterEntity nearestHorizon(Minecraft mc, double range) {
        DisasterEntity best = null;
        double bestD = range * range;
        Vec3 p = mc.player.position();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof DisasterEntity d && d.disasterType() == DisasterType.EVENT_HORIZON) {
                double dx = d.getX() - p.x, dz = d.getZ() - p.z;
                double dd = dx * dx + dz * dz;
                if (dd < bestD) {
                    bestD = dd;
                    best = d;
                }
            }
        }
        return best;
    }

    private static void warning(Minecraft mc, GuiGraphicsExtractor gg, int w, int h, float time, float pt) {
        DisasterEntity d = nearestWarning(mc);
        if (d != null) {
            DisasterType type = d.disasterType();
            float shown = d.tickCount + pt;
            float alpha = bannerAlpha(shown, d.timer() - pt, 1e9F);
            if (alpha > 0.02F) {
                banner(mc, gg, w, h, time, Component.translatable(type.alertKey()), Component.translatable(type.warnKey()), alpha, false);
            }
            return;
        }
        // the singularity caving in
        DisasterEntity hole = nearestHorizon(mc, 600);
        if (hole != null && hole.phase() == DisasterEntity.ACTIVE) {
            DisasterClientState cs = DisasterClientState.of(hole);
            if (cs.collapseAge >= 0) {
                float shown = cs.age - cs.collapseAge + pt;
                float alpha = bannerAlpha(shown, 1e9F, 170 - shown);
                if (alpha > 0.02F) {
                    banner(mc, gg, w, h, time, Component.translatable("disaster.terrawrath.horizon.collapse"),
                        Component.translatable("disaster.terrawrath.horizon.collapse.sub"), alpha, true);
                }
            }
        }
    }

    /** Emergency-broadcast flicker on, then fade out near the end. */
    private static float bannerAlpha(float shown, float ticksLeft, float remaining) {
        if (shown < 14 && ((int) shown % 3 == 1)) {
            return 0;
        }
        float alpha = Mth.clamp(shown / 8F, 0, 1);
        alpha *= Mth.clamp(ticksLeft / 20F, 0, 1);
        alpha *= Mth.clamp(remaining / 25F, 0, 1);
        return alpha;
    }

    /**
     * The full-width EMERGENCY ALERT SYSTEM banner: crawling red/black stripes top and bottom, a small
     * caption, a huge outlined title and the advice line underneath.
     */
    private static void banner(Minecraft mc, GuiGraphicsExtractor gg, int w, int h, float time, Component title, Component sub,
                               float alpha, boolean critical) {
        Font font = mc.font;
        int tw = Math.max(1, font.width(title));
        float s = Math.min(w * 0.88F / tw, h * 0.0118F);
        float cap = Math.max(0.85F, s * 0.36F);
        float subS = Math.min(s * 0.4F, w * 0.92F / Math.max(1, font.width(sub)));
        subS = Math.max(0.75F, subS);
        int stripeH = Math.max(4, Math.round(s * 2.1F));
        float pad = s * 2.4F;
        float top = h * 0.07F;
        float y = top + stripeH + pad;
        float capY = y;
        y += 8 * cap + s * 1.8F;
        float titleY = y;
        y += 8 * s + s * 2.4F;
        float subY = y;
        y += 8 * subS + pad;
        int bandTop = (int) top;
        int bandBot = (int) y + stripeH;
        gg.fill(0, bandTop, w, bandBot, Mesh2D.argb(alpha * (critical ? 0.9F : 0.84F), critical ? 0x0A0607 : 0x1E2534));
        ControlPanelScreen.stripes(gg, 0, bandTop, w, stripeH, time * 0.9F, Mesh2D.argb(alpha, 0xFFFFFF));
        ControlPanelScreen.stripes(gg, 0, bandBot - stripeH, w, stripeH, time * 0.9F + 23, Mesh2D.argb(alpha, 0xFFFFFF));
        Component caption = Component.translatable("terrawrath.alert.system");
        int capColor = critical ? 0xE8342C : 0xFF9A2A;
        centered(gg, font, caption, w / 2F, capY, cap, Mesh2D.argb(alpha, capColor), true);
        float jitter = critical ? (float) Math.sin(time * 2.7F) * 0.5F : 0;
        outlined(gg, font, title, w / 2F + jitter, titleY, s, alpha, 0xF8EEDC, 0xC8202A, 0x2E2A36);
        centered(gg, font, sub, w / 2F, subY, subS, Mesh2D.argb(alpha, 0xF2B33A), true);
    }

    private static void centered(GuiGraphicsExtractor gg, Font font, Component text, float cx, float y, float scale, int color, boolean shadow) {
        gg.pose().pushMatrix();
        gg.pose().translate(cx, y);
        gg.pose().scale(scale, scale);
        gg.text(font, text, -font.width(text) / 2, 0, color, shadow);
        gg.pose().popMatrix();
    }

    /** Big title: soft drop shadow, thin red outline, cream fill. */
    private static void outlined(GuiGraphicsExtractor gg, Font font, Component text, float cx, float y, float scale, float alpha,
                                 int fill, int outline, int shadow) {
        float x = -font.width(text) / 2F;
        gg.pose().pushMatrix();
        gg.pose().translate(cx, y);
        gg.pose().scale(scale, scale);
        at(gg, font, text, x + 0.8F, 0.8F, Mesh2D.argb(alpha * 0.8F, shadow));
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.PI / 4;
            at(gg, font, text, x + Mth.cos(a) * 0.32F, Mth.sin(a) * 0.32F, Mesh2D.argb(alpha, outline));
        }
        at(gg, font, text, x, 0, Mesh2D.argb(alpha, fill));
        gg.pose().popMatrix();
    }

    private static void at(GuiGraphicsExtractor gg, Font font, Component text, float x, float y, int color) {
        gg.pose().pushMatrix();
        gg.pose().translate(x, y);
        gg.text(font, text, 0, 0, color, false);
        gg.pose().popMatrix();
    }

    // ------------------------------------------------------------------ event horizon screen effects

    private static float irisX = -1, irisY = -1;

    /**
     * The dark closes in: first from the sides of the screen, then as a shrinking circle until only the
     * anomaly is left glowing in the black. When the singularity ignites the darkness pulls back to a heavy
     * vignette over a night-dark world.
     */
    private static void horizonDarkness(Minecraft mc, GuiGraphicsExtractor gg, int w, int h, float pt) {
        DisasterEntity hole = nearestHorizon(mc, 700);
        if (hole == null || hole.phase() == DisasterEntity.ENDING) {
            irisX = -1;
            return;
        }
        DisasterClientState cs = DisasterClientState.of(hole);
        float p = cs.initialised ? cs.progress(pt) : hole.progress();
        float side, close, dark;
        if (hole.phase() == DisasterEntity.WARNING) {
            side = smooth(0.0F, 0.2F, p);
            close = smooth(0.12F, 0.62F, p);
            dark = 0.96F;
        } else {
            float open = smooth(0, 45, cs.phaseAge + pt);
            side = Mth.lerp(open, 1.0F, 0.42F);
            close = 1 - open;
            dark = Mth.lerp(open, 0.96F, 0.6F);
        }
        double dx = hole.getX() - mc.player.getX(), dz = hole.getZ() - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        dark *= (float) Mth.clamp(1 - (dist - 450) / 250, 0, 1);
        if (dark <= 0.01F) {
            return;
        }
        int tint = Mesh2D.argb(dark, 0xFFFFFF);
        if (side > 0.01F) {
            int sideW = (int) (w * 0.55F * side);
            gg.blit(RenderPipelines.GUI_TEXTURED, EDGE_L, 0, 0, 0, 0, sideW, h, 128, 4, 128, 4, tint);
            gg.blit(RenderPipelines.GUI_TEXTURED, EDGE_R, w - sideW, 0, 0, 0, sideW, h, 128, 4, 128, 4, tint);
        }
        if (close > 0.01F) {
            float[] scr = project(mc, hole.position(), w, h);
            float tx = scr != null ? scr[0] : w / 2F, ty = scr != null ? scr[1] : h * 0.4F;
            if (irisX < 0) {
                irisX = tx;
                irisY = ty;
            } else {
                irisX += (tx - irisX) * 0.2F;
                irisY += (ty - irisY) * 0.2F;
            }
            float maxR = (float) Math.hypot(w, h) * 1.25F / 0.62F;
            float minR = h * 0.11F / 0.62F;
            float r = Mth.lerp((float) Math.pow(close, 0.85), maxR, minR);
            int x0 = (int) (irisX - r), y0 = (int) (irisY - r), x1 = (int) (irisX + r), y1 = (int) (irisY + r);
            gg.blit(RenderPipelines.GUI_TEXTURED, IRIS, x0, y0, 0, 0, x1 - x0, y1 - y0, 256, 256, 256, 256, tint);
            int solid = Mesh2D.argb(dark, 0x050309);
            if (y0 > 0) {
                gg.fill(0, 0, w, Math.min(h, y0), solid);
            }
            if (y1 < h) {
                gg.fill(0, Math.max(0, y1), w, h, solid);
            }
            if (x0 > 0) {
                gg.fill(0, Math.max(0, y0), Math.min(w, x0), Math.min(h, y1), solid);
            }
            if (x1 < w) {
                gg.fill(Math.max(0, x1), Math.max(0, y0), w, Math.min(h, y1), solid);
            }
        }
    }

    /** Time bending near the core: violet wash, heavy vignette and slow ripples rolling outward. */
    private static void dilationOverlay(GuiGraphicsExtractor gg, int w, int h, float pt) {
        float dil = ClientFX.dilation(pt);
        if (dil <= 0.01F) {
            return;
        }
        float t = System.currentTimeMillis() % 1_000_000L / 1000F;
        gg.fill(0, 0, w, h, Mesh2D.argb(0.16F * dil, 0x5A2AA0));
        gg.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0, 0, w, h, 256, 144, 256, 144, Mesh2D.argb(0.75F * dil, 0x1A0830));
        for (int i = 0; i < 3; i++) {
            float ph = (t * 0.22F + i / 3F) % 1F;
            float size = h * (0.25F + ph * 1.6F);
            float a = 0.22F * dil * Mth.sin(ph * Mth.PI);
            int x0 = (int) (w / 2F - size), y0 = (int) (h / 2F - size);
            gg.blit(RenderPipelines.GUI_TEXTURED, RIPPLE, x0, y0, 0, 0, (int) (size * 2), (int) (size * 2), 256, 256, 256, 256, Mesh2D.argb(a, 0xD8C0FF));
        }
    }

    /** Projects a world point to GUI coordinates, or null when it is behind the camera / far off screen. */
    private static float[] project(Minecraft mc, Vec3 world, int w, int h) {
        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 cp = camera.position();
        Vector3fc f = camera.forwardVector(), u = camera.upVector(), l = camera.leftVector();
        double dx = world.x - cp.x, dy = world.y - cp.y, dz = world.z - cp.z;
        double z = dx * f.x() + dy * f.y() + dz * f.z();
        if (z < 1) {
            return null;
        }
        double x = -(dx * l.x() + dy * l.y() + dz * l.z());
        double y = dx * u.x() + dy * u.y() + dz * u.z();
        double tanHalf = Math.tan(Math.toRadians(Math.max(10, camera.getFov())) / 2);
        double aspect = w / (double) h;
        double sx = w / 2.0 * (1 + x / (z * tanHalf * aspect));
        double sy = h / 2.0 * (1 - y / (z * tanHalf));
        if (sx < -w * 0.25 || sx > w * 1.25 || sy < -h * 0.25 || sy > h * 1.25) {
            return null;
        }
        return new float[]{(float) sx, (float) sy};
    }

    static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static void detector(Minecraft mc, GuiGraphicsExtractor gg, int w, int h, float time) {
        if (!mc.player.getInventory().contains(s -> s.is(TWRegistry.DETECTOR.get()))) {
            return;
        }
        DisasterEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof DisasterEntity d) {
                double dd = d.distanceToSqr(mc.player);
                if (dd < bestD) {
                    bestD = dd;
                    best = d;
                }
            }
        }
        Font font = mc.font;
        int x = 6, y = (int) (h * 0.56F);
        gg.fill(x, y, x + 144, y + 34, 0xB0141216);
        gg.fill(x, y, x + 144, y + 1, 0xFFFF5A1F);
        boolean blink = (int) (time / 10) % 2 == 0;
        gg.text(font, Component.translatable("item.terrawrath.disaster_detector"), x + 6, y + 4, 0xFFFFC23D, false);
        if (best == null) {
            gg.text(font, Component.translatable("terrawrath.detector.hud_calm"), x + 6, y + 18, 0xFF7CF0A0, false);
            return;
        }
        Vec3 off = best.position().subtract(mc.player.position());
        int dist = (int) Math.sqrt(off.x * off.x + off.z * off.z);
        String dir = DisasterDetectorItem.compass(off.x, off.z);
        Component line = Component.translatable(best.disasterType().nameKey()).append(" " + dist + "m " + dir);
        gg.text(font, line, x + 6, y + 16, 0xFFF4EEE6, false);
        String status;
        int col;
        if (best.phase() == DisasterEntity.WARNING) {
            int s = (best.timer() + 19) / 20;
            status = "⚠ " + Component.translatable("terrawrath.detector.hud_incoming").getString() + " " + s + "s";
            col = blink ? 0xFFFF3A2A : 0xFFFFC23D;
        } else if (best.phase() == DisasterEntity.ACTIVE) {
            status = Component.translatable("terrawrath.detector.active").getString();
            col = 0xFFFF8A3A;
        } else {
            status = Component.translatable("terrawrath.detector.hud_fading").getString();
            col = 0xFF9AA0A8;
        }
        gg.text(font, Component.literal(status), x + 6, y + 25, col, false);
    }

    /** Tiny colour helpers for 2D drawing. */
    public static final class Mesh2D {
        private Mesh2D() {}

        public static int argb(float a, int rgb) {
            return Mth.clamp((int) (a * 255), 0, 255) << 24 | rgb & 0xFFFFFF;
        }

        public static int mix(int a, int b, float t) {
            t = Mth.clamp(t, 0, 1);
            int r = (int) Mth.lerp(t, a >> 16 & 255, b >> 16 & 255);
            int g = (int) Mth.lerp(t, a >> 8 & 255, b >> 8 & 255);
            int bl = (int) Mth.lerp(t, a & 255, b & 255);
            return r << 16 | g << 8 | bl;
        }
    }
}
