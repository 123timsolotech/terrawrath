package com.terrawrath.disaster.impl;

import com.terrawrath.disaster.BlockQueue;
import com.terrawrath.disaster.Disaster;
import com.terrawrath.disaster.DisasterType;
import com.terrawrath.disaster.Terrain;
import com.terrawrath.entity.DebrisEntity;
import com.terrawrath.registry.TWRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * "The Swallow" — concentric cracks, then the ground collapses ring by ring into a deep pit.
 */
public class SinkholeDisaster extends Disaster {
    private final float holeRadius;
    private final int depth;
    private final int ringWidth = 2;
    private final int ringInterval;
    private int collapsed = -1;   // last collapsed ring index
    private final int rings;
    private final int baseY;
    private int fallingBudget;

    public SinkholeDisaster(ServerLevel level, Vec3 center, float intensity, int warnTicks, boolean natural) {
        super(level, DisasterType.SINKHOLE, center, intensity, warnTicks, natural);
        this.holeRadius = 13 + 9 * intensity;
        this.depth = (int) (36 + 20 * intensity);
        this.rings = (int) Math.ceil(holeRadius / ringWidth);
        this.ringInterval = 9;
        this.baseY = Terrain.solidGround(level, Mth.floor(center.x), Mth.floor(center.z));
        this.activeTicks = Math.max(activeTicks, rings * ringInterval + 260);
    }

    @Override
    protected double alertRadius() {
        return 220;
    }

    @Override
    protected void onStart() {
        anchor.setRadius(holeRadius);
        anchor.setA(0);
        anchor.setB(holeRadius);
        anchor.setC(depth);
    }

    @Override
    protected void tickWarning() {
        float p = phaseTick / (float) warnTicks;
        anchor.setShake(0.08F + 0.3F * p);
        // cracks circle the ground, outermost first
        int[] marks = {warnTicks / 6, warnTicks * 2 / 5, warnTicks * 3 / 5, warnTicks * 4 / 5};
        double[] radii = {holeRadius, holeRadius * 0.72, holeRadius * 0.45, holeRadius * 0.2};
        for (int i = 0; i < marks.length; i++) {
            if (phaseTick == marks[i]) {
                crackRing(radii[i]);
            }
        }
    }

    private void crackRing(double r) {
        sound(center, TWRegistry.CRACK.get(), 6.0F, 0.7F + random.nextFloat() * 0.3F);
        int steps = (int) (r * Math.PI * 2 * 1.4);
        double wobble = random.nextDouble() * 10;
        for (int i = 0; i < steps; i++) {
            double a = i / (double) steps * Math.PI * 2;
            double rr = r + Math.sin(a * 5 + wobble) * 1.2 + Math.sin(a * 11) * 0.6;
            int x = Mth.floor(center.x + Math.cos(a) * rr), z = Mth.floor(center.z + Math.sin(a) * rr);
            if (!Terrain.loaded(level, x, z)) {
                continue;
            }
            int y = Terrain.ground(level, x, z);
            if (terrain()) {
                BlockState s = chance(0.5) ? Blocks.COARSE_DIRT.defaultBlockState() : chance(0.5) ? Blocks.GRAVEL.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState();
                blocks.set(new BlockPos(x, y, z), s, BlockQueue.FAST);
                if (chance(0.3)) {
                    blocks.set(new BlockPos(x, y - 1, z), Blocks.AIR.defaultBlockState(), BlockQueue.FAST);
                }
            }
            if (i % 4 == 0) {
                particles(TWRegistry.P_DUST.get(), new Vec3(x + 0.5, y + 1.1, z + 0.5), 3, 0.6, 0.05);
            }
        }
    }

    @Override
    protected void onActivate() {
        anchor.fireEvent();
        sound(center, TWRegistry.COLLAPSE.get(), 10.0F, 0.6F);
    }

    @Override
    protected void tickActive() {
        anchor.setShake(0.45F + 0.25F * intensity);
        fallingBudget = 22;
        int ring = phaseTick / ringInterval;
        while (collapsed < Math.min(ring, rings - 1)) {
            collapsed++;
            if (terrain()) {
                collapseRing(collapsed);
            }
            sound(center, TWRegistry.COLLAPSE.get(), 6.0F + collapsed * 0.3F, 0.7F + random.nextFloat() * 0.3F);
        }
        float open = Math.min(holeRadius, (collapsed + 1) * ringWidth);
        anchor.setA(open);
        // dust boils up out of the pit
        if (age % 3 == 0) {
            double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * open;
            particles(TWRegistry.P_DUST.get(), new Vec3(center.x + Math.cos(a) * r, baseY + 1, center.z + Math.sin(a) * r), 10, 2.5, 0.12);
        }
        swallow(open);
        if (collapsed >= rings - 1) {
            anchor.setShake(0.15F);
        }
    }

    @Override
    protected void tickEnding() {
        anchor.setShake(0);
        if (age % 6 == 0) {
            double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * holeRadius;
            particles(TWRegistry.P_DUST.get(), new Vec3(center.x + Math.cos(a) * r, baseY - depth / 2.0, center.z + Math.sin(a) * r), 6, 3, 0.08);
        }
    }

    private void collapseRing(int ring) {
        double r0 = ring * ringWidth, r1 = r0 + ringWidth;
        int cx = Mth.floor(center.x), cz = Mth.floor(center.z);
        int ri = (int) Math.ceil(r1 + 2);
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dz = -ri; dz <= ri; dz++) {
                double ang = Math.atan2(dz, dx);
                double d = Math.sqrt(dx * dx + dz * dz) - Math.sin(ang * 6 + ring) * 0.8;
                if (d < r0 || d >= r1 || d > holeRadius + 0.5) {
                    continue;
                }
                int x = cx + dx, z = cz + dz;
                if (!Terrain.loaded(level, x, z)) {
                    continue;
                }
                int top = Terrain.top(level, x, z);
                double t = d / holeRadius;
                int bottom = baseY - (int) (depth * (1 - t * t * 0.35)) - random.nextInt(3);
                BlockState topState = level.getBlockState(new BlockPos(x, top, z));
                for (int y = top; y > bottom; y--) {
                    blocks.set(x, y, z, Blocks.AIR.defaultBlockState(), BlockQueue.FAST);
                }
                blocks.set(x, bottom, z, chance(0.4) ? Blocks.GRAVEL.defaultBlockState() : chance(0.5) ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState(), BlockQueue.FAST);
                // the surface drops into the pit as real falling blocks
                if (!topState.isAir() && fallingBudget > 0 && chance(0.3)) {
                    fallingBudget--;
                    BlockPos at = new BlockPos(x, top, z);
                    BlockState fall = Terrain.debrisLook(topState);
                    blocks.run(lvl -> FallingBlockEntity.fall(lvl, at, fall));
                } else if (!topState.isAir() && chance(0.04)) {
                    DebrisEntity d2 = debris(new Vec3(x + 0.5, top + 1, z + 0.5), topState, new Vec3(-dx * 0.02, rand(0.3, 0.6), -dz * 0.02));
                    if (d2 != null) {
                        d2.placeChance(0.6F);
                    }
                }
            }
        }
        // the rim cracks and slumps
        if (ring == rings - 1) {
            crackRing(holeRadius + 2.5);
        }
    }

    private void swallow(float open) {
        for (Entity e : entitiesWithin(new Vec3(center.x, baseY, center.z), open + 3)) {
            double dx = center.x - e.getX(), dz = center.z - e.getZ();
            double d = Math.max(1, Math.sqrt(dx * dx + dz * dz));
            if (d > open + 2) {
                continue;
            }
            push(e, dx / d * 0.035, -0.04, dz / d * 0.035);
        }
    }
}
