package com.terrawrath.client;

import com.terrawrath.disaster.DisasterType;
import com.terrawrath.entity.CameraEntity;
import com.terrawrath.entity.DisasterEntity;
import com.terrawrath.registry.TWRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Film mode: hides the HUD and slowly orbits the camera around the nearest disaster.
 * Toggle from the Control Panel; sneaking also exits.
 */
public final class CinematicCamera {
    private static boolean active;
    private static CameraEntity rig;
    private static DisasterEntity target;
    private static double angle;
    private static double sweepPhase;
    private static boolean hidHud;
    private static int age;
    private static Vec3 smoothPos;
    private static Vec3 smoothFocus;
    private static Vec3 camVel = Vec3.ZERO;
    public static boolean letterbox;

    private CinematicCamera() {}

    public static boolean active() {
        return active;
    }

    public static int age() {
        return age;
    }

    public static void toggle() {
        if (active) {
            stop();
        } else {
            start();
        }
    }

    public static void start() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        target = findTarget();
        rig = new CameraEntity(TWRegistry.CAMERA.get(), mc.level);
        Vec3 focus = focus();
        Vec3 p = mc.player.getEyePosition();
        angle = Math.atan2(p.z - focus.z, p.x - focus.x);
        sweepPhase = 0;
        smoothFocus = focus;
        smoothPos = desired(focus);
        camVel = Vec3.ZERO;
        float[] rot = lookAt(smoothPos, smoothFocus);
        rig.snapCamera(smoothPos.x, smoothPos.y, smoothPos.z, rot[0], rot[1]);
        mc.setCameraEntity(rig);
        if (!mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
            hidHud = true;
        }
        active = true;
        age = 0;
    }

    public static void stop() {
        Minecraft mc = Minecraft.getInstance();
        active = false;
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
        }
        if (hidHud && mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
        }
        hidHud = false;
        rig = null;
        target = null;
    }

    private static DisasterEntity findTarget() {
        Minecraft mc = Minecraft.getInstance();
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
        return best;
    }

    /** Orbit radius, height above focus and orbit speed tuned per disaster. */
    private static double[] framing() {
        if (target == null) {
            return new double[]{18, 6, 0.006};
        }
        DisasterType t = target.disasterType();
        float in = target.intensity();
        return switch (t) {
            case TORNADO -> new double[]{250 + 40 * in, 45, 0.0068};
            case VOLCANO -> new double[]{320 + 40 * in, 80, 0.0055};
            case TSUNAMI -> new double[]{210, 30, 0.006};
            case EARTHQUAKE -> new double[]{72, 48, 0.01};
            case METEOR_SHOWER -> new double[]{170, 60, 0.0065};
            case SUPERSTORM -> new double[]{130, 35, 0.0065};
            case BLIZZARD -> new double[]{Math.max(90, target.a() + 95), 45, 0.0055};
            case SINKHOLE -> new double[]{90 + 20 * in, 70, 0.0095};
            case EVENT_HORIZON -> new double[]{230 + 30 * in, -18, 0.0065};
        };
    }

    private static Vec3 focus() {
        Minecraft mc = Minecraft.getInstance();
        if (target == null) {
            return mc.player.getEyePosition();
        }
        DisasterType t = target.disasterType();
        Vec3 p = target.position();
        return switch (t) {
            case TORNADO -> p.add(0, target.a() * 0.42, 0);
            case VOLCANO -> p.add(0, -target.a() * 0.25 + 15, 0);
            case TSUNAMI -> {
                float ang = target.b();
                yield p.add(Math.cos(ang) * target.a(), target.c() * 0.45, Math.sin(ang) * target.a());
            }
            case SUPERSTORM -> p.add(0, 18, 0);
            case EVENT_HORIZON -> p;
            case SINKHOLE -> p.add(0, -target.c() * 0.4, 0);
            default -> p.add(0, 8, 0);
        };
    }

    /** Keeps the camera inside the chunks loaded around the player: full orbit when close, a sweeping arc when far. */
    private static double[] orbitLimits() {
        Minecraft mc = Minecraft.getInstance();
        double[] f = framing();
        double renderBlocks = mc.options.getEffectiveRenderDistance() * 16;
        // terrain is only drawn within the render distance of the camera: keep the disaster inside it
        double radius = Math.min(f[0], Math.max(40, renderBlocks * 0.8));
        double maxDist = Math.max(48, renderBlocks - 20);
        Vec3 c = target != null ? target.position() : mc.player.position();
        double dx = mc.player.getX() - c.x, dz = mc.player.getZ() - c.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (radius - d > maxDist) {
            radius = d + maxDist;
        } else if (d - radius > maxDist) {
            radius = d - maxDist;
        }
        double cosA = (d * d + radius * radius - maxDist * maxDist) / Math.max(1e-3, 2 * d * radius);
        double amplitude = d < 8 ? Math.PI : Math.acos(Mth.clamp(cosA, -1, 1));
        return new double[]{radius, Math.atan2(dz, dx), Math.min(Math.PI, amplitude)};
    }

    private static void advanceAngle(double speed) {
        double[] lim = orbitLimits();
        if (lim[2] > Math.toRadians(170)) {
            angle += speed;
        } else {
            double amp = Math.min(lim[2], Math.toRadians(70));
            sweepPhase += speed / Math.max(0.2, amp);
            angle = lim[1] + Math.sin(sweepPhase) * amp;
        }
    }

    private static Vec3 desired(Vec3 focus) {
        Minecraft mc = Minecraft.getInstance();
        double[] f = framing();
        double radius = orbitLimits()[0];
        double x = focus.x + Math.cos(angle) * radius;
        double z = focus.z + Math.sin(angle) * radius;
        double y = focus.y + f[1];
        if (mc.level != null) {
            int ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
            y = Math.max(y, ground + (target != null && target.disasterType() == DisasterType.EVENT_HORIZON ? 45 : 4));
        }
        return new Vec3(x, y, z);
    }

    private static float[] lookAt(Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(dy, h) * Mth.RAD_TO_DEG);
        return new float[]{yaw, pitch};
    }

    public static void tick() {
        if (!active) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || rig == null || rig.level() != mc.level) {
            stop();
            return;
        }
        age++;
        if (mc.player.isShiftKeyDown() && age > 10) {
            stop();
            return;
        }
        if (target == null || target.isRemoved()) {
            DisasterEntity next = findTarget();
            if (next != null) {
                target = next;
            } else if (target != null && target.isRemoved()) {
                target = null;
            }
        }
        if (mc.getCameraEntity() != rig) {
            mc.setCameraEntity(rig);
        }
        double[] f = framing();
        advanceAngle(f[2]);
        Vec3 focus = focus();
        smoothFocus = smoothFocus.lerp(focus, 0.05);
        Vec3 want = desired(smoothFocus);
        // critically damped follow: fast enough for the wide orbit, no overshoot or jitter
        Vec3 accel = want.subtract(smoothPos).scale(0.012).subtract(camVel.scale(0.2));
        camVel = camVel.add(accel);
        smoothPos = smoothPos.add(camVel);
        float[] rot = lookAt(smoothPos, smoothFocus);
        float yaw = rig.getYRot() + Mth.wrapDegrees(rot[0] - rig.getYRot());
        rig.moveCamera(smoothPos.x, smoothPos.y, smoothPos.z, yaw, rot[1]);
    }
}
