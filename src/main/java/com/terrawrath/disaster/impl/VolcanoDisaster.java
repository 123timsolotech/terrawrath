package com.terrawrath.disaster.impl;

import com.terrawrath.disaster.BlockQueue;
import com.terrawrath.disaster.Disaster;
import com.terrawrath.disaster.DisasterType;
import com.terrawrath.disaster.Terrain;
import com.terrawrath.entity.BoltEntity;
import com.terrawrath.entity.DebrisEntity;
import com.terrawrath.entity.MeteorEntity;
import com.terrawrath.entity.ShockwaveEntity;
import com.terrawrath.registry.TWRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * "Mountain of Fire" — an 80+ block volcano rises out of the ground, then erupts.
 */
public class VolcanoDisaster extends Disaster {
    private final int peak;
    private final int baseRadius;
    private final int craterRadius;
    private final int craterDepth = 12;
    private final int baseY;
    private final int growTicks;
    // per-column data
    private final int[] colX;
    private final int[] colZ;
    private final short[] colFinal;   // final height above baseY
    private final int[] colTop;       // current absolute top y
    private final byte[] colMat;
    private int grown;                // current height reached (blocks)
    private boolean craterDone;
    private float eruption;
    private final List<River> rivers = new ArrayList<>();
    private int nextBlast = 100;

    private static final class River {
        double x, z, angle;
        int steps;
        boolean done;
    }

    public VolcanoDisaster(ServerLevel level, Vec3 center, float intensity, int warnTicks, boolean natural) {
        super(level, DisasterType.VOLCANO, center, intensity, warnTicks, natural);
        this.peak = (int) (88 + 22 * intensity);
        this.baseRadius = (int) (peak * 0.95);
        this.craterRadius = (int) (8 + 3 * intensity);
        this.growTicks = (int) (520 + 120 * intensity);
        this.activeTicks = growTicks + (int) (1300 * Mth.clamp(0.8F + intensity * 0.2F, 0.8F, 1.3F));
        // average ground for a stable base
        int sum = 0, n = 0;
        for (int i = 0; i < 16; i++) {
            double a = i / 16.0 * Math.PI * 2;
            int x = Mth.floor(center.x + Math.cos(a) * 12), z = Mth.floor(center.z + Math.sin(a) * 12);
            sum += Terrain.solidGround(level, x, z);
            n++;
        }
        this.baseY = Math.max(level.getSeaLevel() - 2, sum / Math.max(1, n));
        // precompute every column of the cone
        List<int[]> cols = new ArrayList<>();
        int cx = Mth.floor(center.x), cz = Mth.floor(center.z);
        double ridgePhase = random.nextDouble() * 10;
        for (int dx = -baseRadius; dx <= baseRadius; dx++) {
            for (int dz = -baseRadius; dz <= baseRadius; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > baseRadius) {
                    continue;
                }
                double t = d / baseRadius;
                double ang = Math.atan2(dz, dx);
                double ridges = Math.sin(ang * 7 + ridgePhase + t * 6) * 0.5 + Math.sin(ang * 13 - ridgePhase * 2) * 0.3;
                double h = peak * Math.pow(1 - t, 1.45) + ridges * 4.5 * t * (1 - t) * 3;
                int hi = (int) Math.round(h);
                if (hi < 1) {
                    continue;
                }
                // radial gullies running down the flanks + ash-grey upper slopes + glowing summit
                double gully = Math.sin(ang * 23 + Math.sin(d * 0.21 + ridgePhase) * 1.6 + Math.sin(ang * 5) * 2.0);
                double speck = noise(dx * 0.37, dz * 0.37);
                int mat;
                if (d < craterRadius * 2.0) {
                    mat = gully > 0.35 ? 4 : 1;                       // magma streaks in blackstone
                } else if (t < 0.45) {
                    mat = gully > 0.55 ? 3 : gully < -0.6 ? 6 : 7;    // tuff / andesite streaks on smooth basalt
                } else if (t < 0.8) {
                    mat = gully > 0.6 ? 5 : gully < -0.55 ? 3 : speck > 0.6 ? 2 : 7;
                } else {
                    mat = speck > 0.2 ? 5 : gully > 0.3 ? 6 : 0;      // coarse dirt / andesite apron
                }
                cols.add(new int[]{cx + dx, cz + dz, hi, mat});
            }
        }
        int n2 = cols.size();
        colX = new int[n2];
        colZ = new int[n2];
        colFinal = new short[n2];
        colTop = new int[n2];
        colMat = new byte[n2];
        for (int i = 0; i < n2; i++) {
            int[] c = cols.get(i);
            colX[i] = c[0];
            colZ[i] = c[1];
            colFinal[i] = (short) c[2];
            colTop[i] = Integer.MIN_VALUE;
            colMat[i] = (byte) c[3];
        }
        this.center = new Vec3(center.x, baseY + 1, center.z);
    }

    private static double noise(double x, double z) {
        return Math.sin(x * 1.7 + Math.sin(z * 1.3) * 1.5) * 0.5 + Math.sin(z * 2.1 + Math.cos(x * 0.9) * 1.2) * 0.5;
    }

    private static BlockState surface(int mat) {
        return switch (mat) {
            case 1 -> Blocks.BLACKSTONE.defaultBlockState();
            case 2 -> Blocks.BASALT.defaultBlockState();
            case 3 -> Blocks.TUFF.defaultBlockState();
            case 4 -> Blocks.MAGMA_BLOCK.defaultBlockState();
            case 5 -> Blocks.COARSE_DIRT.defaultBlockState();
            case 6 -> Blocks.ANDESITE.defaultBlockState();
            case 7 -> Blocks.SMOOTH_BASALT.defaultBlockState();
            default -> Blocks.BLACKSTONE.defaultBlockState();
        };
    }

    @Override
    protected double alertRadius() {
        return 360;
    }

    @Override
    protected void onStart() {
        anchor.setRadius(baseRadius);
        anchor.setA(0);
        anchor.setB(0);
        anchor.setC(peak);
        anchor.setD(craterRadius);
        anchor.setShake(0.1F);
    }

    @Override
    protected void tickWarning() {
        float p = phaseTick / (float) warnTicks;
        anchor.setShake(0.1F + 0.5F * p);
        if (phaseTick % 6 == 0) {
            double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * baseRadius * 0.6;
            int x = Mth.floor(center.x + Math.cos(a) * r), z = Mth.floor(center.z + Math.sin(a) * r);
            particles(TWRegistry.P_DUST.get(), new Vec3(x, Terrain.top(level, x, z) + 1, z), 6, 1.5, 0.05);
        }
    }

    @Override
    protected void onActivate() {
        anchor.fireEvent();
        sound(center, TWRegistry.ERUPTION.get(), 10.0F, 0.55F);
    }

    @Override
    protected void tickActive() {
        if (phaseTick <= growTicks) {
            grow();
            return;
        }
        if (!craterDone) {
            carveCrater();
            craterDone = true;
            startRivers();
            anchor.fireEvent();
            sound(craterPos(), TWRegistry.ERUPTION.get(), 16.0F, 0.8F);
            ShockwaveEntity.spawn(level, craterPos(), 70, 0.9F, 0xFF7A2E, 50);
        }
        int eruptTick = phaseTick - growTicks;
        int eruptLength = activeTicks - growTicks;
        float fadeIn = Mth.clamp(eruptTick / 60F, 0, 1);
        float fadeOut = Mth.clamp((eruptLength - eruptTick) / 120F, 0, 1);
        eruption = Math.min(fadeIn, fadeOut);
        anchor.setB(eruption);
        anchor.setShake(0.35F + 0.25F * eruption * intensity);

        Vec3 crater = craterPos();
        // lava bombs
        double bombRate = (0.22 + 0.2 * intensity) * eruption;
        while (bombRate > 0) {
            if (chance(Math.min(1, bombRate))) {
                launchBomb(crater, 1.0);
            }
            bombRate -= 1;
        }
        // big blasts
        if (eruptTick >= nextBlast) {
            nextBlast = eruptTick + 140 + random.nextInt(120);
            anchor.fireEvent();
            sound(crater, TWRegistry.ERUPTION.get(), 14.0F, 0.7F + random.nextFloat() * 0.2F);
            ShockwaveEntity.spawn(level, crater, 45, 0.5F, 0xFF6A1F, 35);
            for (int i = 0; i < 6 + 4 * intensity; i++) {
                launchBomb(crater, 1.4);
            }
        }
        // volcanic lightning inside the ash column
        if (chance(0.035 * (1 + intensity) * eruption)) {
            double ang = random.nextDouble() * Math.PI * 2, r = rand(5, 30);
            Vec3 p = new Vec3(crater.x + Math.cos(ang) * r, crater.y + rand(55, 140), crater.z + Math.sin(ang) * r);
            BoltEntity.strike(level, p, (float) rand(25, 60), 0xE7DBFF, 0.8F, false);
        }
        if (terrain()) {
            tickRivers();
        }
        // pyroclastic heat near the summit
        if (age % 10 == 0) {
            for (Entity e : entitiesWithin(crater, craterRadius + 10)) {
                if (e instanceof LivingEntity living) {
                    living.igniteForSeconds(4);
                    living.hurtServer(level, level.damageSources().hotFloor(), 2.0F);
                }
            }
        }
        // ash fall
        if (age % 4 == 0) {
            double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * 70;
            particles(TWRegistry.P_ASH.get(), new Vec3(crater.x + Math.cos(a) * r, crater.y + 30, crater.z + Math.sin(a) * r), 8, 8, 0.02);
        }
    }

    @Override
    protected void tickEnding() {
        float p = phaseTick / (float) endTicks;
        anchor.setB(0);
        anchor.setShake(0.25F * (1 - p));
    }

    private Vec3 craterPos() {
        return new Vec3(center.x, baseY + peak - craterDepth + 3, center.z);
    }

    private void grow() {
        float g = phaseTick / (float) growTicks;
        float eased = g * g * (3 - 2 * g);
        int target = Math.round(peak * eased);
        anchor.setA(target);
        anchor.setPos(center.x, baseY + target + 1, center.z);
        anchor.setShake(0.7F + 0.3F * intensity);
        if (target <= grown) {
            return;
        }
        grown = target;
        float scale = target / (float) peak;
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState under = Blocks.BLACKSTONE.defaultBlockState();
        for (int i = 0; i < colX.length; i++) {
            int x = colX[i], z = colZ[i];
            if (colTop[i] == Integer.MIN_VALUE) {
                if (!Terrain.loaded(level, x, z)) {
                    continue;
                }
                colTop[i] = Math.min(Terrain.solidGround(level, x, z), baseY);
            }
            int want = baseY + Math.round(colFinal[i] * scale);
            int cur = colTop[i];
            if (want <= cur) {
                continue;
            }
            BlockState top = surface(colMat[i]);
            for (int y = cur + 1; y <= want; y++) {
                BlockState s = y == want ? top : y >= want - 2 ? under : stone;
                blocks.set(x, y, z, s, BlockQueue.FAST);
            }
            colTop[i] = want;
        }
        if (phaseTick % 10 == 0) {
            unstick(center, baseRadius);
            sound(new Vec3(center.x, baseY + target, center.z), TWRegistry.CRACK.get(), 5.0F, 0.5F + random.nextFloat() * 0.3F);
            for (int i = 0; i < 6; i++) {
                int idx = random.nextInt(colX.length);
                particles(TWRegistry.P_DUST.get(), new Vec3(colX[idx], colTop[idx] + 1, colZ[idx]), 8, 2.5, 0.08);
            }
        }
    }

    private void carveCrater() {
        int cx = Mth.floor(center.x), cz = Mth.floor(center.z);
        int top = baseY + peak;
        int floor = top - craterDepth;
        for (int dx = -craterRadius - 1; dx <= craterRadius + 1; dx++) {
            for (int dz = -craterRadius - 1; dz <= craterRadius + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > craterRadius + 0.5) {
                    continue;
                }
                double t = d / craterRadius;
                int bottom = floor + (int) Math.round(t * t * craterDepth * 0.75);
                for (int y = top + 2; y > bottom; y--) {
                    BlockState s = y <= floor + 3 ? Blocks.LAVA.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    blocks.set(cx + dx, y, cz + dz, s, BlockQueue.FAST);
                }
                blocks.set(cx + dx, bottom, cz + dz, Blocks.MAGMA_BLOCK.defaultBlockState(), BlockQueue.FAST);
            }
        }
        anchor.setA(peak);
        anchor.setPos(center.x, floor + 3, center.z);
    }

    private void startRivers() {
        int count = 3 + Math.round(2 * intensity);
        double base = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            River r = new River();
            r.angle = base + i * Math.PI * 2 / count + rand(-0.3, 0.3);
            r.x = center.x + Math.cos(r.angle) * (craterRadius + 1);
            r.z = center.z + Math.sin(r.angle) * (craterRadius + 1);
            rivers.add(r);
        }
    }

    private void tickRivers() {
        if (age % 2 != 0) {
            return;
        }
        for (River r : rivers) {
            if (r.done) {
                continue;
            }
            r.angle += rand(-0.08, 0.08);
            r.x += Math.cos(r.angle);
            r.z += Math.sin(r.angle);
            r.steps++;
            int x = Mth.floor(r.x), z = Mth.floor(r.z);
            double d = Math.sqrt((x - center.x) * (x - center.x) + (z - center.z) * (z - center.z));
            if (d > baseRadius + 4 || r.steps > baseRadius + 10 || !Terrain.loaded(level, x, z)) {
                r.done = true;
                // spill out at the foot of the mountain
                int y = Terrain.ground(level, x, z);
                blocks.set(new BlockPos(x, y + 1, z), Blocks.LAVA.defaultBlockState(), BlockQueue.PHYSICS);
                continue;
            }
            int y = Terrain.ground(level, x, z);
            blocks.set(new BlockPos(x, y, z), Blocks.LAVA.defaultBlockState(), BlockQueue.FAST);
            blocks.set(new BlockPos(x, y - 1, z), Blocks.MAGMA_BLOCK.defaultBlockState(), BlockQueue.FAST);
            int sx = Mth.floor(r.x - Math.sin(r.angle)), sz = Mth.floor(r.z + Math.cos(r.angle));
            int sy = Terrain.ground(level, sx, sz);
            blocks.set(new BlockPos(sx, sy, sz), Blocks.LAVA.defaultBlockState(), BlockQueue.FAST);
            if (chance(0.25)) {
                particles(TWRegistry.P_EMBER.get(), new Vec3(x + 0.5, y + 1.2, z + 0.5), 4, 0.5, 0.05);
            }
        }
    }

    private void launchBomb(Vec3 crater, double power) {
        double ang = random.nextDouble() * Math.PI * 2;
        double hs = rand(0.35, 1.25) * power;
        Vec3 vel = new Vec3(Math.cos(ang) * hs, rand(1.4, 2.3) * power, Math.sin(ang) * hs);
        Vec3 from = crater.add(rand(-2, 2), 2, rand(-2, 2));
        float size = (float) rand(1.0, 2.4);
        MeteorEntity.launch(level, from, vel, from, MeteorEntity.KIND_LAVA_BOMB, size, 0.05, m -> bombImpact(m.position(), size));
    }

    private void bombImpact(Vec3 at, float size) {
        level.explode(null, at.x, at.y, at.z, 1.6F + size * 0.6F, true, Level.ExplosionInteraction.NONE);
        sound(at, TWRegistry.IMPACT.get(), 3.0F, 1.3F + random.nextFloat() * 0.3F);
        particles(TWRegistry.P_EMBER.get(), at.add(0, 0.5, 0), 25, 1.2, 0.25);
        particles(TWRegistry.P_SMOKE.get(), at.add(0, 1, 0), 6, 1.0, 0.04);
        if (terrain()) {
            BlockPos p = BlockPos.containing(at);
            for (int i = 0; i < 4; i++) {
                BlockPos q = p.offset(random.nextInt(3) - 1, random.nextInt(2) - 1, random.nextInt(3) - 1);
                BlockState s = level.getBlockState(q);
                if (!s.isAir() && Terrain.solid(s) && !Terrain.protectedBlock(level, q, s)) {
                    blocks.set(q, chance(0.6) ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState(), BlockQueue.FAST);
                } else if (s.isAir() && chance(0.4)) {
                    blocks.set(q, Blocks.FIRE.defaultBlockState(), BlockQueue.PHYSICS);
                }
            }
        }
        for (int i = 0; i < 2; i++) {
            DebrisEntity d = debris(at.add(0, 1, 0), Blocks.MAGMA_BLOCK.defaultBlockState(),
                new Vec3(rand(-0.5, 0.5), rand(0.4, 0.8), rand(-0.5, 0.5)));
            if (d != null) {
                d.setBurning(true);
                d.placeChance(0.15F);
            }
        }
    }
}
