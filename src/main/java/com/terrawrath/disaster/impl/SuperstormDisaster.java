package com.terrawrath.disaster.impl;

import com.terrawrath.disaster.BlockQueue;
import com.terrawrath.disaster.Disaster;
import com.terrawrath.disaster.DisasterType;
import com.terrawrath.disaster.Terrain;
import com.terrawrath.entity.BoltEntity;
import com.terrawrath.registry.TWRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * "Black Sky" — a rotating violet-black supercell with nonstop branching lightning and gale winds.
 */
public class SuperstormDisaster extends Disaster {
    private static final int BOLT_COLOR = 0xE4D6FF;
    private final boolean wasRaining;
    private final boolean wasThundering;
    private double windAngle;
    private float wind;
    private int nextMega = 120;
    private final float cloudHeight;

    public SuperstormDisaster(ServerLevel level, Vec3 center, float intensity, int warnTicks, boolean natural) {
        super(level, DisasterType.SUPERSTORM, center, intensity, warnTicks, natural);
        this.wasRaining = level.isRaining();
        this.wasThundering = level.isThundering();
        this.windAngle = random.nextDouble() * Math.PI * 2;
        this.cloudHeight = (float) Math.max(center.y + 62, level.getSeaLevel() + 75);
    }

    @Override
    protected double alertRadius() {
        return 300;
    }

    @Override
    protected void onStart() {
        anchor.setRadius(110 + 40 * intensity);
        anchor.setC(cloudHeight);
        level.getServer().setWeatherParameters(0, warnTicks + activeTicks + endTicks + 200, true, true);
    }

    @Override
    protected void tickWarning() {
        float p = phaseTick / (float) warnTicks;
        wind = 0.3F * p;
        updateWind();
        anchor.setShake(0.05F * p);
        if (p > 0.4F && chance(0.03)) {
            strike(randomPoint(140), false, 0.6F);
        }
        blow(0.4 * p);
    }

    @Override
    protected void onActivate() {
        anchor.fireEvent();
        megaStrike(center);
    }

    @Override
    protected void tickActive() {
        float p = phaseTick / (float) activeTicks;
        wind = Math.min(1.0F, 0.3F + phaseTick / 200F) * (0.8F + 0.2F * intensity);
        updateWind();
        anchor.setShake(0.12F + 0.1F * intensity);
        double rate = 0.2 + 0.22 * intensity;
        while (rate > 0) {
            if (chance(Math.min(1, rate))) {
                Vec3 target = chance(0.6) ? nearLivingTarget() : randomPoint(80 + 25 * intensity);
                strike(target, chance(0.22), 1.0F);
            }
            rate -= 1;
        }
        if (phaseTick >= nextMega) {
            nextMega = phaseTick + 110 + random.nextInt(90);
            Vec3 c = chance(0.6) ? nearLivingTarget() : randomPoint(60);
            megaStrike(c);
        }
        blow(1.0);
    }

    @Override
    protected void tickEnding() {
        float p = phaseTick / (float) endTicks;
        wind = 0.4F * (1 - p);
        updateWind();
        anchor.setShake(0);
        if (chance(0.05 * (1 - p))) {
            strike(randomPoint(140), false, 0.6F);
        }
        blow(0.5 * (1 - p));
    }

    @Override
    protected void onStop() {
        if (wasThundering) {
            level.getServer().setWeatherParameters(0, 6000, true, true);
        } else if (wasRaining) {
            level.getServer().setWeatherParameters(0, 6000, true, false);
        } else {
            level.getServer().setWeatherParameters(12000, 0, false, false);
        }
    }

    private void updateWind() {
        windAngle += 0.0025;
        anchor.setA((float) windAngle);
        anchor.setB(wind);
    }

    private Vec3 randomPoint(double radius) {
        double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * radius;
        int x = Mth.floor(center.x + Math.cos(a) * r), z = Mth.floor(center.z + Math.sin(a) * r);
        return new Vec3(x + 0.5, Terrain.top(level, x, z) + 1, z + 0.5);
    }

    private Vec3 nearLivingTarget() {
        List<LivingEntity> targets = new ArrayList<>();
        for (Entity e : entitiesWithin(center, 110)) {
            if (e instanceof LivingEntity l && level.canSeeSky(l.blockPosition())) {
                targets.add(l);
            }
        }
        if (targets.isEmpty()) {
            return randomPoint(90);
        }
        LivingEntity t = targets.get(random.nextInt(targets.size()));
        double minR = t instanceof Player ? 4.5 : 0.5;
        double a = random.nextDouble() * Math.PI * 2, r = minR + random.nextDouble() * (t instanceof Player ? 14 : 4);
        int x = Mth.floor(t.getX() + Math.cos(a) * r), z = Mth.floor(t.getZ() + Math.sin(a) * r);
        return new Vec3(x + 0.5, Terrain.top(level, x, z) + 1, z + 0.5);
    }

    private void strike(Vec3 at, boolean doubleStrike, float strength) {
        float height = (float) (cloudHeight - at.y + rand(5, 25));
        BoltEntity.strike(level, at, Math.max(40, height), BOLT_COLOR, (float) rand(1.6, 2.6) * strength, false);
        damageAt(at, strength);
        if (doubleStrike) {
            schedule(4 + random.nextInt(5), () -> {
                BoltEntity.strike(level, at, Math.max(40, height), BOLT_COLOR, 2.0F, true);
                damageAt(at, strength);
            });
        }
    }

    private void megaStrike(Vec3 c) {
        anchor.fireEvent();
        int n = 4 + Math.round(intensity * 1.5F);
        double base = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double a = base + i * Math.PI * 2 / n;
            double r = rand(6, 11);
            int x = Mth.floor(c.x + Math.cos(a) * r), z = Mth.floor(c.z + Math.sin(a) * r);
            Vec3 at = new Vec3(x + 0.5, Terrain.top(level, x, z) + 1, z + 0.5);
            int delay = i == 0 ? 0 : random.nextInt(3);
            if (delay == 0) {
                strike(at, false, 1.4F);
            } else {
                schedule(delay, () -> strike(at, false, 1.4F));
            }
        }
        strike(c, true, 1.8F);
    }

    private void damageAt(Vec3 at, float strength) {
        for (Entity e : entitiesWithin(at, 3.5)) {
            if (e instanceof LivingEntity living) {
                living.hurtServer(level, level.damageSources().lightningBolt(), 5.0F + 3.0F * strength);
                living.igniteForSeconds(5);
            }
        }
        if (terrain() && chance(0.45)) {
            BlockPos p = BlockPos.containing(at);
            BlockState below = level.getBlockState(p.below());
            if (level.getBlockState(p).isAir() && (below.ignitedByLava() || below.is(Blocks.GRASS_BLOCK) || below.is(net.minecraft.tags.BlockTags.LEAVES))) {
                blocks.set(p, Blocks.FIRE.defaultBlockState(), BlockQueue.PHYSICS);
            }
            if (below.is(Blocks.SAND) && chance(0.3)) {
                blocks.set(p.below(), Blocks.GLASS.defaultBlockState(), BlockQueue.FAST);
            }
        }
        particles(TWRegistry.P_SPARK.get(), at.add(0, 0.5, 0), 20, 0.8, 0.4);
    }

    private void blow(double strength) {
        if (strength <= 0) {
            return;
        }
        double wx = Math.cos(windAngle), wz = Math.sin(windAngle);
        for (Entity e : entitiesWithin(center, 160)) {
            if (!level.canSeeSky(e.blockPosition().above())) {
                continue;
            }
            double f = 0.028 * wind * strength * (0.7 + 0.3 * intensity);
            push(e, wx * f, 0, wz * f);
        }
    }
}
