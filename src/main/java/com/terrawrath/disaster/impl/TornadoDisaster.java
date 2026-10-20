package com.terrawrath.disaster.impl;

import com.terrawrath.disaster.Disaster;
import com.terrawrath.disaster.DisasterType;
import com.terrawrath.disaster.Terrain;
import com.terrawrath.entity.BoltEntity;
import com.terrawrath.entity.DebrisEntity;
import com.terrawrath.registry.TWRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * "The Finger of God" — a 160+ block funnel that wanders, rips up the land and flings it for miles.
 */
public class TornadoDisaster extends Disaster {
    private final float height;
    private final float baseRadius;
    private final float topRadius;
    private final Vec3 origin;
    private double heading;
    private double turn;
    private final double speed;
    private double groundY;
    private final List<DebrisEntity> orbiting = new ArrayList<>();

    public TornadoDisaster(ServerLevel level, Vec3 center, float intensity, int warnTicks, boolean natural, Vec3 viewer) {
        super(level, DisasterType.TORNADO, center, intensity, warnTicks, natural);
        this.height = 150 + 35 * intensity;
        this.baseRadius = 5.5F + 4.0F * intensity;
        this.topRadius = 38 + 22 * intensity;
        this.origin = center;
        this.speed = 0.13 + 0.06 * intensity;
        this.groundY = center.y;
        if (viewer != null) {
            // head roughly toward the viewer but pass beside them: perfect for filming
            double toViewer = Math.atan2(viewer.z - center.z, viewer.x - center.x);
            this.heading = toViewer + (random.nextBoolean() ? 1 : -1) * (0.35 + random.nextDouble() * 0.25);
        } else {
            this.heading = random.nextDouble() * Math.PI * 2;
        }
    }

    @Override
    protected void onStart() {
        anchor.setRadius(baseRadius);
        anchor.setA(height);
        anchor.setB(baseRadius);
        anchor.setC(topRadius);
        anchor.setD((float) heading);
        anchor.setShake(0.08F);
    }

    @Override
    protected void tickWarning() {
        float p = phaseTick / (float) warnTicks;
        anchor.setShake(0.05F + p * 0.25F);
        if (p > 0.55F) {
            suck(0.35 * (p - 0.55) / 0.45);
        }
    }

    @Override
    protected void onActivate() {
        anchor.fireEvent();
        sound(center, TWRegistry.IMPACT.get(), 6.0F, 0.6F);
    }

    @Override
    protected void tickActive() {
        move(1.0);
        anchor.setShake(0.55F + 0.3F * intensity);
        anchor.setD((float) heading);
        if (terrain()) {
            rip();
        }
        suck(1.0);
        orbiting.removeIf(Entity::isRemoved);
        if (phaseTick % 28 == 14 && chance(0.75)) {
            double ang = random.nextDouble() * Math.PI * 2;
            double r = topRadius * rand(0.3, 0.9);
            Vec3 p = new Vec3(center.x + Math.cos(ang) * r, groundY + height * rand(0.55, 0.85), center.z + Math.sin(ang) * r);
            BoltEntity.strike(level, p, (float) rand(25, 55), 0xD9D2FF, 0.7F, false);
        }
    }

    @Override
    protected void onEnding() {
        for (DebrisEntity d : orbiting) {
            if (!d.isRemoved()) {
                Vec3 out = d.position().subtract(center);
                Vec3 dir = new Vec3(out.x, 0, out.z).normalize();
                d.release(new Vec3(dir.x * 1.6, 0.5, dir.z * 1.6));
            }
        }
        orbiting.clear();
    }

    @Override
    protected void tickEnding() {
        move(0.5);
        float p = phaseTick / (float) endTicks;
        anchor.setShake(0.4F * (1 - p));
        suck(0.5 * (1 - p));
    }

    private void move(double factor) {
        turn += (random.nextDouble() - 0.5) * 0.02;
        turn = Mth.clamp(turn * 0.98, -0.03, 0.03);
        heading += turn;
        Vec3 away = center.subtract(origin);
        double dist = Math.sqrt(away.x * away.x + away.z * away.z);
        if (dist > 150) {
            double back = Math.atan2(-away.z, -away.x);
            heading += Mth.wrapDegrees((float) Math.toDegrees(back - heading)) * 0.002;
        }
        double nx = center.x + Math.cos(heading) * speed * factor;
        double nz = center.z + Math.sin(heading) * speed * factor;
        int gx = Mth.floor(nx), gz = Mth.floor(nz);
        if (Terrain.loaded(level, gx, gz)) {
            double g = Terrain.solidGround(level, gx, gz) + 1;
            groundY += (g - groundY) * 0.08;
        }
        center = new Vec3(nx, groundY, nz);
        anchor.setPos(center.x, center.y, center.z);
    }

    private void rip() {
        int samples = (int) (14 + 16 * intensity);
        double ripRadius = baseRadius * 1.7 + 2;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < samples; i++) {
            double ang = random.nextDouble() * Math.PI * 2;
            double r = Math.sqrt(random.nextDouble()) * ripRadius;
            int x = Mth.floor(center.x + Math.cos(ang) * r);
            int z = Mth.floor(center.z + Math.sin(ang) * r);
            if (!Terrain.loaded(level, x, z)) {
                continue;
            }
            int y = Terrain.top(level, x, z);
            if (y < groundY - 8 || y > groundY + 45) {
                continue;
            }
            p.set(x, y, z);
            BlockState s = level.getBlockState(p);
            if (s.isAir() || Terrain.protectedBlock(level, p, s)) {
                continue;
            }
            if (Terrain.isWater(s)) {
                if (chance(0.3)) {
                    particles(TWRegistry.P_SPRAY.get(), new Vec3(x + 0.5, y + 1, z + 0.5), 4, 0.6, 0.3);
                }
                continue;
            }
            boolean core = r < baseRadius;
            if (Terrain.weak(s) || core && chance(0.06 * intensity) && s.getBlock().defaultDestroyTime() < 3.5F) {
                BlockState scar = s.is(Blocks.GRASS_BLOCK) && chance(0.5) ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState();
                level.setBlock(p, scar, 2);
                if (chance(0.65)) {
                    launchOrbit(x + 0.5, y + 0.5, z + 0.5, s);
                }
            }
        }
    }

    private void launchOrbit(double x, double y, double z, BlockState s) {
        double dx = x - center.x, dz = z - center.z;
        double r = Math.max(baseRadius * 0.8, Math.sqrt(dx * dx + dz * dz));
        DebrisEntity d = debris(new Vec3(x, y, z), s, Vec3.ZERO);
        if (d == null) {
            return;
        }
        d.placeChance(0.3F).maxLife(900);
        d.orbit(anchor, r, Math.atan2(dz, dx), y - center.y, rand(0.17, 0.32), rand(0.03, 0.08),
            rand(0.35, 0.95), rand(30, height * 0.6), rand(1.2, 2.6), false);
        orbiting.add(d);
    }

    private void suck(double strength) {
        double range = 26 + 20 * intensity;
        for (Entity e : entitiesWithin(new Vec3(center.x, center.y + 20, center.z), range + 25)) {
            double dx = center.x - e.getX(), dz = center.z - e.getZ();
            double d = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
            if (d > range) {
                continue;
            }
            double f = Math.pow(1 - d / range, 1.15);
            double pull = (0.035 + 0.075 * intensity * f) * strength;
            double spin = (0.05 + 0.12 * f) * strength;
            double tx = -dz / d, tz = dx / d;
            double lift = 0;
            if (d < baseRadius * 2.2) {
                lift = (0.09 + 0.07 * intensity) * strength;
                if (e.getY() > center.y + 28 + 20 * intensity) {
                    // spat out the side
                    push(e, -dx / d * 1.6 * strength, 0.25, -dz / d * 1.6 * strength);
                    continue;
                }
                if (e instanceof LivingEntity living && age % 15 == 0) {
                    living.hurtServer(level, level.damageSources().flyIntoWall(), 2.0F + intensity);
                }
            }
            push(e, dx / d * pull + tx * spin, lift, dz / d * pull + tz * spin);
        }
    }
}
