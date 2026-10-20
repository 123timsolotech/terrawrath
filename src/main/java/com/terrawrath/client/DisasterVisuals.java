package com.terrawrath.client;

import com.terrawrath.client.particle.FX;
import com.terrawrath.client.particle.FX.Kind;
import com.terrawrath.client.particle.TWParticle;
import com.terrawrath.client.sound.DisasterLoopSound;
import com.terrawrath.disaster.DisasterType;
import com.terrawrath.entity.BoltEntity;
import com.terrawrath.entity.DebrisEntity;
import com.terrawrath.entity.DisasterEntity;
import com.terrawrath.entity.MeteorEntity;
import com.terrawrath.entity.ShockwaveEntity;
import com.terrawrath.registry.TWRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/**
 * Client-side life of every TerraWrath entity: particle storms, looping soundscapes and one-shot flashes.
 */
public final class DisasterVisuals {
    private static final RandomSource R = RandomSource.create();

    private DisasterVisuals() {}

    public static void tick(Entity entity) {
        if (entity instanceof DisasterEntity e) {
            tickDisaster(e);
        } else if (entity instanceof DebrisEntity d) {
            tickDebris(d);
        } else if (entity instanceof MeteorEntity m) {
            tickMeteor(m);
        } else if (entity instanceof BoltEntity b) {
            tickBolt(b);
        } else if (entity instanceof ShockwaveEntity s) {
            tickShockwave(s);
        }
    }

    private static Vec3 cam() {
        return Minecraft.getInstance().gameRenderer.mainCamera().position();
    }

    private static double rnd(double a, double b) {
        return a + R.nextDouble() * (b - a);
    }

    private static int groundY(double x, double z) {
        var level = Minecraft.getInstance().level;
        return level == null ? 64 : level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
    }

    // ------------------------------------------------------------------ disasters

    private static void tickDisaster(DisasterEntity e) {
        DisasterClientState cs = DisasterClientState.of(e);
        cs.update(e);
        DisasterType type = e.disasterType();
        int phase = e.phase();
        float presence = DisasterClientState.presence(phase, e.progress());
        ensureSounds(e, cs, type);
        switch (type) {
            case TORNADO -> tornado(e, phase, presence);
            case VOLCANO -> volcano(e, phase, presence);
            case TSUNAMI -> tsunami(e, phase, presence);
            case EARTHQUAKE -> earthquake(e, phase, presence);
            case METEOR_SHOWER -> meteor(e, phase, presence);
            case SUPERSTORM -> storm(e, phase, presence);
            case BLIZZARD -> blizzard(e, phase, presence);
            case SINKHOLE -> sinkhole(e, phase, presence);
            case EVENT_HORIZON -> horizon(e, phase, presence);
        }
    }

    private static void ensureSounds(DisasterEntity e, DisasterClientState cs, DisasterType type) {
        if (cs.loop != null) {
            return;
        }
        var sm = Minecraft.getInstance().getSoundManager();
        switch (type) {
            case TORNADO -> {
                cs.loop = loop(TWRegistry.WIND_LOOP.get(), e, () -> e.position().add(0, 6, 0),
                    () -> e.phase() == DisasterEntity.WARNING ? e.progress() * 0.6F : DisasterClientState.presence(e.phase(), e.progress()) * 1.3F, 260, 0.9F);
                cs.loop2 = loop(TWRegistry.RUMBLE_LOOP.get(), e, e::position, () -> DisasterClientState.presence(e.phase(), e.progress()) * 0.7F, 200, 1.0F);
            }
            case VOLCANO -> {
                cs.loop = loop(TWRegistry.RUMBLE_LOOP.get(), e, e::position, () -> 0.55F + 0.6F * e.b(), 340, 0.75F);
                cs.loop2 = loop(TWRegistry.LAVA_LOOP.get(), e, e::position, () -> e.b() * 1.2F, 180, 1.0F);
            }
            case TSUNAMI -> {
                cs.loop = loop(TWRegistry.WAVE_ROAR_LOOP.get(), e, () -> waveNearest(e), () -> e.phase() == DisasterEntity.WARNING ? 0.25F : Mth.clamp(e.c() / 45F, 0, 1.3F), 340, 0.85F);
                cs.loop2 = loop(TWRegistry.RUMBLE_LOOP.get(), e, e::position, () -> e.shake() * 0.9F, 300, 0.7F);
            }
            case EARTHQUAKE -> cs.loop = loop(TWRegistry.RUMBLE_LOOP.get(), e, e::position, () -> 0.25F + e.shake() * 0.9F, 260, 0.85F);
            case METEOR_SHOWER -> cs.loop = loop(TWRegistry.RUMBLE_LOOP.get(), e, e::position, () -> e.a() > 0.5F ? 1.0F : 0.15F * DisasterClientState.presence(e.phase(), e.progress()), 400, 0.6F);
            case SUPERSTORM -> {
                cs.loop = loop(TWRegistry.STORM_LOOP.get(), e, () -> cam().add(0, 4, 0), () -> DisasterClientState.presence(e.phase(), e.progress()) * stormInside(e), 1000, 1.0F);
                cs.loop2 = loop(TWRegistry.WIND_LOOP.get(), e, () -> cam().add(0, 4, 0), () -> e.b() * 0.55F * stormInside(e), 1000, 1.15F);
            }
            case BLIZZARD -> cs.loop = loop(TWRegistry.BLIZZARD_LOOP.get(), e, () -> blizzardNearest(e),
                () -> DisasterClientState.presence(e.phase(), e.progress()) * (e.phase() == DisasterEntity.WARNING ? 0.4F : 1.1F), 220, 1.0F);
            case SINKHOLE -> cs.loop = loop(TWRegistry.RUMBLE_LOOP.get(), e, e::position, () -> 0.2F + e.shake() * 1.1F, 200, 0.9F);
            case EVENT_HORIZON -> {
                cs.loop = loop(TWRegistry.HOLE_LOOP.get(), e, e::position, () -> DisasterClientState.presence(e.phase(), e.progress()) * (0.5F + 0.8F * Mth.clamp(e.a() / Math.max(1, e.radius() / 6), 0, 1)), 420, 1.0F);
                cs.loop2 = loop(TWRegistry.RUMBLE_LOOP.get(), e, e::position, () -> e.shake() * 0.8F, 300, 0.6F);
            }
        }
        if (cs.loop != null) {
            sm.play(cs.loop);
        }
        if (cs.loop2 != null) {
            sm.play(cs.loop2);
        }
    }

    private static DisasterLoopSound loop(SoundEvent sound, DisasterEntity e, java.util.function.Supplier<Vec3> pos,
                                          java.util.function.Supplier<Float> loud, float range, float pitch) {
        return new DisasterLoopSound(sound, e, pos, loud, range, pitch);
    }

    private static float stormInside(DisasterEntity e) {
        Vec3 c = cam();
        double d = Math.sqrt((c.x - e.getX()) * (c.x - e.getX()) + (c.z - e.getZ()) * (c.z - e.getZ()));
        return (float) Mth.clamp(1 - (d - e.radius()) / 100, 0.1, 1);
    }

    private static Vec3 waveNearest(DisasterEntity e) {
        float ang = e.b();
        double dx = Math.cos(ang), dz = Math.sin(ang);
        Vec3 front = new Vec3(e.getX() + dx * e.a(), e.getY() + e.c() * 0.5, e.getZ() + dz * e.a());
        Vec3 c = cam();
        double px = -dz, pz = dx;
        double lat = (c.x - front.x) * px + (c.z - front.z) * pz;
        lat = Mth.clamp(lat, -e.d() / 2, e.d() / 2);
        return front.add(px * lat, 0, pz * lat);
    }

    private static Vec3 blizzardNearest(DisasterEntity e) {
        Vec3 c = cam();
        double dx = c.x - e.getX(), dz = c.z - e.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < e.a() || d < 1) {
            return c.add(0, 2, 0);
        }
        return new Vec3(e.getX() + dx / d * e.a(), c.y, e.getZ() + dz / d * e.a());
    }

    private static void tornado(DisasterEntity e, int phase, float presence) {
        float r0 = Math.max(2, e.b());
        float height = e.a();
        double x = e.getX(), y = e.getY(), z = e.getZ();
        if (phase != DisasterEntity.WARNING) {
            for (int i = 0; i < 12; i++) {
                double ang = R.nextDouble() * Math.PI * 2, r = r0 * rnd(0.9, 3.2);
                double px = x + Math.cos(ang) * r, pz = z + Math.sin(ang) * r;
                double tx = -Math.sin(ang), tz = Math.cos(ang);
                TWParticle p = FX.spawn(Kind.DUST, px, y + rnd(-0.5, 3), pz, tx * 0.7 + Math.cos(ang) * 0.25, rnd(0.05, 0.35), tz * 0.7 + Math.sin(ang) * 0.25);
                if (p != null) {
                    p.size((float) rnd(2.5, 4.5), (float) rnd(6, 10)).life(30 + R.nextInt(30)).color(0x9C8466, 0x7E6C55).alpha(0.6F * presence, 0);
                }
            }
            for (int i = 0; i < 4; i++) {
                double s = rnd(0.05, 0.6);
                double ang = R.nextDouble() * Math.PI * 2;
                double r = (r0 + (e.c() - r0) * Math.pow(s, 2.3)) * rnd(0.8, 1.3);
                double tx = -Math.sin(ang), tz = Math.cos(ang);
                TWParticle p = FX.spawn(Kind.SMOKE, x + Math.cos(ang) * r, y + s * height, z + Math.sin(ang) * r, tx * 0.9, 0.12, tz * 0.9);
                if (p != null) {
                    p.size((float) rnd(4, 7), (float) rnd(9, 14)).life(25 + R.nextInt(20)).color(0x6E665C, 0x4E4840).alpha(0.45F, 0);
                }
            }
        }
        rainAroundCamera(e, presence * 0.6F, 0.15F, 0, 14);
    }

    private static void volcano(DisasterEntity e, int phase, float presence) {
        float eruption = e.b();
        float crater = Math.max(4, e.d());
        double x = e.getX(), y = e.getY(), z = e.getZ();
        if (eruption < 0.01F) {
            if (phase == DisasterEntity.ACTIVE) {
                // the mountain rising: dust off its flanks, steam from the summit
                float h = e.a();
                float baseR = e.c() * 0.95F;
                for (int i = 0; i < 7; i++) {
                    double ang = R.nextDouble() * Math.PI * 2, t = Math.sqrt(R.nextDouble());
                    double r = t * baseR * Math.max(0.1, h / Math.max(1, e.c()));
                    double sy = y - h + h * Math.pow(1 - t, 1.45);
                    TWParticle p = FX.spawn(Kind.DUST, x + Math.cos(ang) * r, sy + 1, z + Math.sin(ang) * r, rnd(-0.1, 0.1), rnd(0.05, 0.2), rnd(-0.1, 0.1));
                    if (p != null) {
                        p.size((float) rnd(3, 5), (float) rnd(8, 12)).life(50).color(0x8A7A66, 0x6A5E50);
                    }
                }
                for (int i = 0; i < 2; i++) {
                    TWParticle p = FX.spawn(Kind.SMOKE, x + rnd(-3, 3), y + 1, z + rnd(-3, 3), 0, rnd(0.15, 0.3), 0);
                    if (p != null) {
                        p.color(0xD8D4D0, 0x8A8682).size(3, 10).alpha(0.5F, 0);
                    }
                }
            }
            return;
        }
        for (int i = 0; i < 16; i++) {
            TWParticle p = FX.spawn(Kind.EMBER, x + rnd(-crater, crater) * 0.4, y + rnd(0, 6), z + rnd(-crater, crater) * 0.4,
                rnd(-0.45, 0.45), rnd(0.6, 1.5) * eruption, rnd(-0.45, 0.45));
            if (p != null) {
                p.life(40 + R.nextInt(40)).gravity(0.09F).size((float) rnd(0.25, 0.5), 0.05F);
            }
        }
        for (int i = 0; i < 5; i++) {
            TWParticle p = FX.spawn(Kind.FLAME, x + rnd(-crater, crater) * 0.4, y + rnd(0, 4), z + rnd(-crater, crater) * 0.4, rnd(-0.1, 0.1), rnd(0.3, 0.7), rnd(-0.1, 0.1));
            if (p != null) {
                p.size((float) rnd(2, 4), 0.5F).life(14 + R.nextInt(8));
            }
        }
        // the lava fountain: big glowing gobbets hurled up and raining back down
        for (int i = 0; i < 10 * eruption; i++) {
            TWParticle p = FX.spawn(Kind.EMBER, x + rnd(-2, 2), y + rnd(0, 3), z + rnd(-2, 2),
                rnd(-0.28, 0.28), rnd(1.2, 2.3), rnd(-0.28, 0.28));
            if (p != null) {
                p.size((float) rnd(0.9, 1.8), 0.4F).life(45 + R.nextInt(25)).gravity(0.5F).drag(0.985F).color(0xFFF0B0, 0xFF5010);
            }
        }
        for (int i = 0; i < 3; i++) {
            TWParticle p = FX.spawn(Kind.SMOKE, x + rnd(-crater, crater) * 0.6, y + rnd(6, 20), z + rnd(-crater, crater) * 0.6, rnd(-0.15, 0.15), rnd(0.35, 0.6), rnd(-0.15, 0.15));
            if (p != null) {
                p.size((float) rnd(7, 12), (float) rnd(20, 30)).life(90 + R.nextInt(40)).color(0x3A3330, 0x221E1C).alpha(0.75F, 0);
            }
        }
        // ash falling around the viewer
        Vec3 c = cam();
        double dist = Math.sqrt((c.x - x) * (c.x - x) + (c.z - z) * (c.z - z));
        if (dist < 260) {
            for (int i = 0; i < 12 * eruption; i++) {
                FX.spawn(Kind.ASH, c.x + rnd(-24, 24), c.y + rnd(6, 18), c.z + rnd(-24, 24), rnd(-0.05, 0.05), -0.05, rnd(-0.05, 0.05));
            }
            if (R.nextInt(3) == 0) {
                TWParticle p = FX.spawn(Kind.EMBER, c.x + rnd(-14, 14), c.y + rnd(4, 12), c.z + rnd(-14, 14), rnd(-0.05, 0.05), -0.03, rnd(-0.05, 0.05));
                if (p != null) {
                    p.gravity(0.01F).life(80);
                }
            }
        }
    }

    private static void tsunami(DisasterEntity e, int phase, float presence) {
        float h = e.c();
        if (phase != DisasterEntity.ACTIVE || h < 3) {
            return;
        }
        float ang = e.b();
        double dx = Math.cos(ang), dz = Math.sin(ang);
        double px = -dz, pz = dx;
        double fx = e.getX() + dx * e.a(), fz = e.getZ() + dz * e.a();
        float width = e.d();
        Vec3 c = cam();
        double camLat = Mth.clamp((c.x - fx) * px + (c.z - fz) * pz, -width / 2, width / 2);
        for (int i = 0; i < 18; i++) {
            double lat = R.nextFloat() < 0.6F ? camLat + rnd(-60, 60) : rnd(-width / 2, width / 2);
            lat = Mth.clamp(lat, -width / 2, width / 2);
            double edge = 1 - Math.pow(Math.abs(lat) / (width / 2), 4);
            double bow = -Math.pow(lat / (width / 2), 2) * 22;
            double ox = fx + px * lat + -dx * (bow + 4), oz = fz + pz * lat + -dz * (bow + 4);
            double oy = e.getY() + h * edge * rnd(0.85, 1.0);
            TWParticle p = FX.spawn(Kind.SPRAY, ox, oy, oz, -dx * rnd(0.3, 0.8), rnd(0.15, 0.5), -dz * rnd(0.3, 0.8));
            if (p != null) {
                p.size((float) rnd(1.5, 3), (float) rnd(5, 8)).life(25 + R.nextInt(15));
            }
        }
        for (int i = 0; i < 6; i++) {
            double lat = camLat + rnd(-50, 50);
            double ox = fx + px * lat - dx * rnd(3, 12), oz = fz + pz * lat - dz * rnd(3, 12);
            TWParticle p = FX.spawn(Kind.SPRAY, ox, e.getY() + rnd(0, 4), oz, -dx * 0.5, rnd(0.05, 0.2), -dz * 0.5);
            if (p != null) {
                p.size(4, 9).life(30).alpha(0.6F, 0);
            }
        }
    }

    private static void earthquake(DisasterEntity e, int phase, float presence) {
        float shake = e.shake();
        if (shake < 0.15F) {
            return;
        }
        Vec3 c = cam();
        for (int i = 0; i < 8 * shake; i++) {
            double px = c.x + rnd(-30, 30), pz = c.z + rnd(-30, 30);
            TWParticle p = FX.spawn(Kind.DUST, px, groundY(px, pz) + 0.3, pz, rnd(-0.05, 0.05), rnd(0.02, 0.1), rnd(-0.05, 0.05));
            if (p != null) {
                p.size((float) rnd(1.5, 3), (float) rnd(4, 7)).alpha(0.45F, 0).life(40);
            }
        }
        for (int i = 0; i < 4 * shake; i++) {
            TWParticle p = FX.spawn(Kind.ASH, c.x + rnd(-8, 8), c.y + rnd(3, 8), c.z + rnd(-8, 8), 0, -0.1, 0);
            if (p != null) {
                p.color(0x8A7A66).gravity(0.3F);
            }
        }
    }

    private static void meteor(DisasterEntity e, int phase, float presence) {
        if (phase == DisasterEntity.WARNING && R.nextInt(4) == 0) {
            Vec3 c = cam();
            TWParticle p = FX.spawn(Kind.GLOW, c.x + rnd(-80, 80), c.y + rnd(50, 90), c.z + rnd(-80, 80), 0, 0, 0);
            if (p != null) {
                p.color(0x9AFFD8).size(0.8F, 0).life(30).alpha(0.8F * presence, 0);
            }
        }
    }

    private static void storm(DisasterEntity e, int phase, float presence) {
        float inside = stormInside(e);
        float wind = e.b();
        rainAroundCamera(e, presence * inside, wind, e.a(), 46);
        Vec3 c = cam();
        double wx = Math.cos(e.a()), wz = Math.sin(e.a());
        for (int i = 0; i < 3 * wind * inside; i++) {
            double px = c.x + rnd(-20, 20), pz = c.z + rnd(-20, 20);
            TWParticle p = FX.spawn(Kind.DUST, px, groundY(px, pz) + rnd(0.2, 2), pz, wx * rnd(0.6, 1.1) * wind, rnd(0, 0.05), wz * rnd(0.6, 1.1) * wind);
            if (p != null) {
                p.size(0.8F, 2.5F).color(0x6A6670, 0x4A4650).alpha(0.4F, 0).life(25);
            }
        }
    }

    /** Wind-driven rain streaks hugging the camera. */
    private static void rainAroundCamera(DisasterEntity e, float strength, float wind, float windAngle, int count) {
        if (strength <= 0.05F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 c = cam();
        double wx = Math.cos(windAngle) * wind * 1.1, wz = Math.sin(windAngle) * wind * 1.1;
        float yaw = mc.gameRenderer.mainCamera().yRot() * Mth.DEG_TO_RAD;
        double rx = Math.cos(yaw), rz = Math.sin(yaw);
        double screenX = wx * rx + wz * rz;
        float roll = (float) Math.atan2(screenX, 1.4);
        int n = (int) (count * strength);
        for (int i = 0; i < n; i++) {
            double px = c.x + rnd(-16, 16) - wx * 6, pz = c.z + rnd(-16, 16) - wz * 6;
            TWParticle p = FX.spawn(Kind.RAIN, px, c.y + rnd(4, 14), pz, wx, -1.4, wz);
            if (p != null) {
                p.rollTo(roll).size((float) rnd(0.3, 0.5)).life(14 + R.nextInt(6));
            }
        }
    }

    private static void blizzard(DisasterEntity e, int phase, float presence) {
        Vec3 c = cam();
        double dx = c.x - e.getX(), dz = c.z - e.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        float radius = e.a();
        double wx = Math.cos(e.c()) * 0.55, wz = Math.sin(e.c()) * 0.55;
        boolean inside = d < radius;
        int n = inside ? 60 : phase == DisasterEntity.WARNING ? (int) (10 * presence) : 0;
        for (int i = 0; i < n; i++) {
            TWParticle p = FX.spawn(Kind.SNOW, c.x + rnd(-16, 16) - wx * 8, c.y + rnd(-4, 10), c.z + rnd(-16, 16) - wz * 8,
                wx * rnd(0.7, 1.3), -rnd(0.05, 0.2), wz * rnd(0.7, 1.3));
            if (p != null) {
                p.life(40 + R.nextInt(30)).alpha(presence, 0.5F * presence);
            }
        }
        if (inside) {
            for (int i = 0; i < 4; i++) {
                TWParticle p = FX.spawn(Kind.SPRAY, c.x + rnd(-14, 14), c.y + rnd(-2, 5), c.z + rnd(-14, 14), wx, 0, wz);
                if (p != null) {
                    p.size(3, 8).alpha(0.35F * presence, 0).life(30).gravity(0);
                }
            }
        } else if (d - radius < 80 && radius > 5) {
            for (int i = 0; i < 10; i++) {
                double ang = Math.atan2(dz, dx) + rnd(-0.35, 0.35);
                double px = e.getX() + Math.cos(ang) * radius, pz = e.getZ() + Math.sin(ang) * radius;
                TWParticle p = FX.spawn(Kind.SPRAY, px, groundY(px, pz) + rnd(0, 12), pz, Math.cos(ang) * 0.45, rnd(0, 0.1), Math.sin(ang) * 0.45);
                if (p != null) {
                    p.size(3, 9).alpha(0.55F, 0).life(35).gravity(0);
                }
            }
        }
    }

    private static void sinkhole(DisasterEntity e, int phase, float presence) {
        float open = e.a();
        if (open < 1) {
            return;
        }
        float depth = e.c();
        for (int i = 0; i < 9; i++) {
            double ang = R.nextDouble() * Math.PI * 2, r = Math.sqrt(R.nextDouble()) * open;
            TWParticle p = FX.spawn(Kind.DUST, e.getX() + Math.cos(ang) * r, e.getY() - rnd(0, depth), e.getZ() + Math.sin(ang) * r,
                rnd(-0.05, 0.05), rnd(0.25, 0.6), rnd(-0.05, 0.05));
            if (p != null) {
                p.size((float) rnd(2, 4), (float) rnd(8, 12)).life(60).alpha(0.55F * presence, 0).color(0x9A8A70, 0x7A6C58);
            }
        }
    }

    private static void horizon(DisasterEntity e, int phase, float presence) {
        double x = e.getX(), y = e.getY(), z = e.getZ();
        float rh = e.a();
        float liftR = Math.max(10, e.d());
        if (phase == DisasterEntity.WARNING) {
            Vec3 c = cam();
            for (int i = 0; i < 6; i++) {
                double px = c.x + rnd(-20, 20), pz = c.z + rnd(-20, 20);
                TWParticle p = FX.spawn(Kind.GLOW, px, groundY(px, pz) + rnd(0, 2), pz, 0, rnd(0.04, 0.1), 0);
                if (p != null) {
                    p.color(0xA77BFF).size(0.25F, 0.05F).life(60).alpha(0.9F * presence, 0);
                }
            }
            return;
        }
        if (phase != DisasterEntity.ACTIVE) {
            return;
        }
        // matter streaming into the disk
        for (int i = 0; i < 14; i++) {
            double ang = R.nextDouble() * Math.PI * 2, r = rh * rnd(2, 7);
            double tx = -Math.sin(ang), tz = Math.cos(ang);
            double sp = rnd(0.5, 1.1);
            TWParticle p = FX.spawn(i % 3 == 0 ? Kind.GLOW : Kind.EMBER, x + Math.cos(ang) * r, y + rnd(-1.5, 1.5), z + Math.sin(ang) * r,
                (tx * sp - Math.cos(ang) * 0.25) , rnd(-0.02, 0.02), (tz * sp - Math.sin(ang) * 0.25));
            if (p != null) {
                p.gravity(0).drag(1.0F).life(16 + R.nextInt(10)).color(i % 2 == 0 ? 0xFFE0B0 : 0xFF8A3A, 0x9B6BFF);
            }
        }
        // debris dust lifting off the ground below
        int gy = groundY(x, z);
        for (int i = 0; i < 8; i++) {
            double ang = R.nextDouble() * Math.PI * 2, r = Math.sqrt(R.nextDouble()) * liftR;
            double px = x + Math.cos(ang) * r, pz = z + Math.sin(ang) * r;
            TWParticle p = FX.spawn(i % 2 == 0 ? Kind.DUST : Kind.GLOW, px, groundY(px, pz) + 0.5, pz,
                -Math.cos(ang) * 0.08, rnd(0.25, 0.55), -Math.sin(ang) * 0.08);
            if (p != null) {
                if (i % 2 == 0) {
                    p.size(1.5F, 4).color(0x8A7AA0, 0x5A4A70).alpha(0.5F, 0).life(60);
                } else {
                    p.color(0xC9A6FF).size(0.3F, 0.1F).life(70).alpha(1, 0);
                }
            }
        }
        if (e.b() > 0.3F) {
            for (int i = 0; i < 6; i++) {
                TWParticle p = FX.spawn(Kind.SPARK, x + rnd(-rh, rh) * 1.4, y + rnd(-rh, rh) * 1.4, z + rnd(-rh, rh) * 1.4, rnd(-0.6, 0.6), rnd(-0.6, 0.6), rnd(-0.6, 0.6));
                if (p != null) {
                    p.gravity(0).color(0xFFFFFF, 0xB98CFF);
                }
            }
        }
        if (gy < y - 2 && R.nextInt(4) == 0) {
            TWParticle p = FX.spawn(Kind.SMOKE, x + rnd(-liftR, liftR) * 0.6, gy + 1, z + rnd(-liftR, liftR) * 0.6, 0, rnd(0.3, 0.6), 0);
            if (p != null) {
                p.color(0x4A3A5A, 0x2A2035).size(4, 12).alpha(0.5F, 0);
            }
        }
    }

    // ------------------------------------------------------------------ small entities

    private static void tickDebris(DebrisEntity d) {
        if (d.burning()) {
            TWParticle f = FX.spawn(Kind.FLAME, d.getX() + rnd(-0.3, 0.3), d.getY() + 0.5, d.getZ() + rnd(-0.3, 0.3), 0, 0.05, 0);
            if (f != null) {
                f.size(1.0F, 0.2F).life(10);
            }
            if (R.nextInt(2) == 0) {
                TWParticle s = FX.spawn(Kind.SMOKE, d.getX(), d.getY() + 0.5, d.getZ(), 0, 0.05, 0);
                if (s != null) {
                    s.size(1, 3).life(30).alpha(0.4F, 0);
                }
            }
        } else if (R.nextInt(6) == 0 && d.getDeltaMovement().lengthSqr() > 0.01) {
            TWParticle s = FX.spawn(Kind.DUST, d.getX(), d.getY() + 0.5, d.getZ(), 0, 0, 0);
            if (s != null) {
                s.size(0.6F, 1.6F).life(20).alpha(0.4F, 0);
            }
        }
    }

    private static void tickMeteor(MeteorEntity m) {
        float size = m.size();
        boolean colossal = m.kind() == MeteorEntity.KIND_COLOSSAL;
        boolean bomb = m.kind() == MeteorEntity.KIND_LAVA_BOMB;
        if (m.tickCount == 1 && !bomb) {
            Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(TWRegistry.METEOR_WHISTLE.get(), SoundSource.WEATHER,
                colossal ? 1.0F : 0.7F, colossal ? 0.5F : (float) rnd(0.9, 1.3), RandomSource.create(), m.getX(), m.getY(), m.getZ()));
        }
        Vector3fc v = m.launchVelocity();
        int flames = colossal ? 10 : bomb ? 2 : 4;
        for (int i = 0; i < flames; i++) {
            double back = rnd(0, 1.5);
            TWParticle p = FX.spawn(Kind.FLAME, m.getX() - v.x() * back + rnd(-size, size) * 0.3, m.getY() - v.y() * back + rnd(-size, size) * 0.3,
                m.getZ() - v.z() * back + rnd(-size, size) * 0.3, rnd(-0.05, 0.05), rnd(-0.05, 0.05), rnd(-0.05, 0.05));
            if (p != null) {
                p.size(size * (float) rnd(0.8, 1.4), size * 0.2F).life(10 + R.nextInt(8));
            }
        }
        int smokes = colossal ? 6 : bomb ? 1 : 2;
        for (int i = 0; i < smokes; i++) {
            TWParticle p = FX.spawn(Kind.SMOKE, m.getX() - v.x() * rnd(1, 3), m.getY() - v.y() * rnd(1, 3), m.getZ() - v.z() * rnd(1, 3), 0, 0.02, 0);
            if (p != null) {
                p.size(size * 1.2F, size * (colossal ? 5 : 3.5F)).life(colossal ? 140 : 70).color(0x4A4038, 0x2A2522).alpha(0.65F, 0);
            }
        }
        if (R.nextInt(2) == 0) {
            TWParticle p = FX.spawn(Kind.EMBER, m.getX(), m.getY(), m.getZ(), rnd(-0.2, 0.2), rnd(-0.1, 0.2), rnd(-0.2, 0.2));
            if (p != null) {
                p.life(40).gravity(0.04F);
            }
        }
    }

    private static void tickBolt(BoltEntity b) {
        if (b.clientStarted) {
            return;
        }
        b.clientStarted = true;
        Minecraft mc = Minecraft.getInstance();
        Vec3 c = cam();
        double dist = c.distanceTo(b.position());
        float near = (float) Mth.clamp(1 - dist / 220, 0.05, 1);
        ClientFX.addLightning(0.55F * near * Math.min(1.5F, b.width()));
        if (dist < 30) {
            ClientFX.addFlash(0.3F * (float) (1 - dist / 30), b.color());
        }
        ClientFX.addPunch(0.25F * near);
        if (mc.level != null) {
            mc.level.setSkyFlashTime(3);
        }
        if (!b.silent()) {
            int delay = (int) Math.min(40, dist / 17);
            float vol = (float) Mth.clamp(1.2 - dist / 300, 0.25, 1.0);
            mc.getSoundManager().playDelayed(new SimpleSoundInstance(TWRegistry.THUNDER.get(), SoundSource.WEATHER, vol,
                (float) rnd(0.8, 1.1), RandomSource.create(), c.x + (b.getX() - c.x) * 0.2, c.y + (b.getY() - c.y) * 0.2, c.z + (b.getZ() - c.z) * 0.2), delay);
        }
        for (int i = 0; i < 20; i++) {
            TWParticle p = FX.spawn(Kind.SPARK, b.getX(), b.getY() + 0.3, b.getZ(), rnd(-0.5, 0.5), rnd(0.2, 0.8), rnd(-0.5, 0.5));
            if (p != null) {
                p.color(0xFFFFFF, b.color());
            }
        }
    }

    private static void tickShockwave(ShockwaveEntity s) {
        if (s.clientStarted) {
            return;
        }
        s.clientStarted = true;
        Vec3 c = cam();
        double dist = c.distanceTo(s.position());
        float near = (float) Mth.clamp(1 - dist / (s.maxRadius() * 2.5 + 40), 0.0, 1);
        if (s.power() > 0) {
            ClientFX.addFlash(Math.min(1.1F, s.power()) * (0.25F + 0.75F * near), s.color());
            ClientFX.addPunch(s.power() * 1.6F * near);
        }
    }
}
