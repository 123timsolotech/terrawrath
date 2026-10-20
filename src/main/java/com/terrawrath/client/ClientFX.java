package com.terrawrath.client;

import com.terrawrath.config.TWConfig;
import com.terrawrath.disaster.DisasterType;
import com.terrawrath.entity.DisasterEntity;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

/**
 * The client "atmosphere director": blends sky, fog and light toward each nearby disaster's mood, and owns
 * camera shake, impact punches, screen flashes and screen-edge effects.
 */
public final class ClientFX {
    /** Mood per disaster: sky, fog, sky-light tint, how dark the day gets, fog distance and star visibility. */
    public record Mood(int sky, int fog, int lightTint, float lightFactor, float fogEnd, float stars, int cloud, float range, float inner) {}

    public static Mood mood(DisasterType type, DisasterEntity e) {
        return switch (type) {
            case TORNADO -> new Mood(0x5A645C, 0x6E776B, 0xB8C2B0, 0.55F, 430, 0.0F, 0x464A46, 340, 140);
            case VOLCANO -> new Mood(0x8A3A14, 0xA84A1C, 0xFF9A5A, 0.5F, 560, 0.1F, 0x2A1A14, 480, 240);
            case TSUNAMI -> new Mood(0x5A7486, 0x7890A0, 0xA8C0D8, 0.66F, 420, 0.0F, 0x5A6872, 420, 240);
            case EARTHQUAKE -> new Mood(0x8A7558, 0x9A8466, 0xFFE0B8, 0.78F, 190, 0.0F, 0x8A7A66, 220, 90);
            case METEOR_SHOWER -> e != null && e.a() > 0.5F
                ? new Mood(0x5A1508, 0x6A2010, 0xFF8A50, 0.45F, 600, 0.5F, 0x4A1A10, 520, 260)
                : new Mood(0x2A6E4A, 0x3A7A58, 0xC0FFDC, 0.6F, 600, 0.7F, 0x2A5A44, 520, 260);
            case SUPERSTORM -> new Mood(0x3A2858, 0x4A3A66, 0xC8B4FF, 0.62F, 360, 0.0F, 0x1A1226, 360, 200);
            case BLIZZARD -> new Mood(0xDCE6EE, 0xE8F0F6, 0xD8E8FF, 0.85F, 24, 0.0F, 0xE0E8EE, 70, 0);
            case SINKHOLE -> new Mood(0x7A6A55, 0x8A7A62, 0xFFE6C8, 0.8F, 320, 0.0F, 0x7A6A5A, 140, 50);
            case EVENT_HORIZON -> new Mood(0x0E0518, 0x150820, 0xA890FF, 0.15F, 760, 1.0F, 0x140A24, 760, 420);
        };
    }

    // smoothed atmosphere
    private static final Vector3f sky = new Vector3f();
    private static final Vector3f fog = new Vector3f();
    private static final Vector3f lightTint = new Vector3f(1, 1, 1);
    private static final Vector3f cloud = new Vector3f();
    private static float weight, lightFactor = 1, fogEnd = 1000, stars, fogWeight;
    /** 1 = vanilla clouds visible, 0 = hidden (disasters with their own cloud decks hide them). */
    private static float cloudVisibility = 1;
    // camera
    private static float shake, prevShake;
    private static float punch, prevPunch;
    private static float flash, prevFlash;
    private static float lightning, prevLightning;
    private static int flashColor = 0xFFFFFF;
    // screen edges
    public static float frost, heat, dread;
    // event horizon: time dilation near the core and the supernova white-out
    private static float dilation, prevDilation;
    private static int whiteTicks = -1;
    private static int whiteArrive;
    private static float white, prevWhite;
    public static int dreadColor = 0x000000;
    private static long ticks;

    private ClientFX() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        prevShake = shake;
        prevPunch = punch;
        prevFlash = flash;
        prevLightning = lightning;
        prevDilation = dilation;
        prevCineFov = cineFov;
        cineFov += ((CinematicCamera.active() ? 20F : 0F) - cineFov) * 0.08F;
        prevWhite = white;
        tickWhiteout();
        punch *= 0.82F;
        flash *= 0.84F;
        lightning *= 0.62F;
        if (mc.level == null || mc.player == null) {
            restoreClouds(mc);
            weight = 0;
            shake = 0;
            dilation = 0;
            frost = heat = dread = 0;
            return;
        }
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        float wSum = 0, shakeSum = 0;
        float tSky0 = 0, tSky1 = 0, tSky2 = 0, tFog0 = 0, tFog1 = 0, tFog2 = 0, tL0 = 0, tL1 = 0, tL2 = 0, tC0 = 0, tC1 = 0, tC2 = 0;
        float tLight = 0, tFogEnd = 0, tStars = 0, fogW = 0;
        float frostT = 0, heatT = 0, dreadT = 0;
        float hideClouds = 0;
        float dilationT = 0;
        int dreadC = 0;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof DisasterEntity e)) {
                continue;
            }
            DisasterType type = e.disasterType();
            Mood m = mood(type, e);
            double dx = e.getX() - cam.x, dz = e.getZ() - cam.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            float presence = DisasterClientState.presence(e.phase(), e.progress());
            float inner = m.inner;
            if (type == DisasterType.BLIZZARD) {
                inner = Math.max(5, e.a());
                m = new Mood(m.sky(), m.fog(), m.lightTint(), m.lightFactor(), m.fogEnd(), m.stars(), m.cloud(), inner + 70, inner);
            } else if (type == DisasterType.TSUNAMI) {
                inner = Math.max(inner, Math.abs(e.a()) + 60);
            }
            float prox = (float) Mth.clamp(1 - (dist - inner) / Math.max(1, m.range - inner), 0, 1);
            float w = presence * prox;
            if (w <= 0.001F) {
                continue;
            }
            wSum += w;
            tSky0 += r(m.sky) * w; tSky1 += g(m.sky) * w; tSky2 += b(m.sky) * w;
            tFog0 += r(m.fog) * w; tFog1 += g(m.fog) * w; tFog2 += b(m.fog) * w;
            tL0 += r(m.lightTint) * w; tL1 += g(m.lightTint) * w; tL2 += b(m.lightTint) * w;
            tC0 += r(m.cloud) * w; tC1 += g(m.cloud) * w; tC2 += b(m.cloud) * w;
            tLight += m.lightFactor * w;
            if (type == DisasterType.TORNADO || type == DisasterType.SUPERSTORM || type == DisasterType.VOLCANO
                || type == DisasterType.EVENT_HORIZON || type == DisasterType.METEOR_SHOWER || type == DisasterType.BLIZZARD) {
                hideClouds = Math.max(hideClouds, Math.min(1, w * 1.5F));
            }
            tStars += m.stars * w;
            float fogInfluence = type == DisasterType.BLIZZARD
                ? (dist < e.a() ? presence : presence * (float) Mth.clamp(1 - (dist - e.a()) / 40, 0, 1))
                : w;
            tFogEnd += m.fogEnd * fogInfluence;
            fogW += fogInfluence;
            // shake falls off with distance from the source
            float reach = Math.max(30, e.radius() * 1.6F + 25);
            float s = e.shake() / (1 + (float) (dist * dist) / (reach * reach));
            shakeSum += s;
            if (type == DisasterType.BLIZZARD && dist < e.a()) {
                frostT = Math.max(frostT, presence * 0.75F);
            }
            if (type == DisasterType.EVENT_HORIZON) {
                double dist3 = Math.sqrt(dist * dist + (e.getY() - cam.y) * (e.getY() - cam.y));
                if (e.phase() == DisasterEntity.ACTIVE) {
                    float rh = Math.max(1, e.a());
                    double zone = Math.max(rh * 3.5, 50);
                    dilationT = Math.max(dilationT, (float) Mth.clamp(1 - (dist3 - rh) / (zone - rh), 0, 1));
                }
                DisasterClientState hs = DisasterClientState.of(e);
                if (e.phase() == DisasterEntity.ENDING && !hs.whiteFired) {
                    hs.whiteFired = true;
                    startWhiteout(dist3);
                }
            }
            if (type == DisasterType.VOLCANO) {
                heatT = Math.max(heatT, e.b() * (float) Mth.clamp(1 - dist / 160, 0, 1));
            }
            if (type == DisasterType.METEOR_SHOWER && e.a() > 0.5F) {
                heatT = Math.max(heatT, 0.45F);
            }
            if (e.phase() == DisasterEntity.WARNING) {
                float dw = presence * prox;
                if (dw > dreadT) {
                    dreadT = dw;
                    dreadC = type.color;
                }
            }
            // one-shot events from the server (eruptions, impacts, ignition)
            DisasterClientState cs = DisasterClientState.of(e);
            int ev = e.eventCounter();
            if (cs.lastEvent == Integer.MIN_VALUE) {
                cs.lastEvent = ev;
            } else if (ev != cs.lastEvent) {
                cs.lastEvent = ev;
                float near = (float) Mth.clamp(1 - dist / 260, 0.15, 1);
                addFlash(0.55F * near, type == DisasterType.EVENT_HORIZON ? 0xE8DCFF : type == DisasterType.VOLCANO ? 0xFFB070 : 0xFFFFFF);
                addPunch(0.9F * near);
            }
        }
        float target = Math.min(1, wSum);
        weight += (target - weight) * 0.04F;
        cloudVisibility += ((1 - hideClouds) - cloudVisibility) * 0.05F;
        shaderClouds(mc);
        if (wSum > 0.0001F) {
            lerp(sky, tSky0 / wSum, tSky1 / wSum, tSky2 / wSum, 0.06F);
            lerp(fog, tFog0 / wSum, tFog1 / wSum, tFog2 / wSum, 0.06F);
            lerp(lightTint, tL0 / wSum, tL1 / wSum, tL2 / wSum, 0.05F);
            lerp(cloud, tC0 / wSum, tC1 / wSum, tC2 / wSum, 0.05F);
            lightFactor += (Mth.lerp(target, 1, tLight / wSum) - lightFactor) * 0.04F;
            stars += (tStars / wSum * target - stars) * 0.04F;
        } else {
            lightFactor += (1 - lightFactor) * 0.04F;
            lerp(lightTint, 1, 1, 1, 0.04F);
            stars += (0 - stars) * 0.04F;
        }
        if (fogW > 0.0001F) {
            float fw = Math.min(1, fogW);
            fogWeight += (fw - fogWeight) * 0.05F;
            fogEnd += (tFogEnd / fogW - fogEnd) * 0.05F;
        } else {
            fogWeight += (0 - fogWeight) * 0.05F;
        }
        dilation += (dilationT - dilation) * 0.08F;
        float shakeTarget = shakeSum * (float) TWConfig.VALUES.screenShakeMultiplier;
        shake += (shakeTarget - shake) * 0.25F;
        float frozen = mc.player.getPercentFrozen();
        frost += (Math.max(frostT, frozen) - frost) * 0.05F;
        heat += (heatT - heat) * 0.05F;
        dread += (dreadT - dread) * 0.08F;
        if (dreadT > 0) {
            dreadColor = dreadC;
        }
    }

    // ------------------------------------------------------------------ clouds under shader packs

    private static CloudStatus savedClouds;

    /**
     * Shader packs draw vanilla clouds on top of translucent sky effects (and ignore the cloud colour
     * attribute that hides them in vanilla). While a disaster owns the sky, switch vanilla clouds off for the
     * session and put the player's setting back afterwards. The options file is never written.
     */
    private static void shaderClouds(Minecraft mc) {
        boolean hide = cloudVisibility < 0.5F && com.terrawrath.client.render.ShaderCompat.shaderPack();
        if (hide && savedClouds == null) {
            savedClouds = mc.options.cloudStatus().get();
            if (savedClouds != CloudStatus.OFF) {
                mc.options.cloudStatus().set(CloudStatus.OFF);
            }
        } else if (!hide) {
            restoreClouds(mc);
        }
    }

    private static void restoreClouds(Minecraft mc) {
        if (savedClouds != null) {
            if (mc.options.cloudStatus().get() == CloudStatus.OFF) {
                mc.options.cloudStatus().set(savedClouds);
            }
            savedClouds = null;
        }
    }

    private static void lerp(Vector3f v, float x, float y, float z, float f) {
        v.set(v.x + (x - v.x) * f, v.y + (y - v.y) * f, v.z + (z - v.z) * f);
    }

    private static float r(int c) { return (c >> 16 & 255) / 255F; }
    private static float g(int c) { return (c >> 8 & 255) / 255F; }
    private static float b(int c) { return (c & 255) / 255F; }

    // ------------------------------------------------------------------ events

    public static void addFlash(float amount, int color) {
        if (amount > flash * 0.6F) {
            flashColor = color;
        }
        flash = Math.min(1.2F, flash + amount);
    }

    /** Lightning lights up sky, clouds, fog and terrain for a moment (no screen wash). */
    public static void addLightning(float amount) {
        lightning = Math.min(1.0F, lightning + amount);
    }

    public static float lightning(float pt) {
        return Mth.clamp(Mth.lerp(pt, prevLightning, lightning), 0, 1);
    }

    public static void addPunch(float amount) {
        punch = Math.min(3.0F, punch + amount * (float) TWConfig.VALUES.screenShakeMultiplier);
    }

    public static float flash(float pt) {
        return Mth.clamp(Mth.lerp(pt, prevFlash, flash), 0, 1);
    }

    public static int flashColor() {
        return flashColor;
    }

    public static float weight() {
        return weight;
    }

    // ------------------------------------------------------------------ event horizon: time and the white-out

    /** 0..1: how deep the camera is inside the core's time-dilation zone. */
    public static float dilation(float pt) {
        return Mth.clamp(Mth.lerp(pt, prevDilation, dilation), 0, 1);
    }

    private static float cineFov, prevCineFov;

    /** Extra field of view: the cinematic drone shoots wide, and space-time stretches near the core. */
    public static float fovBoost(float pt) {
        float cine = Mth.lerp(pt, prevCineFov, cineFov);
        float dil = dilation(pt);
        float wobble = dil > 0.002F ? (12 + 7 * Mth.sin((ticks + pt) * 0.06F)) * dil : 0;
        return cine + wobble;
    }

    /** Sound pitch / animation factor while time is bent (1 = normal). */
    public static float timeScale() {
        return 1 - 0.45F * dilation;
    }

    /** The supernova shell reaches the camera, then the world stays white for five seconds. */
    private static void startWhiteout(double distance) {
        whiteTicks = 0;
        double k = Math.min(0.999, distance / 600.0);
        whiteArrive = Mth.clamp((int) Math.ceil(28 * (1 - Math.cbrt(1 - k))), 2, 28);
    }

    private static void tickWhiteout() {
        if (whiteTicks < 0) {
            white = 0;
            return;
        }
        whiteTicks++;
        int t = whiteTicks;
        int hold = 100;
        int fade = 50;
        if (t < whiteArrive) {
            float k = t / (float) whiteArrive;
            white = 0.45F * k * k;
        } else if (t < whiteArrive + 4) {
            white = Mth.lerp((t - whiteArrive) / 4F, 0.45F, 1.0F);
        } else if (t < whiteArrive + 4 + hold) {
            white = 1;
        } else if (t < whiteArrive + 4 + hold + fade) {
            float k = (t - whiteArrive - 4 - hold) / (float) fade;
            white = 1 - k * k * (3 - 2 * k);
        } else {
            white = 0;
            whiteTicks = -1;
        }
    }

    public static float whiteout(float pt) {
        return Mth.clamp(Mth.lerp(pt, prevWhite, white), 0, 1);
    }

    // ------------------------------------------------------------------ camera

    /** Returns {yaw, pitch, roll} offsets in degrees for this frame. */
    public static float[] cameraShake(float pt) {
        float s = Mth.lerp(pt, prevShake, shake);
        float p = Mth.lerp(pt, prevPunch, punch);
        float dil = dilation(pt);
        if (s < 0.002F && p < 0.002F && dil < 0.002F) {
            return null;
        }
        float time = (ticks + pt);
        // the cinematic drone is far away: keep its shot steady
        float amp = (s * 1.6F + p * 2.4F) * (CinematicCamera.active() ? 0.35F : 1.0F);
        float yaw = (float) (Math.sin(time * 1.9) * 0.6 + Math.sin(time * 3.7 + 1.3) * 0.4) * amp;
        float pitch = (float) (Math.sin(time * 2.3 + 0.7) * 0.6 + Math.sin(time * 4.9 + 2.1) * 0.4) * amp;
        float roll = (float) (Math.sin(time * 1.3 + 2.0) * 0.7 + Math.sin(time * 5.3) * 0.3) * amp * 0.8F;
        if (dil > 0.002F) {
            // spacetime bending near the core: a slow, heavy sway and roll
            yaw += (float) Math.sin(time * 0.031) * 3.0F * dil;
            pitch += (float) Math.sin(time * 0.037 + 1.0) * 2.2F * dil;
            roll += (float) Math.sin(time * 0.045 + 0.5) * 9.0F * dil;
        }
        return new float[]{yaw, pitch, roll};
    }

    // ------------------------------------------------------------------ fog

    public static void applyFog(FogData data) {
        if (fogWeight > 0.002F) {
            float end = Mth.lerp(fogWeight, data.environmentalEnd, Math.min(data.environmentalEnd, fogEnd));
            data.environmentalEnd = end;
            data.environmentalStart = Mth.lerp(fogWeight, data.environmentalStart, Math.min(data.environmentalStart, end * 0.05F));
            data.skyEnd = Mth.lerp(fogWeight, data.skyEnd, Math.min(data.skyEnd, Math.max(end * 2, 60)));
            data.cloudEnd = Mth.lerp(fogWeight, data.cloudEnd, Math.min(data.cloudEnd, Math.max(end * 2, 80)));
        }
        if (weight > 0.002F) {
            Vector4f c = data.color;
            float w = weight * 0.92F;
            float k = daylight(c.x, c.y, c.z) * fl();
            c.set(Mth.lerp(w, c.x, fog.x * k), Mth.lerp(w, c.y, fog.y * k), Mth.lerp(w, c.z, fog.z * k), c.w);
        }
    }

    /** Lightning brightens fog and sky momentarily. */
    private static float fl() {
        return 1 + flash * 1.2F + lightning * 2.2F;
    }

    // ------------------------------------------------------------------ environment attributes (sky, light, clouds)

    /** How bright the vanilla value is compared to a clear day (keeps nights dark under a tinted sky). */
    private static float daylight(float r, float g, float b) {
        return Mth.clamp(Math.max(r, Math.max(g, b)) / 0.75F, 0.1F, 1.0F);
    }

    public static Vector3fc skyColor(Vector3fc base) {
        if (weight <= 0.002F) {
            return base;
        }
        float w = weight * 0.95F;
        float k = daylight(base.x(), base.y(), base.z()) * fl();
        return new Vector3f(Mth.lerp(w, base.x(), sky.x * k), Mth.lerp(w, base.y(), sky.y * k), Mth.lerp(w, base.z(), sky.z * k));
    }

    public static Vector3fc fogColor(Vector3fc base) {
        if (weight <= 0.002F) {
            return base;
        }
        float w = weight * 0.92F;
        float k = daylight(base.x(), base.y(), base.z());
        return new Vector3f(Mth.lerp(w, base.x(), fog.x * k), Mth.lerp(w, base.y(), fog.y * k), Mth.lerp(w, base.z(), fog.z * k));
    }

    public static Vector4fc cloudColor(Vector4fc base) {
        if (weight <= 0.002F && cloudVisibility > 0.998F) {
            return base;
        }
        float w = weight * 0.9F;
        return new Vector4f(Mth.lerp(w, base.x(), cloud.x), Mth.lerp(w, base.y(), cloud.y), Mth.lerp(w, base.z(), cloud.z), base.w() * cloudVisibility);
    }

    public static Vector4fc sunriseColor(Vector4fc base) {
        if (weight <= 0.002F) {
            return base;
        }
        return new Vector4f(base.x(), base.y(), base.z(), base.w() * (1 - weight * 0.85F));
    }

    public static float skyLightFactor(float base) {
        float boost = Math.max(flash > 0.25F ? flash : 0, lightning * 0.9F);
        return Math.max(base * lightFactor, Math.min(1, boost));
    }

    public static Vector3fc skyLightColor(Vector3fc base) {
        if (weight <= 0.002F) {
            return base;
        }
        return new Vector3f(base.x() * lightTint.x, base.y() * lightTint.y, base.z() * lightTint.z);
    }

    public static float starBrightness(float base) {
        return Math.max(base, stars);
    }
}
