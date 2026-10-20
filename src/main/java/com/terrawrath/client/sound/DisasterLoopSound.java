package com.terrawrath.client.sound;

import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * A looping disaster sound that follows a moving source and grows louder as the camera approaches.
 */
public class DisasterLoopSound extends AbstractTickableSoundInstance {
    private final Entity owner;
    private final Supplier<Vec3> source;
    private final Supplier<Float> loudness;
    private final float range;
    private float current;

    /**
     * @param source   world position of the sound (e.g. funnel base, wave front)
     * @param loudness 0..1 target loudness at the source (phase/intensity driven)
     * @param range    blocks over which the sound fades to silence
     */
    private final float basePitch;

    public DisasterLoopSound(SoundEvent sound, Entity owner, Supplier<Vec3> source, Supplier<Float> loudness, float range, float pitch) {
        super(sound, SoundSource.WEATHER, RandomSource.create());
        this.owner = owner;
        this.source = source;
        this.loudness = loudness;
        this.range = range;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0001F;
        this.pitch = pitch;
        this.basePitch = pitch;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.relative = false;
        Vec3 p = source.get();
        this.x = p.x;
        this.y = p.y;
        this.z = p.z;
    }

    @Override
    public void tick() {
        if (owner.isRemoved()) {
            current *= 0.85F;
            this.volume = current;
            if (current < 0.01F) {
                this.stop();
            }
            return;
        }
        Vec3 p = source.get();
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        double d = cam.distanceTo(p);
        float falloff = (float) Math.pow(Mth.clamp(1 - d / range, 0, 1), 1.6);
        float target = Mth.clamp(loudness.get() * (0.08F + 0.92F * falloff), 0, 1.6F);
        current += (target - current) * 0.08F;
        this.volume = Math.max(0.0001F, current);
        // time bends near the singularity: every loop drops in pitch
        this.pitch = basePitch * com.terrawrath.client.ClientFX.timeScale();
        // keep the emitter near the camera along the true direction so panning stays right while it is loud
        Vec3 dir = p.subtract(cam);
        double len = dir.length();
        if (len > 12) {
            dir = dir.scale(12 / len);
        }
        this.x = cam.x + dir.x;
        this.y = cam.y + dir.y;
        this.z = cam.z + dir.z;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    public void kill() {
        this.stop();
    }
}
