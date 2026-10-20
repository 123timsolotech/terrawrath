package com.terrawrath.disaster.impl;

import com.terrawrath.disaster.Disaster;
import com.terrawrath.disaster.DisasterType;
import com.terrawrath.disaster.Terrain;
import com.terrawrath.entity.DebrisEntity;
import com.terrawrath.entity.ShockwaveEntity;
import com.terrawrath.registry.TWRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * SIGNATURE — "Event Horizon": the sky goes dark, a black hole ignites high above the land, drinks the terrain
 * from below in a blazing accretion disk, bends time around its core, then collapses into a supernova.
 */
public class EventHorizonDisaster extends Disaster {
    private final float horizon;      // event horizon radius
    private final float liftRadius;
    private final int groundY;
    private final List<DebrisEntity> orbiting = new ArrayList<>();
    private final int formTicks = 70;
    private final int collapseStart;
    private boolean ignited;
    // time dilation near the core (singleplayer: the whole world slows down)
    private float savedTickRate = -1;
    private float timeDebt;

    public EventHorizonDisaster(ServerLevel level, Vec3 center, float intensity, int warnTicks, boolean natural, Vec3 viewer) {
        super(level, DisasterType.EVENT_HORIZON, center, intensity, warnTicks, natural);
        this.horizon = 14.0F + 6.0F * intensity;
        this.liftRadius = 45 + 25 * intensity;
        // make sure the chunk exists before measuring the ground (otherwise the hole could spawn underground)
        level.getChunk(Mth.floor(center.x) >> 4, Mth.floor(center.z) >> 4);
        int measured = Terrain.solidGround(level, Mth.floor(center.x), Mth.floor(center.z));
        this.groundY = measured < level.getMinY() + 5 ? Mth.floor(center.y) - 1 : measured;
        double holeY = Math.min(groundY + 80 + 25 * intensity, level.getMaxY() - horizon - 20);
        this.center = new Vec3(center.x, holeY, center.z);
        this.collapseStart = (int) (activeTicks * 0.8);
    }

    @Override
    protected double alertRadius() {
        return 480;
    }

    @Override
    protected void onStart() {
        anchor.setRadius(horizon * 6);
        anchor.setA(0);
        anchor.setB(0);
        anchor.setC(0);
        anchor.setD(liftRadius);
    }

    @Override
    protected void tickWarning() {
        float p = phaseTick / (float) warnTicks;
        anchor.setA(horizon * 0.12F * p * p);
        anchor.setShake(0.04F + 0.12F * p);
        if (phaseTick % 5 == 0) {
            double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * liftRadius;
            int x = Mth.floor(center.x + Math.cos(a) * r), z = Mth.floor(center.z + Math.sin(a) * r);
            particles(TWRegistry.P_GLOW.get(), new Vec3(x, Terrain.top(level, x, z) + 1.5, z), 2, 0.5, 0.02);
        }
        if (p > 0.5F) {
            pull(0.25 * (p - 0.5) / 0.5);
        }
    }

    @Override
    protected void onActivate() {
        anchor.fireEvent();
        ignited = true;
        sound(center, TWRegistry.SHOCKWAVE.get(), 16.0F, 0.5F);
        ShockwaveEntity.spawn(level, center, 110, 0.6F, 0xC9A0FF, 40);
    }

    @Override
    protected void tickActive() {
        float form = Mth.clamp(phaseTick / (float) formTicks, 0, 1);
        float collapse = phaseTick > collapseStart ? (phaseTick - collapseStart) / (float) (activeTicks - collapseStart) : 0;
        float radius = horizon * (0.12F + 0.88F * form * form * (3 - 2 * form)) * (1 - 0.85F * collapse * collapse);
        anchor.setA(radius);
        anchor.setB(collapse);
        anchor.setC(Mth.clamp(collapse * 1.4F, 0, 1));
        anchor.setShake(0.22F + 0.2F * intensity + collapse * 0.6F);
        orbiting.removeIf(Entity::isRemoved);
        if (terrain() && collapse < 0.9F && form > 0.3F) {
            devour((int) ((16 + 22 * intensity) * Math.min(1, form * 1.5)));
        }
        pull(1.0 + collapse * 0.8);
        dilateTime(radius);
        if ((Boolean.getBoolean("terrawrath.debug") || !System.getProperty("terrawrath.autotest", "").isEmpty()) && phaseTick % 100 == 0) {
            int cx = Mth.floor(center.x), cz = Mth.floor(center.z);
            com.terrawrath.TerraWrath.LOG.info("[debug] horizon t={} form={} ignited={} orbiting={} ground={} top={} topBlock={} survivors={}", phaseTick, form, ignited, orbiting.size(),
                groundY, Terrain.top(level, cx, cz), level.getBlockState(new BlockPos(cx, Terrain.top(level, cx, cz), cz)),
                level.players().stream().filter(p -> !p.isCreative() && !p.isSpectator()).map(p -> p.position().toString()).toList());
        }
        if (collapse > 0 && phaseTick % 20 == 0) {
            sound(center, TWRegistry.CRACK.get(), 10.0F, 0.4F + collapse * 0.5F);
        }
    }

    @Override
    protected void onEnding() {
        restoreTime();
        // SUPERNOVA
        anchor.fireEvent();
        anchor.setA(0);
        anchor.setC(1);
        ShockwaveEntity.spawn(level, center, 320, 3.2F, 0xF4EEFF, 70);
        ShockwaveEntity.spawn(level, new Vec3(center.x, groundY + 1, center.z), 200, 0.0F, 0xFFB46B, 60);
        sound(center, TWRegistry.SUPERNOVA.get(), 40.0F, 1.0F);
        sound(center, TWRegistry.IMPACT.get(), 40.0F, 0.5F);
        for (ServerPlayer p : playersWithin(400)) {
            playAt(p, TWRegistry.SUPERNOVA.get(), 0.8F, 1.0F);
        }
        for (DebrisEntity d : orbiting) {
            if (!d.isRemoved()) {
                Vec3 out = d.position().subtract(center);
                Vec3 dir = out.lengthSqr() < 1e-3 ? new Vec3(1, 0, 0) : out.normalize();
                double sp = rand(1.8, 3.4);
                d.setBurning(true);
                d.release(new Vec3(dir.x * sp, Math.abs(dir.y) * sp + rand(0.4, 1.2), dir.z * sp));
            }
        }
        orbiting.clear();
        for (Entity e : entitiesWithin(center, 200)) {
            Vec3 off = e.position().subtract(center);
            double d = Math.max(1, off.length());
            double f = Math.max(0, 1 - d / 200);
            Vec3 dir = off.normalize();
            push(e, dir.x * 3.0 * f, 0.5 + Math.abs(dir.y) * f, dir.z * 3.0 * f);
            if (e instanceof LivingEntity living && d < 60) {
                living.hurtServer(level, level.damageSources().magic(), (float) (12 * (1 - d / 60)));
            }
        }
        particles(TWRegistry.P_SPARK.get(), center, 300, 16, 1.8);
        particles(TWRegistry.P_GLOW.get(), center, 140, 8, 1.2);
    }

    @Override
    protected void tickEnding() {
        float p = phaseTick / (float) endTicks;
        anchor.setC(1 - p);
        anchor.setShake(0.6F * (1 - p) * (1 - p));
    }

    @Override
    protected void onStop() {
        restoreTime();
    }

    private void devour(int samples) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < samples; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = Math.pow(random.nextDouble(), 0.7) * liftRadius;
            int x = Mth.floor(center.x + Math.cos(a) * r), z = Mth.floor(center.z + Math.sin(a) * r);
            if (!Terrain.loaded(level, x, z)) {
                continue;
            }
            int y = Terrain.top(level, x, z);
            if (y > center.y - horizon - 4 || y < groundY - 45) {
                continue;
            }
            p.set(x, y, z);
            BlockState s = level.getBlockState(p);
            if (s.isAir() || Terrain.protectedBlock(level, p, s)) {
                continue;
            }
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
            if (Terrain.isWater(s)) {
                particles(TWRegistry.P_SPRAY.get(), new Vec3(x + 0.5, y + 1, z + 0.5), 3, 0.5, 0.2);
                continue;
            }
            if (chance(0.85)) {
                DebrisEntity d = DebrisEntity.spawn(level, new Vec3(x + 0.5, y + 0.5, z + 0.5), Terrain.debrisLook(s), Vec3.ZERO, 1.0F);
                if (d != null) {
                    double dx = x + 0.5 - center.x, dz = z + 0.5 - center.z;
                    double rr = Math.max(3, Math.sqrt(dx * dx + dz * dz));
                    d.maxLife(1600).placeChance(0.4F);
                    // lifted off the ground below, then drawn up and around into the disk
                    d.orbit(anchor, rr, Math.atan2(dz, dx), y - center.y, rand(0.02, 0.045), -rand(0.04, 0.09), 0, 0, 2.0, true);
                    orbiting.add(d);
                }
            } else {
                particles(TWRegistry.P_DUST.get(), new Vec3(x + 0.5, y + 1, z + 0.5), 3, 0.5, 0.1);
            }
        }
    }

    private void pull(double strength) {
        double range = 230 + 50 * intensity;
        float radius = Math.max(1, anchor.a());
        for (Entity e : entitiesWithin(center, range)) {
            Vec3 off = center.subtract(e.position().add(0, e.getBbHeight() / 2, 0));
            double d = Math.max(0.5, off.length());
            if (d > range) {
                continue;
            }
            Vec3 dir = off.scale(1 / d);
            double f = Math.pow(1 - d / range, 0.6);
            boolean survivor = e instanceof Player p && !p.isCreative() && !p.isSpectator();
            double tx = -dir.z, tz = dir.x;
            if (survivor) {
                // players in survival are dragged off the ground and up into the singularity
                Player player = (Player) e;
                double ramp = ignited ? Mth.clamp(phaseTick / 240.0, 0.2, 1.0) : 0.1;
                double accel = (0.035 + 0.08 * intensity * f) * strength * ramp;
                Vec3 v = player.getKnownMovement().add(dir.scale(accel)).add(tx * 0.015 * f * ramp, 0, tz * 0.015 * f * ramp);
                if (ignited && dir.y > 0.05) {
                    // lifted off the ground only while below the core
                    v = v.add(0, 0.105 * ramp * Math.min(1, dir.y * 2.5), 0);
                    if (age % 4 == 0) {
                        tearOpenAbove(player);
                    }
                }
                if (ignited && d < radius * 3.5) {
                    // captured: spiral in instead of overshooting
                    v = v.scale(0.86).add(dir.scale(0.14));
                }
                double sp = v.length();
                if (sp > 1.4) {
                    v = v.scale(1.4 / sp);
                }
                player.setDeltaMovement(v);
                player.needsSync = true;
                player.syncVelocity = true;
                player.resetFallDistance();
            } else if (d <= 150 + 40 * intensity) {
                double pullStrength = (0.025 + 0.06 * intensity * f) * strength;
                push(e, dir.x * pullStrength + tx * 0.02 * f * strength, dir.y * pullStrength + 0.012 * strength, dir.z * pullStrength + tz * 0.02 * f * strength);
            }
            if (ignited && d < radius + 2.5) {
                if (e instanceof ItemEntity || e instanceof ExperienceOrb) {
                    e.discard();
                } else if (e instanceof LivingEntity living && age % 4 == 0) {
                    living.hurtServer(level, level.damageSources().magic(), survivor ? 7.0F : e instanceof Player ? 4.0F : 10.0F);
                }
            }
        }
    }

    /** Trees or a roof over a survivor being lifted are ripped away and drawn into the singularity. */
    private void tearOpenAbove(Player player) {
        if (!terrain()) {
            return;
        }
        BlockPos head = BlockPos.containing(player.getEyePosition());
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = 1; dy <= 6; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    p.set(head.getX() + dx, head.getY() + dy, head.getZ() + dz);
                    BlockState s = level.getBlockState(p);
                    if (s.isAir() || Terrain.protectedBlock(level, p, s) || s.getBlock().defaultDestroyTime() > 3.0F || !s.getFluidState().isEmpty()) {
                        continue;
                    }
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                    if (chance(0.5)) {
                        DebrisEntity d = DebrisEntity.spawn(level, new Vec3(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), Terrain.debrisLook(s), Vec3.ZERO, 1.0F);
                        if (d != null) {
                            double ox = p.getX() + 0.5 - center.x, oz = p.getZ() + 0.5 - center.z;
                            d.maxLife(1200).placeChance(0.3F);
                            d.orbit(anchor, Math.max(3, Math.sqrt(ox * ox + oz * oz)), Math.atan2(oz, ox), p.getY() - center.y,
                                rand(0.02, 0.045), -rand(0.04, 0.09), 0, 0, 2.0, true);
                            orbiting.add(d);
                        }
                    }
                }
            }
        }
    }

    /**
     * Near the core time itself slows: in singleplayer the world's tick rate drops (everything moves in slow
     * motion). The disaster's own clock is advanced to compensate, so the event keeps its real-time length.
     */
    private void dilateTime(float radius) {
        MinecraftServer server = level.getServer();
        double zone = Math.max(radius * 3.5, 50);
        double closest = 0;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) {
                continue;
            }
            double d = p.getEyePosition().distanceTo(center);
            double c = Mth.clamp(1 - (d - radius) / (zone - radius), 0, 1);
            closest = Math.max(closest, c);
        }
        if (server == null || !server.isSingleplayer()) {
            return;
        }
        ServerTickRateManager trm = server.tickRateManager();
        if (closest <= 0.001) {
            restoreTime();
            return;
        }
        if (savedTickRate < 0) {
            savedTickRate = trm.tickrate();
        }
        float rate = (float) (savedTickRate * (1 - 0.7 * Math.pow(closest, 1.2)));
        rate = Math.max(5.0F, rate);
        if (Math.abs(trm.tickrate() - rate) > 0.4F) {
            trm.setTickRate(rate);
        }
        // keep the event on schedule in real time
        timeDebt += savedTickRate / trm.tickrate() - 1;
        while (timeDebt >= 1 && phaseTick < activeTicks - 1) {
            phaseTick++;
            timeDebt -= 1;
        }
    }

    private void restoreTime() {
        if (savedTickRate > 0) {
            MinecraftServer server = level.getServer();
            if (server != null) {
                server.tickRateManager().setTickRate(savedTickRate);
            }
            savedTickRate = -1;
        }
        timeDebt = 0;
    }
}
