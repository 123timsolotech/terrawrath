package com.terrawrath.disaster.impl;

import com.terrawrath.disaster.BlockQueue;
import com.terrawrath.disaster.Disaster;
import com.terrawrath.disaster.DisasterType;
import com.terrawrath.disaster.Terrain;
import com.terrawrath.entity.DebrisEntity;
import com.terrawrath.registry.TWRegistry;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * "The Wall" — the sea drains away, then a 40+ block wave rolls over hundreds of blocks of land.
 */
public class TsunamiDisaster extends Disaster {
    private final double dirX;   // unit vector pointing from the land toward the sea (the wave travels opposite)
    private final double dirZ;
    private final double startDist = 240;
    private final double endDist;
    private final double speed;
    private final float waveHeight;
    private final float width;
    private final int seaY;
    private final double shoreDist;
    private final boolean hasSea;
    private double front;
    private final LongArrayList drained = new LongArrayList();
    private boolean refilled;
    private double drainCursor;

    public TsunamiDisaster(ServerLevel level, Vec3 center, float intensity, int warnTicks, boolean natural, Vec3 viewer, Vec3 viewDir) {
        super(level, DisasterType.TSUNAMI, center, intensity, warnTicks, natural);
        this.seaY = level.getSeaLevel();
        this.waveHeight = 42 + 14 * intensity;
        this.width = 200 + 80 * intensity;
        this.speed = 0.8 + 0.2 * intensity;
        this.endDist = -(150 + 60 * intensity);
        double bestAngle = -1;
        int bestScore = 0;
        double bestShore = 0;
        for (int i = 0; i < 32; i++) {
            double a = i / 32.0 * Math.PI * 2;
            int score = 0;
            double firstWater = -1;
            for (int r = 20; r <= 260; r += 12) {
                int x = Mth.floor(center.x + Math.cos(a) * r), z = Mth.floor(center.z + Math.sin(a) * r);
                if (!Terrain.loaded(level, x, z)) {
                    break;
                }
                int y = Terrain.top(level, x, z);
                if (y <= seaY && Terrain.isWater(level.getBlockState(new BlockPos(x, y, z)))) {
                    score++;
                    if (firstWater < 0) {
                        firstWater = r;
                    }
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestAngle = a;
                bestShore = firstWater;
            }
        }
        double angle;
        if (viewDir != null && new Vec3(viewDir.x, 0, viewDir.z).lengthSqr() > 1e-4) {
            angle = Math.atan2(viewDir.z, viewDir.x);
            double diff = Math.abs(Mth.wrapDegrees((float) Math.toDegrees(angle - bestAngle)));
            this.hasSea = bestScore >= 6 && diff < 50;
        } else if (bestScore >= 6) {
            angle = bestAngle;
            this.hasSea = true;
        } else {
            angle = random.nextDouble() * Math.PI * 2;
            this.hasSea = false;
        }
        this.shoreDist = hasSea ? bestShore : 120;
        this.dirX = Math.cos(angle);
        this.dirZ = Math.sin(angle);
        this.front = startDist;
        int y = Math.max(seaY, (int) center.y);
        this.center = new Vec3(center.x, y, center.z);
    }

    @Override
    protected double alertRadius() {
        return 380;
    }

    @Override
    protected void onStart() {
        anchor.setRadius(width / 2);
        anchor.setA((float) front);
        anchor.setB((float) Math.atan2(dirZ, dirX));
        anchor.setC(waveHeight * 0.5F);
        anchor.setD(width);
        anchor.setPos(center.x, seaY, center.z);
        drainCursor = shoreDist - 6;
    }

    @Override
    protected void tickWarning() {
        float p = phaseTick / (float) warnTicks;
        anchor.setShake(0.05F + 0.2F * p);
        anchor.setC(waveHeight * (0.35F + 0.25F * p));
        // the sea pulls back: drain the near-shore strip, a few rows per tick
        if (hasSea && terrain() && drainCursor < shoreDist + 95) {
            for (int row = 0; row < 2; row++) {
                drainRow(drainCursor);
                drainCursor += 1;
            }
        }
    }

    private void drainRow(double along) {
        double px = -dirZ, pz = dirX;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (double l = -width / 2; l <= width / 2; l += 1) {
            int x = Mth.floor(center.x + dirX * along + px * l);
            int z = Mth.floor(center.z + dirZ * along + pz * l);
            if (!Terrain.loaded(level, x, z)) {
                continue;
            }
            int depth = 4 + (int) Math.min(4, (along - shoreDist + 6) / 12);
            for (int y = seaY; y > seaY - depth; y--) {
                p.set(x, y, z);
                BlockState s = level.getBlockState(p);
                if (Terrain.isWater(s) || s.is(Blocks.SEAGRASS) || s.is(Blocks.TALL_SEAGRASS) || s.is(Blocks.KELP) || s.is(Blocks.KELP_PLANT)) {
                    blocks.set(p.immutable(), Blocks.AIR.defaultBlockState(), BlockQueue.FAST);
                    if (Terrain.isWater(s)) {
                        drained.add(p.asLong());
                    }
                } else {
                    break;
                }
            }
        }
    }

    @Override
    protected void onActivate() {
        sound(waveFront(), TWRegistry.WAVE_CRASH.get(), 10.0F, 0.6F);
    }

    private Vec3 waveFront() {
        return new Vec3(center.x + dirX * front, seaY, center.z + dirZ * front);
    }

    private float heightFactor() {
        if (front > 0) {
            double approach = Mth.clamp((startDist - front) / (startDist - 20), 0, 1);
            return (float) (0.62 + 0.38 * approach);
        }
        return (float) Mth.clamp(1.0 + front / -endDist, 0.0, 1.0);
    }

    @Override
    protected void tickActive() {
        front -= speed;
        float hf = heightFactor();
        float h = waveHeight * hf;
        anchor.setA((float) front);
        anchor.setC(h);
        double closeness = Mth.clamp(1 - Math.abs(front) / 140, 0, 1);
        anchor.setShake((float) (0.15 + 0.6 * closeness));
        if (front < endDist) {
            phaseTick = activeTicks; // done rolling
            return;
        }
        if (!refilled && front < shoreDist - 4) {
            refilled = true;
            for (int i = 0; i < drained.size(); i++) {
                blocks.set(BlockPos.of(drained.getLong(i)), Blocks.WATER.defaultBlockState(), BlockQueue.FAST);
            }
        }
        if (Math.abs(front) < speed * 1.5) {
            sound(new Vec3(center.x, seaY + 5, center.z), TWRegistry.WAVE_CRASH.get(), 12.0F, 0.8F);
            anchor.fireEvent();
        }
        if (terrain()) {
            smashFront(h);
            flood(hf);
        }
        sweepEntities(h);
    }

    private void smashFront(float h) {
        double px = -dirZ, pz = dirX;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int topY = seaY + (int) h;
        int smashed = 0;
        for (double l = -width / 2; l <= width / 2; l += 1) {
            double edge = 1 - Math.pow(Math.abs(l) / (width / 2), 4);
            int localTop = seaY + (int) ((topY - seaY) * edge * 0.75);
            for (double k = 0; k < speed + 0.5; k += 1) {
                int x = Mth.floor(center.x + dirX * (front + k) + px * l);
                int z = Mth.floor(center.z + dirZ * (front + k) + pz * l);
                if (!Terrain.loaded(level, x, z)) {
                    continue;
                }
                int ground = Terrain.solidGround(level, x, z);
                int top = Math.min(Terrain.top(level, x, z), localTop);
                for (int y = ground + 1; y <= top; y++) {
                    p.set(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (s.isAir() || Terrain.isWater(s) || !Terrain.weak(s) || Terrain.protectedBlock(level, p, s)) {
                        continue;
                    }
                    blocks.set(p.immutable(), Blocks.AIR.defaultBlockState(), BlockQueue.FAST);
                    if (smashed++ < 6 && chance(0.25)) {
                        DebrisEntity d = debris(new Vec3(x + 0.5, y + 1, z + 0.5), s,
                            new Vec3(-dirX * rand(0.9, 1.5), rand(0.5, 0.9), -dirZ * rand(0.9, 1.5)));
                        if (d != null) {
                            d.placeChance(0.4F);
                        }
                    }
                }
                // scour sand and soil at the waterline
                p.set(x, ground, z);
                BlockState g = level.getBlockState(p);
                if (ground > seaY - 3 && chance(0.15) && (g.is(Blocks.SAND) || g.is(Blocks.GRASS_BLOCK) || g.is(Blocks.DIRT))) {
                    blocks.set(p.immutable(), g.is(Blocks.SAND) ? Blocks.WATER.defaultBlockState() : Blocks.MUD.defaultBlockState(), BlockQueue.FAST);
                }
            }
        }
    }

    private void flood(float hf) {
        double behind = front + 7;
        if (behind > shoreDist) {
            return; // still over the sea
        }
        double px = -dirZ, pz = dirX;
        int floodTop = seaY + 1 + Math.round(3 * hf);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (double l = -width / 2; l <= width / 2; l += 1) {
            for (double k = 0; k < speed + 0.5; k += 1) {
                int x = Mth.floor(center.x + dirX * (behind + k) + px * l);
                int z = Mth.floor(center.z + dirZ * (behind + k) + pz * l);
                if (!Terrain.loaded(level, x, z)) {
                    continue;
                }
                int ground = Terrain.solidGround(level, x, z);
                for (int y = ground + 1; y <= Math.min(floodTop, ground + 2); y++) {
                    p.set(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (s.isAir() || s.canBeReplaced() && !Terrain.isWater(s)) {
                        blocks.set(p.immutable(), Blocks.WATER.defaultBlockState(), BlockQueue.FAST);
                    }
                }
            }
        }
    }

    private void sweepEntities(float h) {
        Vec3 f = waveFront();
        double px = -dirZ, pz = dirX;
        for (Entity e : entitiesWithin(new Vec3(f.x, seaY + h / 2, f.z), width / 2 + 10)) {
            double rx = e.getX() - center.x, rz = e.getZ() - center.z;
            double along = rx * dirX + rz * dirZ;
            double lateral = rx * px + rz * pz;
            if (Math.abs(lateral) > width / 2 || e.getY() > seaY + h + 3) {
                continue;
            }
            double rel = along - front;
            if (rel < -3 || rel > 14) {
                continue;
            }
            push(e, -dirX * 0.22, 0.06, -dirZ * 0.22);
            if (e.getDeltaMovement().horizontalDistanceSqr() > 2.0) {
                e.setDeltaMovement(e.getDeltaMovement().multiply(0.8, 1, 0.8));
            }
            e.clearFire();
            if (e instanceof LivingEntity living && age % 12 == 0) {
                living.hurtServer(level, level.damageSources().drown(), 2.0F + intensity);
            }
        }
    }

    @Override
    protected void tickEnding() {
        anchor.setShake(0);
        anchor.setC(0);
    }

    @Override
    protected void onStop() {
        if (!refilled && !drained.isEmpty()) {
            // stopped early: give the sea back immediately
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int i = 0; i < drained.size(); i++) {
                p.set(drained.getLong(i));
                if (level.isLoaded(p) && level.getBlockState(p).isAir()) {
                    level.setBlock(p, Blocks.WATER.defaultBlockState(), BlockQueue.FAST);
                }
            }
        }
    }
}
