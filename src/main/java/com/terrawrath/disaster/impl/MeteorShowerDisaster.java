package com.terrawrath.disaster.impl;

import com.terrawrath.disaster.BlockQueue;
import com.terrawrath.disaster.Disaster;
import com.terrawrath.disaster.DisasterType;
import com.terrawrath.disaster.Terrain;
import com.terrawrath.entity.DebrisEntity;
import com.terrawrath.entity.MeteorEntity;
import com.terrawrath.entity.ShockwaveEntity;
import com.terrawrath.registry.TWRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * "Skyfall" — burning meteors rain in at an angle, then one colossal rock ends it all.
 */
public class MeteorShowerDisaster extends Disaster {
    private final double azimuth;
    private final double elevation;
    private final List<Vec3> craters = new ArrayList<>();
    private boolean colossalLaunched;
    private boolean colossalLanded;
    private final Vec3 viewer;

    public MeteorShowerDisaster(ServerLevel level, Vec3 center, float intensity, int warnTicks, boolean natural, Vec3 viewer) {
        super(level, DisasterType.METEOR_SHOWER, center, intensity, warnTicks, natural);
        this.viewer = viewer;
        if (viewer != null) {
            // meteors come in from the side so the trails cross the view
            double toCenter = Math.atan2(center.z - viewer.z, center.x - viewer.x);
            this.azimuth = toCenter + Math.PI / 2 * (random.nextBoolean() ? 1 : -1) + rand(-0.4, 0.4);
        } else {
            this.azimuth = random.nextDouble() * Math.PI * 2;
        }
        this.elevation = Math.toRadians(rand(32, 48));
    }

    @Override
    protected double alertRadius() {
        return 360;
    }

    private Vec3 travelDir(double elev) {
        // direction the meteor travels (downward, toward +azimuth)
        return new Vec3(Math.cos(azimuth) * Math.cos(elev), -Math.sin(elev), Math.sin(azimuth) * Math.cos(elev));
    }

    @Override
    protected void onStart() {
        anchor.setRadius(80);
        anchor.setA(0);
        anchor.setB((float) azimuth);
        anchor.setC((float) elevation);
    }

    @Override
    protected void tickWarning() {
        anchor.setShake(0.03F);
        // a few distant streaks foreshadow the shower
        if (phaseTick > warnTicks / 2 && chance(0.04)) {
            launch(randomTarget(140, 220), rand(0.6, 1.0), false);
        }
    }

    @Override
    protected void onActivate() {
        sound(center, TWRegistry.METEOR_WHISTLE.get(), 8.0F, 0.9F);
    }

    @Override
    protected void tickActive() {
        float p = phaseTick / (float) activeTicks;
        anchor.setShake(0.12F);
        if (!colossalLaunched) {
            double rate = (0.1 + 0.11 * intensity) * Mth.clamp(phaseTick / 60F, 0.2F, 1F);
            if (chance(rate)) {
                launch(randomTarget(0, 70 + 25 * intensity), rand(1.0, 2.6), true);
            }
            if (p > 0.5F) {
                launchColossal();
            }
        } else if (colossalLanded && chance(0.04)) {
            launch(randomTarget(20, 90), rand(0.8, 1.6), true);
        }
        // craters keep smoking
        if (age % 6 == 0 && !craters.isEmpty()) {
            for (int i = 0; i < 2; i++) {
                Vec3 c = craters.get(random.nextInt(craters.size()));
                particles(TWRegistry.P_SMOKE.get(), c.add(rand(-1.5, 1.5), 0.8, rand(-1.5, 1.5)), 2, 0.6, 0.02);
                if (chance(0.3)) {
                    particles(TWRegistry.P_EMBER.get(), c.add(0, 0.5, 0), 3, 0.8, 0.06);
                }
            }
        }
    }

    @Override
    protected void tickEnding() {
        anchor.setShake(0);
        anchor.setA(0);
        if (age % 8 == 0 && !craters.isEmpty()) {
            Vec3 c = craters.get(random.nextInt(craters.size()));
            particles(TWRegistry.P_SMOKE.get(), c.add(0, 0.8, 0), 2, 0.6, 0.02);
        }
    }

    private Vec3 randomTarget(double minR, double maxR) {
        double a = random.nextDouble() * Math.PI * 2, r = minR + Math.sqrt(random.nextDouble()) * (maxR - minR);
        int x = Mth.floor(center.x + Math.cos(a) * r), z = Mth.floor(center.z + Math.sin(a) * r);
        return new Vec3(x + 0.5, Terrain.top(level, x, z) + 1, z + 0.5);
    }

    private void launch(Vec3 target, double size, boolean impact) {
        double elev = elevation + rand(-0.08, 0.08);
        Vec3 dir = travelDir(elev);
        // keep the spawn point inside simulated chunks: limit the horizontal offset, gain height instead
        double dist = (95 + random.nextDouble() * 20) / Math.cos(elev);
        Vec3 from = target.subtract(dir.scale(dist));
        double speed = rand(2.6, 3.4);
        float s = (float) size;
        MeteorEntity.launch(level, from, dir.scale(speed), target, MeteorEntity.KIND_METEOR, s, 0,
            impact ? m -> impact(m.position(), s) : m -> {});
    }

    private void launchColossal() {
        colossalLaunched = true;
        anchor.setA(1);
        Vec3 target;
        if (viewer != null) {
            Vec3 toCenter = center.subtract(viewer);
            double d = Math.max(45, Math.min(70, Math.sqrt(toCenter.x * toCenter.x + toCenter.z * toCenter.z)));
            Vec3 flat = new Vec3(toCenter.x, 0, toCenter.z).normalize();
            int x = Mth.floor(viewer.x + flat.x * d), z = Mth.floor(viewer.z + flat.z * d);
            target = new Vec3(x + 0.5, Terrain.top(level, x, z) + 1, z + 0.5);
        } else {
            target = center;
        }
        double elev = Math.toRadians(56);
        Vec3 dir = travelDir(elev);
        Vec3 from = target.subtract(dir.scale(100 / Math.cos(elev)));
        float size = 13 + 5 * intensity;
        MeteorEntity.launch(level, from, dir.scale(0.95), target, MeteorEntity.KIND_COLOSSAL, size, 0, m -> colossalImpact(m.position(), size));
        for (ServerPlayer p : playersWithin(400)) {
            playAt(p, TWRegistry.WARNING.get(), 1.0F, 0.8F);
            playAt(p, TWRegistry.SIREN.get(), 1.0F, 1.1F);
        }
        sound(target, TWRegistry.METEOR_WHISTLE.get(), 30.0F, 0.5F);
        schedule(90, () -> sound(target, TWRegistry.METEOR_WHISTLE.get(), 30.0F, 0.45F));
        schedule(160, () -> sound(target, TWRegistry.METEOR_WHISTLE.get(), 30.0F, 0.38F));
    }

    private void impact(Vec3 at, float size) {
        float r = 2.2F + size * 1.25F * (0.8F + intensity * 0.2F);
        level.explode(null, at.x, at.y, at.z, 2.0F + size, true, Level.ExplosionInteraction.NONE);
        sound(at, TWRegistry.IMPACT.get(), 6.0F + size * 2, 0.9F + random.nextFloat() * 0.3F);
        ShockwaveEntity.spawn(level, at, 10 + size * 6, 0.2F + size * 0.12F, 0xFFB15A, 22);
        particles(TWRegistry.P_EMBER.get(), at.add(0, 1, 0), 40, 2.0, 0.35);
        particles(TWRegistry.P_SMOKE.get(), at.add(0, 2, 0), 14, 2.0, 0.06);
        particles(TWRegistry.P_DUST.get(), at.add(0, 1, 0), 18, 3.0, 0.15);
        if (terrain()) {
            crater(at, r, false);
        }
        int count = (int) (5 + size * 4);
        for (int i = 0; i < count; i++) {
            BlockState s = chance(0.5) ? Blocks.MAGMA_BLOCK.defaultBlockState() : level.getBlockState(BlockPos.containing(at).below());
            if (s.isAir()) {
                s = Blocks.BLACKSTONE.defaultBlockState();
            }
            double a = random.nextDouble() * Math.PI * 2, sp = rand(0.4, 1.2);
            DebrisEntity d = debris(at.add(0, 1.5, 0), s, new Vec3(Math.cos(a) * sp, rand(0.7, 1.5), Math.sin(a) * sp));
            if (d != null) {
                d.setBurning(true);
                d.placeChance(0.35F);
            }
        }
        craters.add(at);
    }

    private void colossalImpact(Vec3 at, float size) {
        colossalLanded = true;
        anchor.setA(0);
        anchor.fireEvent();
        float r = 15 + 6 * intensity;
        level.explode(null, at.x, at.y, at.z, 9.0F, true, Level.ExplosionInteraction.NONE);
        ShockwaveEntity.spawn(level, at.add(0, 1, 0), 150, 1.6F, 0xFFD39A, 70);
        sound(at, TWRegistry.IMPACT.get(), 40.0F, 0.5F);
        sound(at, TWRegistry.SHOCKWAVE.get(), 40.0F, 0.6F);
        sound(at, TWRegistry.SUPERNOVA.get(), 25.0F, 1.3F);
        particles(TWRegistry.P_EMBER.get(), at.add(0, 3, 0), 200, 8.0, 0.8);
        particles(TWRegistry.P_SMOKE.get(), at.add(0, 6, 0), 80, 10.0, 0.15);
        particles(TWRegistry.P_DUST.get(), at.add(0, 2, 0), 120, 16.0, 0.4);
        if (terrain()) {
            crater(at, r, true);
        }
        for (int i = 0; i < 70; i++) {
            double a = random.nextDouble() * Math.PI * 2, sp = rand(0.9, 2.6);
            BlockState s = switch (random.nextInt(4)) {
                case 0 -> Blocks.MAGMA_BLOCK.defaultBlockState();
                case 1 -> Blocks.BLACKSTONE.defaultBlockState();
                case 2 -> Blocks.OBSIDIAN.defaultBlockState();
                default -> Blocks.COARSE_DIRT.defaultBlockState();
            };
            DebrisEntity d = debris(at.add(rand(-4, 4), 3, rand(-4, 4)), s, new Vec3(Math.cos(a) * sp, rand(1.0, 2.4), Math.sin(a) * sp));
            if (d != null) {
                d.setBurning(true);
                d.placeChance(0.5F);
            }
        }
        for (Entity e : entitiesWithin(at, 90)) {
            Vec3 off = e.position().subtract(at);
            double d = Math.max(1, off.length());
            double f = Math.max(0, 1 - d / 90);
            Vec3 dir = new Vec3(off.x, Math.abs(off.y) + 4, off.z).normalize();
            push(e, dir.x * 3.2 * f, 0.6 + dir.y * 1.2 * f, dir.z * 3.2 * f);
            if (e instanceof LivingEntity living && d < 40) {
                living.hurtServer(level, level.damageSources().explosion(null, null), (float) (14 * (1 - d / 40)));
                living.igniteForSeconds(6);
            }
        }
    }

    private void crater(Vec3 at, float r, boolean big) {
        int cx = Mth.floor(at.x), cz = Mth.floor(at.z);
        int ri = (int) Math.ceil(r * 1.35);
        int baseY = Mth.floor(at.y) - 1;
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dz = -ri; dz <= ri; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz) + random.nextDouble() * 0.6;
                int x = cx + dx, z = cz + dz;
                if (!Terrain.loaded(level, x, z)) {
                    continue;
                }
                int top = Terrain.top(level, x, z);
                if (d <= r) {
                    double t = d / r;
                    int depth = (int) (r * 0.62 * Math.sqrt(1 - t * t)) + 1;
                    int floor = Math.min(top, baseY) - depth;
                    for (int y = Math.max(top, baseY + 2); y > floor; y--) {
                        blocks.set(x, y, z, Blocks.AIR.defaultBlockState(), BlockQueue.FAST);
                    }
                    BlockState lining;
                    if (t < 0.25) {
                        lining = chance(0.55) ? Blocks.LAVA.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState();
                    } else if (t < 0.7) {
                        lining = chance(0.6) ? Blocks.MAGMA_BLOCK.defaultBlockState() : chance(0.5) ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
                    } else {
                        lining = chance(0.5) ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
                    }
                    blocks.set(x, floor, z, lining, BlockQueue.FAST);
                    blocks.set(x, floor - 1, z, Blocks.BASALT.defaultBlockState(), BlockQueue.FAST);
                } else if (d <= r * 1.35) {
                    // ejecta rim
                    int raise = big ? (int) (3 * (1 - (d - r) / (r * 0.35))) + 1 : 1;
                    for (int y = top + 1; y <= top + raise; y++) {
                        blocks.set(x, y, z, chance(0.6) ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState(), BlockQueue.FAST);
                    }
                    if (chance(big ? 0.25 : 0.12)) {
                        blocks.set(x, top + raise + 1, z, Blocks.FIRE.defaultBlockState(), BlockQueue.PHYSICS);
                    }
                }
            }
        }
    }
}
