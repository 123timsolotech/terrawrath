package com.terrawrath.disaster.impl;

import com.terrawrath.disaster.BlockQueue;
import com.terrawrath.disaster.Disaster;
import com.terrawrath.disaster.DisasterType;
import com.terrawrath.disaster.Terrain;
import com.terrawrath.entity.DebrisEntity;
import com.terrawrath.entity.ShockwaveEntity;
import com.terrawrath.registry.TWRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * "Faultline" — glowing cracks race across the land and tear open into deep chasms.
 */
public class EarthquakeDisaster extends Disaster {
    private static final class Crack {
        final List<double[]> path = new ArrayList<>(); // x, z, direction
        final double widthScale;
        final double depthScale;
        int front;
        int opened;

        Crack(double widthScale, double depthScale) {
            this.widthScale = widthScale;
            this.depthScale = depthScale;
        }
    }

    private final List<Crack> cracks = new ArrayList<>();
    private final float mainShockShare = 0.55F;
    private double crackSpeed;
    private double crackProgress;
    private int nextAftershock;
    private int pulse;

    public EarthquakeDisaster(ServerLevel level, Vec3 center, float intensity, int warnTicks, boolean natural) {
        super(level, DisasterType.EARTHQUAKE, center, intensity, warnTicks, natural);
        int count = 3 + Math.round(2 * intensity);
        double base = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            double dir = base + i * Math.PI * 2 / count + rand(-0.4, 0.4);
            Crack c = new Crack(1.0, 1.0);
            trace(c, center.x + Math.cos(dir) * 2, center.z + Math.sin(dir) * 2, dir, (int) (55 + 45 * intensity));
            cracks.add(c);
            // a branch or two off each main crack
            if (c.path.size() > 30) {
                int at = 12 + random.nextInt(c.path.size() / 2);
                double[] p = c.path.get(at);
                Crack b = new Crack(0.55, 0.6);
                trace(b, p[0], p[1], p[2] + (random.nextBoolean() ? 0.8 : -0.8), (int) (18 + 16 * intensity));
                b.front = -at; // branch starts when the main crack has passed
                cracks.add(b);
            }
        }
        this.crackSpeed = 1.5 + 0.4 * intensity;
    }

    private void trace(Crack c, double x, double z, double dir, int length) {
        double turn = 0;
        for (int i = 0; i < length; i++) {
            turn += rand(-0.12, 0.12);
            turn = Mth.clamp(turn, -0.25, 0.25);
            dir += turn * 0.5;
            x += Math.cos(dir);
            z += Math.sin(dir);
            c.path.add(new double[]{x, z, dir});
        }
    }

    @Override
    protected double alertRadius() {
        return 260;
    }

    @Override
    protected void onStart() {
        anchor.setRadius(60 + 40 * intensity);
        anchor.setShake(0.1F);
    }

    @Override
    protected void tickWarning() {
        float p = phaseTick / (float) warnTicks;
        // foreshocks
        float tremor = 0.08F + 0.3F * p + (phaseTick % 50 < 8 ? 0.35F * p : 0);
        anchor.setShake(tremor);
        if (phaseTick % 8 == 0) {
            double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * 40;
            int x = Mth.floor(center.x + Math.cos(a) * r), z = Mth.floor(center.z + Math.sin(a) * r);
            particles(TWRegistry.P_DUST.get(), new Vec3(x, Terrain.top(level, x, z) + 1, z), 5, 1.5, 0.04);
        }
    }

    @Override
    protected void onActivate() {
        anchor.fireEvent();
        sound(center, TWRegistry.IMPACT.get(), 10.0F, 0.5F);
        sound(center, TWRegistry.COLLAPSE.get(), 8.0F, 0.7F);
        ShockwaveEntity.spawn(level, center, 55, 0.7F, 0xFF8A3A, 40);
    }

    @Override
    protected void tickActive() {
        float p = phaseTick / (float) activeTicks;
        boolean main = p < mainShockShare;
        if (main) {
            anchor.setShake(0.95F + 0.35F * intensity);
            crackProgress += crackSpeed;
        } else {
            if (phaseTick >= nextAftershock) {
                nextAftershock = phaseTick + 90 + random.nextInt(90);
                pulse = 25;
                anchor.fireEvent();
                sound(center, TWRegistry.CRACK.get(), 8.0F, 0.6F);
                crackProgress += 12;
            }
            float base = 0.25F * (1 - (p - mainShockShare) / (1 - mainShockShare));
            anchor.setShake(base + (pulse > 0 ? 0.75F * pulse / 25F : 0));
            if (pulse > 0) {
                pulse--;
                crackProgress += 0.6;
            }
        }
        if (terrain()) {
            advanceCracks();
            if (main || pulse > 0) {
                collapseStructures(main ? 26 : 12);
            }
        }
        jolt(main ? 1.0 : pulse > 0 ? 0.6 : 0.15);
    }

    @Override
    protected void tickEnding() {
        anchor.setShake(0.12F * (1 - phaseTick / (float) endTicks));
        if (terrain()) {
            advanceCracks();
        }
    }

    private void advanceCracks() {
        int target = (int) crackProgress;
        for (Crack c : cracks) {
            while (c.front < Math.min(target, c.path.size())) {
                if (c.front >= 0) {
                    glow(c, c.front);
                }
                c.front++;
            }
            // chasm opens a little behind the glowing tip
            while (c.opened < c.front - 10) {
                if (c.opened >= 0) {
                    open(c, c.opened);
                }
                c.opened++;
            }
        }
    }

    private void glow(Crack c, int i) {
        double[] pt = c.path.get(i);
        int x = Mth.floor(pt[0]), z = Mth.floor(pt[1]);
        if (!Terrain.loaded(level, x, z)) {
            return;
        }
        int y = Terrain.ground(level, x, z);
        blocks.set(new BlockPos(x, y, z), Blocks.MAGMA_BLOCK.defaultBlockState(), BlockQueue.FAST);
        blocks.set(new BlockPos(x, y - 1, z), Blocks.MAGMA_BLOCK.defaultBlockState(), BlockQueue.FAST);
        if (i % 3 == 0) {
            particles(TWRegistry.P_DUST.get(), new Vec3(x + 0.5, y + 1.2, z + 0.5), 10, 1.2, 0.12);
            particles(TWRegistry.P_EMBER.get(), new Vec3(x + 0.5, y + 1.0, z + 0.5), 4, 0.4, 0.08);
        }
        if (i % 9 == 0) {
            sound(new Vec3(x, y, z), TWRegistry.CRACK.get(), 4.0F, 0.8F + random.nextFloat() * 0.4F);
        }
    }

    private void open(Crack c, int i) {
        double[] pt = c.path.get(i);
        double t = i / (double) c.path.size();
        double halfWidth = (1.2 + 2.6 * intensity) * Math.sqrt(1 - t) * c.widthScale + 0.6;
        int depth = (int) ((14 + 22 * intensity) * (1 - t * 0.6) * c.depthScale) + random.nextInt(4);
        double nx = -Math.sin(pt[2]), nz = Math.cos(pt[2]);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (double w = -halfWidth; w <= halfWidth; w += 0.7) {
            int x = Mth.floor(pt[0] + nx * w), z = Mth.floor(pt[1] + nz * w);
            if (!Terrain.loaded(level, x, z)) {
                continue;
            }
            int top = Terrain.top(level, x, z);
            int ground = Terrain.ground(level, x, z);
            double edge = Math.abs(w) / halfWidth;
            int d = (int) (depth * (1 - edge * edge * 0.6));
            int bottom = ground - d;
            boolean rim = edge > 0.75;
            for (int y = top; y > bottom; y--) {
                p.set(x, y, z);
                BlockState s = level.getBlockState(p);
                if (s.isAir() || Terrain.protectedBlock(level, p, s)) {
                    continue;
                }
                if (rim && y >= ground - 1 && chance(0.18)) {
                    BlockState fall = s;
                    BlockPos at = p.immutable();
                    blocks.run(lvl -> {
                        if (!lvl.getBlockState(at).isAir()) {
                            FallingBlockEntity.fall(lvl, at, fall);
                        }
                    });
                    continue;
                }
                blocks.set(p.immutable(), Blocks.AIR.defaultBlockState(), BlockQueue.FAST);
            }
            blocks.set(new BlockPos(x, bottom, z), chance(0.55) ? Blocks.LAVA.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState(), BlockQueue.FAST);
            blocks.set(new BlockPos(x, bottom - 1, z), Blocks.MAGMA_BLOCK.defaultBlockState(), BlockQueue.FAST);
            if (i % 2 == 0 && chance(0.35)) {
                BlockState s = level.getBlockState(new BlockPos(x, ground, z));
                if (!s.isAir()) {
                    DebrisEntity db = debris(new Vec3(x + 0.5, ground + 1, z + 0.5), s, new Vec3(rand(-0.3, 0.3), rand(0.6, 1.1), rand(-0.3, 0.3)));
                    if (db != null) {
                        db.placeChance(0.2F);
                    }
                }
            }
        }
        if (i % 2 == 0) {
            particles(TWRegistry.P_DUST.get(), new Vec3(pt[0], Terrain.ground(level, Mth.floor(pt[0]), Mth.floor(pt[1])) + 1.5, pt[1]), 18, 2.0, 0.15);
        }
        if (i % 6 == 0) {
            sound(new Vec3(pt[0], center.y, pt[1]), TWRegistry.COLLAPSE.get(), 4.0F, 0.8F + random.nextFloat() * 0.3F);
        }
    }

    private void collapseStructures(int samples) {
        double r = 45 + 20 * intensity;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < samples; i++) {
            double a = random.nextDouble() * Math.PI * 2, d = Math.sqrt(random.nextDouble()) * r;
            int x = Mth.floor(center.x + Math.cos(a) * d), z = Mth.floor(center.z + Math.sin(a) * d);
            if (!Terrain.loaded(level, x, z)) {
                continue;
            }
            int ground = Terrain.ground(level, x, z);
            int top = Terrain.top(level, x, z);
            int y = ground + random.nextInt(Math.max(1, Math.min(24, top - ground + 2)));
            p.set(x, y, z);
            BlockState s = level.getBlockState(p);
            if (Terrain.structural(s) && chance(0.6)) {
                FallingBlockEntity f = FallingBlockEntity.fall(level, p.immutable(), s);
                f.setHurtsEntities(1.5F, 20);
            } else if (s.is(net.minecraft.tags.BlockTags.LEAVES) && chance(0.3)) {
                particles(TWRegistry.P_DUST.get(), new Vec3(x, y, z), 3, 0.8, 0.02);
            }
        }
    }

    private void jolt(double strength) {
        if (age % 3 != 0) {
            return;
        }
        for (Entity e : entitiesWithin(center, 70 + 30 * intensity)) {
            if (!e.onGround()) {
                continue;
            }
            double s = 0.12 * strength * intensity;
            push(e, rand(-s, s), s * 0.6, rand(-s, s));
        }
    }
}
