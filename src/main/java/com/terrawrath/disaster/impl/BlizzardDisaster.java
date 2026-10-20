package com.terrawrath.disaster.impl;

import com.terrawrath.disaster.BlockQueue;
import com.terrawrath.disaster.Disaster;
import com.terrawrath.disaster.DisasterType;
import com.terrawrath.disaster.Terrain;
import com.terrawrath.entity.DebrisEntity;
import com.terrawrath.registry.TWRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * "Whiteout" — a visible wall of snow rolls outward, freezing everything it swallows.
 */
public class BlizzardDisaster extends Disaster {
    private final float maxRadius;
    private final float frontSpeed;
    private float radius;
    private float lastRadius;
    private double windAngle;
    private int nextSpike = 40;

    public BlizzardDisaster(ServerLevel level, Vec3 center, float intensity, int warnTicks, boolean natural) {
        super(level, DisasterType.BLIZZARD, center, intensity, warnTicks, natural);
        this.maxRadius = 105 + 55 * intensity;
        this.frontSpeed = 0.42F + 0.12F * intensity;
        this.windAngle = random.nextDouble() * Math.PI * 2;
    }

    @Override
    protected void onStart() {
        anchor.setRadius(maxRadius);
        anchor.setA(0);
        anchor.setB(maxRadius);
        anchor.setC((float) windAngle);
    }

    @Override
    protected void tickWarning() {
        anchor.setShake(0.02F);
        anchor.setA(0);
    }

    @Override
    protected void onActivate() {
        sound(center, TWRegistry.ICE_BURST.get(), 6.0F, 0.6F);
    }

    @Override
    protected void tickActive() {
        lastRadius = radius;
        if (radius < 18) {
            radius = 18;
        }
        radius = Math.min(maxRadius, radius + frontSpeed);
        windAngle += 0.002;
        anchor.setA(radius);
        anchor.setC((float) windAngle);
        anchor.setShake(0.06F);
        if (terrain()) {
            freezeRing(lastRadius, radius);
            accumulate((int) (40 + 40 * intensity));
            if (phaseTick >= nextSpike && radius > 20) {
                nextSpike = phaseTick + (int) (26 / (0.6 + intensity * 0.6)) + random.nextInt(20);
                spike();
            }
        }
        freezeEntities(1.0);
    }

    @Override
    protected void tickEnding() {
        anchor.setShake(0);
        freezeEntities(1 - phaseTick / (float) endTicks);
    }

    private void freezeRing(float r0, float r1) {
        if (r1 <= r0) {
            return;
        }
        double mid = (r0 + r1) / 2;
        int steps = (int) Math.max(24, mid * Math.PI * 2 * 1.2);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < steps; i++) {
            double a = i / (double) steps * Math.PI * 2;
            double r = r0 + random.nextDouble() * (r1 - r0 + 0.8);
            int x = Mth.floor(center.x + Math.cos(a) * r), z = Mth.floor(center.z + Math.sin(a) * r);
            if (!Terrain.loaded(level, x, z)) {
                continue;
            }
            int y = Terrain.top(level, x, z);
            p.set(x, y, z);
            BlockState s = level.getBlockState(p);
            if (Terrain.isWater(s)) {
                blocks.set(p.immutable(), chance(0.15) ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.ICE.defaultBlockState(), BlockQueue.FAST);
            } else if (s.is(Blocks.LAVA)) {
                blocks.set(p.immutable(), Blocks.OBSIDIAN.defaultBlockState(), BlockQueue.FAST);
            } else {
                addSnow(p, s, 1 + random.nextInt(3));
            }
        }
    }

    private void accumulate(int samples) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < samples; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * radius;
            int x = Mth.floor(center.x + Math.cos(a) * r), z = Mth.floor(center.z + Math.sin(a) * r);
            if (!Terrain.loaded(level, x, z)) {
                continue;
            }
            int y = Terrain.top(level, x, z);
            p.set(x, y, z);
            BlockState s = level.getBlockState(p);
            if (Terrain.isWater(s)) {
                blocks.set(p.immutable(), Blocks.ICE.defaultBlockState(), BlockQueue.FAST);
                continue;
            }
            int ground = Terrain.ground(level, x, z);
            if (y - ground > 3 && !s.is(BlockTags.LEAVES)) {
                continue; // deep enough here
            }
            addSnow(p, s, 1 + random.nextInt(2));
        }
    }

    private void addSnow(BlockPos.MutableBlockPos top, BlockState s, int layers) {
        if (s.is(Blocks.SNOW)) {
            int cur = s.getValue(SnowLayerBlock.LAYERS);
            int next = cur + layers;
            if (next >= 8) {
                blocks.set(top.immutable(), Blocks.SNOW_BLOCK.defaultBlockState(), BlockQueue.FAST);
                BlockPos above = top.above();
                if (level.getBlockState(above).isAir() && next > 8) {
                    blocks.set(above, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, Math.min(8, next - 8)), BlockQueue.FAST);
                }
            } else {
                blocks.set(top.immutable(), s.setValue(SnowLayerBlock.LAYERS, next), BlockQueue.FAST);
            }
            return;
        }
        if (s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW) || Terrain.solid(s) || s.is(BlockTags.LEAVES)) {
            BlockPos above = top.above();
            BlockState a = level.getBlockState(above);
            if (a.isAir()) {
                blocks.set(above, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, Math.min(8, layers)), BlockQueue.FAST);
            }
        } else if (s.canBeReplaced() && !s.isAir()) {
            blocks.set(top.immutable(), Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, Math.min(8, layers)), BlockQueue.FAST);
        }
    }

    private void spike() {
        double a = random.nextDouble() * Math.PI * 2;
        double r = radius * rand(0.25, 0.95);
        int x = Mth.floor(center.x + Math.cos(a) * r), z = Mth.floor(center.z + Math.sin(a) * r);
        if (!Terrain.loaded(level, x, z)) {
            return;
        }
        for (Entity e : entitiesWithin(new Vec3(x, center.y, z), 5)) {
            if (e instanceof net.minecraft.world.entity.player.Player) {
                return; // never impale the player
            }
        }
        int ground = Terrain.ground(level, x, z);
        int height = (int) (12 + random.nextDouble() * 18 * intensity + 6 * intensity);
        double baseR = rand(2.2, 3.8) * (0.8 + 0.25 * intensity);
        double tiltX = rand(-0.25, 0.25), tiltZ = rand(-0.25, 0.25);
        BlockState packed = Blocks.PACKED_ICE.defaultBlockState();
        BlockState blue = Blocks.BLUE_ICE.defaultBlockState();
        for (int y = -2; y <= height; y++) {
            final int layer = y;
            double t = Math.max(0, y) / (double) height;
            double rr = baseR * Math.pow(1 - t, 1.15) + 0.35;
            double ox = tiltX * Math.max(0, y), oz = tiltZ * Math.max(0, y);
            int delay = Math.max(1, (y + 2) / 3);
            schedule(delay, () -> {
                int ri = (int) Math.ceil(rr);
                for (int dx = -ri; dx <= ri; dx++) {
                    for (int dz = -ri; dz <= ri; dz++) {
                        double d = Math.sqrt(dx * dx + dz * dz);
                        if (d > rr) {
                            continue;
                        }
                        BlockPos p = new BlockPos(x + dx + (int) Math.round(ox), ground + layer, z + dz + (int) Math.round(oz));
                        blocks.set(p, d < rr * 0.45 ? blue : packed, BlockQueue.FAST);
                    }
                }
                if (layer % 4 == 0) {
                    particles(TWRegistry.P_SHARD.get(), new Vec3(x + ox, ground + layer + 1, z + oz), 10, rr, 0.25);
                    particles(TWRegistry.P_SNOW.get(), new Vec3(x + ox, ground + layer + 1, z + oz), 8, rr + 1, 0.1);
                }
            });
        }
        sound(new Vec3(x, ground, z), TWRegistry.ICE_BURST.get(), 6.0F, 0.6F + random.nextFloat() * 0.4F);
        sound(new Vec3(x, ground, z), TWRegistry.CRACK.get(), 4.0F, 1.2F);
        for (int i = 0; i < 5; i++) {
            DebrisEntity d = debris(new Vec3(x, ground + 1, z), Blocks.PACKED_ICE.defaultBlockState(),
                new Vec3(rand(-0.5, 0.5), rand(0.7, 1.2), rand(-0.5, 0.5)));
            if (d != null) {
                d.placeChance(0.1F);
            }
        }
    }

    private void freezeEntities(double strength) {
        if (strength <= 0) {
            return;
        }
        double wx = Math.cos(windAngle), wz = Math.sin(windAngle);
        for (Entity e : entitiesWithin(center, radius + 2)) {
            double dx = e.getX() - center.x, dz = e.getZ() - center.z;
            if (dx * dx + dz * dz > radius * radius) {
                continue;
            }
            BlockPos head = BlockPos.containing(e.getEyePosition());
            boolean exposed = level.canSeeSky(head);
            if (exposed) {
                push(e, wx * 0.012 * strength, 0, wz * 0.012 * strength);
            }
            if (e instanceof LivingEntity living && exposed && living.canFreeze()) {
                boolean warm = level.getBrightness(LightLayer.BLOCK, head) >= 12 || living.isOnFire();
                if (!warm) {
                    int need = living.getTicksRequiredToFreeze();
                    living.setTicksFrozen(Math.min(need + 40, living.getTicksFrozen() + 4));
                }
            }
        }
    }
}
