package com.terrawrath.client;

import com.terrawrath.client.sound.DisasterLoopSound;
import com.terrawrath.entity.DisasterEntity;
import net.minecraft.util.Mth;

/**
 * Per-disaster client scratch data: previous values for smooth interpolation, one-shot event tracking
 * and the looping sound handles.
 */
public final class DisasterClientState {
    public float pa, pb, pc, pd, a, b, c, d;
    public float pRadius, radius;
    public float pProgress, progress;
    public int lastEvent = Integer.MIN_VALUE;
    public boolean initialised;
    public DisasterLoopSound loop;
    public DisasterLoopSound loop2;
    public int age;
    /** Phase seen last tick, ticks spent in the current phase and the age the horizon began collapsing. */
    public int lastPhase = -1;
    public int phaseAge;
    public int collapseAge = -1;
    public boolean whiteFired;

    public static DisasterClientState of(DisasterEntity e) {
        if (e.clientState instanceof DisasterClientState s) {
            return s;
        }
        DisasterClientState s = new DisasterClientState();
        e.clientState = s;
        return s;
    }

    public void update(DisasterEntity e) {
        if (!initialised) {
            a = pa = e.a();
            b = pb = e.b();
            c = pc = e.c();
            d = pd = e.d();
            radius = pRadius = e.radius();
            progress = pProgress = e.progress();
            initialised = true;
        } else {
            pa = a;
            pb = b;
            pc = c;
            pd = d;
            pRadius = radius;
            pProgress = progress;
            a = e.a();
            b = e.b();
            c = e.c();
            d = e.d();
            radius = e.radius();
            progress = e.progress();
        }
        age++;
        if (e.phase() != lastPhase) {
            lastPhase = e.phase();
            phaseAge = 0;
        } else {
            phaseAge++;
        }
        if (collapseAge < 0 && e.phase() == DisasterEntity.ACTIVE && e.b() > 0.001F) {
            collapseAge = age;
        }
    }

    public float a(float pt) { return Mth.lerp(pt, pa, a); }
    public float b(float pt) { return Mth.lerp(pt, pb, b); }
    public float c(float pt) { return Mth.lerp(pt, pc, c); }
    public float d(float pt) { return Mth.lerp(pt, pd, d); }
    public float radius(float pt) { return Mth.lerp(pt, pRadius, radius); }
    public float progress(float pt) { return Mth.lerp(pt, pProgress, progress); }

    /** 0..1 strength of the disaster's presence (fades in through the warning, out at the end). */
    public static float presence(int phase, float progress) {
        return switch (phase) {
            case DisasterEntity.WARNING -> Mth.clamp(progress * 1.3F, 0, 1);
            case DisasterEntity.ACTIVE -> 1.0F;
            default -> Mth.clamp(1 - progress, 0, 1);
        };
    }
}
