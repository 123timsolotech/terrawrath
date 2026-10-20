package com.terrawrath.disaster;

import com.terrawrath.TerraWrath;
import com.terrawrath.config.TWConfig;
import com.terrawrath.disaster.impl.BlizzardDisaster;
import com.terrawrath.disaster.impl.EarthquakeDisaster;
import com.terrawrath.disaster.impl.EventHorizonDisaster;
import com.terrawrath.disaster.impl.MeteorShowerDisaster;
import com.terrawrath.disaster.impl.SinkholeDisaster;
import com.terrawrath.disaster.impl.SuperstormDisaster;
import com.terrawrath.disaster.impl.TornadoDisaster;
import com.terrawrath.disaster.impl.TsunamiDisaster;
import com.terrawrath.disaster.impl.VolcanoDisaster;
import com.terrawrath.entity.DebrisEntity;
import com.terrawrath.registry.TWRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Owns every running disaster, spreads the block-edit budget between them and schedules natural disasters.
 */
public final class DisasterManager {
    public enum Placement { FRONT, NEARBY }

    private static final List<Disaster> ACTIVE = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static long naturalCountdown = -1;
    private static final String GIVEN_TAG = "terrawrath_given_panel";

    private DisasterManager() {}

    public static List<Disaster> active() {
        return ACTIVE;
    }

    public static Disaster start(ServerPlayer player, DisasterType type, float intensity, Placement placement) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 center = placementFor(level, player, type, placement, intensity);
        return start(level, type, center, intensity, type.warnTicks, false, player.position(), placement == Placement.FRONT ? player.getLookAngle() : null);
    }

    public static Disaster start(ServerLevel level, DisasterType type, Vec3 center, float intensity, int warnTicks,
                                 boolean natural, Vec3 viewer, Vec3 viewDir) {
        intensity = Mth.clamp(intensity, 0.25F, 2.0F);
        Disaster d = switch (type) {
            case TORNADO -> new TornadoDisaster(level, center, intensity, warnTicks, natural, viewer);
            case VOLCANO -> new VolcanoDisaster(level, center, intensity, warnTicks, natural);
            case TSUNAMI -> new TsunamiDisaster(level, center, intensity, warnTicks, natural, viewer, viewDir);
            case EARTHQUAKE -> new EarthquakeDisaster(level, center, intensity, warnTicks, natural);
            case METEOR_SHOWER -> new MeteorShowerDisaster(level, center, intensity, warnTicks, natural, viewer);
            case SUPERSTORM -> new SuperstormDisaster(level, center, intensity, warnTicks, natural);
            case BLIZZARD -> new BlizzardDisaster(level, center, intensity, warnTicks, natural);
            case SINKHOLE -> new SinkholeDisaster(level, center, intensity, warnTicks, natural);
            case EVENT_HORIZON -> new EventHorizonDisaster(level, center, intensity, warnTicks, natural, viewer);
        };
        ACTIVE.add(d);
        d.start();
        TerraWrath.LOG.info("Disaster started: {} at {} intensity {}", type.id, center, intensity);
        return d;
    }

    /** Where a disaster should appear relative to the player, tuned so it frames well on camera. */
    public static Vec3 placementFor(ServerLevel level, Player player, DisasterType type, Placement placement, float intensity) {
        // the volcano must never grow on top of the player: stand well outside its base radius
        double volcanoClearance = (88 + 22 * Mth.clamp(intensity, 0.25F, 2.0F)) * 0.95 + 45;
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0E-4) {
            flat = new Vec3(0, 0, 1);
        }
        flat = flat.normalize();
        double dist;
        if (placement == Placement.FRONT) {
            dist = switch (type) {
                case TORNADO -> 70;
                case VOLCANO -> volcanoClearance;
                case TSUNAMI -> 0;
                case EARTHQUAKE -> 18;
                case METEOR_SHOWER -> 45;
                case SUPERSTORM -> 20;
                case BLIZZARD -> 80;
                case SINKHOLE -> 30;
                case EVENT_HORIZON -> 150;
            };
        } else {
            dist = switch (type) {
                case TORNADO -> 120;
                case VOLCANO -> volcanoClearance + 15;
                case TSUNAMI -> 0;
                case EARTHQUAKE -> 30;
                case METEOR_SHOWER -> 50;
                case SUPERSTORM -> 0;
                case BLIZZARD -> 50;
                case SINKHOLE -> 40;
                case EVENT_HORIZON -> 180;
            };
            double ang = RANDOM.nextDouble() * Math.PI * 2;
            flat = new Vec3(Math.cos(ang), 0, Math.sin(ang));
        }
        int x = Mth.floor(player.getX() + flat.x * dist);
        int z = Mth.floor(player.getZ() + flat.z * dist);
        level.getChunk(x >> 4, z >> 4);
        int y = Terrain.solidGround(level, x, z) + 1;
        if (y < level.getMinY() + 5) {
            y = (int) player.getY();
        }
        return new Vec3(x + 0.5, y, z + 0.5);
    }

    public static Disaster startRandom(ServerPlayer player, float intensity, Placement placement) {
        DisasterType[] all = DisasterType.values();
        return start(player, all[RANDOM.nextInt(all.length)], intensity, placement);
    }

    public static int stopAll() {
        int n = ACTIVE.size();
        for (Disaster d : ACTIVE) {
            d.stop();
        }
        ACTIVE.clear();
        return n;
    }

    public static void serverStopped() {
        stopAll();
        DebrisEntity.LIVE.set(0);
        naturalCountdown = -1;
    }

    public static void tick(MinecraftServer server) {
        if (!ACTIVE.isEmpty()) {
            List<Disaster> snapshot = new ArrayList<>(ACTIVE);
            for (Disaster d : snapshot) {
                try {
                    d.tick();
                } catch (Exception e) {
                    TerraWrath.LOG.error("Disaster {} crashed, stopping it", d.type.id, e);
                    d.stop();
                }
            }
            int budget = TWConfig.VALUES.maxBlockChangesPerTick;
            int pending = 0;
            for (Disaster d : snapshot) {
                if (d.hasPendingBlocks()) {
                    pending++;
                }
            }
            if (pending > 0) {
                int share = Math.max(64, budget / pending);
                for (Disaster d : snapshot) {
                    if (d.hasPendingBlocks()) {
                        d.processBlocks(share);
                    }
                }
            }
            ACTIVE.removeIf(Disaster::isFinished);
        }
        tickNatural(server);
    }

    private static void tickNatural(MinecraftServer server) {
        if (!TWConfig.VALUES.naturalDisasters) {
            return;
        }
        if (naturalCountdown < 0) {
            resetNaturalTimer();
        }
        if (--naturalCountdown > 0) {
            return;
        }
        resetNaturalTimer();
        if (!ACTIVE.isEmpty()) {
            return;
        }
        ServerLevel overworld = server.overworld();
        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer p : overworld.players()) {
            if (!p.isSpectator() && !p.isCreative()) {
                candidates.add(p);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        ServerPlayer target = candidates.get(RANDOM.nextInt(candidates.size()));
        DisasterType[] all = DisasterType.values();
        DisasterType type = all[RANDOM.nextInt(all.length)];
        double ang = RANDOM.nextDouble() * Math.PI * 2;
        double dist = switch (type) {
            case SUPERSTORM, TSUNAMI -> 0;
            case EARTHQUAKE, SINKHOLE -> 30 + RANDOM.nextDouble() * 30;
            default -> 90 + RANDOM.nextDouble() * 50;
        };
        int x = Mth.floor(target.getX() + Math.cos(ang) * dist);
        int z = Mth.floor(target.getZ() + Math.sin(ang) * dist);
        int y = Terrain.solidGround(overworld, x, z) + 1;
        float intensity = (float) TWConfig.VALUES.naturalIntensity;
        int warn = TWConfig.VALUES.naturalWarningSeconds * 20;
        start(overworld, type, new Vec3(x + 0.5, y, z + 0.5), intensity, warn, true, target.position(), null);
    }

    private static void resetNaturalTimer() {
        int min = TWConfig.VALUES.minMinutesBetweenDisasters;
        int max = TWConfig.VALUES.maxMinutesBetweenDisasters;
        naturalCountdown = (long) (min + RANDOM.nextDouble() * (max - min)) * 60L * 20L;
    }

    /** Dev/testing hook: make the next natural disaster arrive after {@code ticks}. */
    public static void scheduleNaturalIn(int ticks) {
        naturalCountdown = ticks;
    }

    /** Ticks until the next natural disaster (for the detector), or -1 if disabled. */
    public static long naturalCountdown() {
        return TWConfig.VALUES.naturalDisasters ? naturalCountdown : -1;
    }

    public static void onPlayerJoin(ServerPlayer player) {
        if (!TWConfig.VALUES.giveControlPanelOnFirstJoin || player.entityTags().contains(GIVEN_TAG)) {
            return;
        }
        player.addTag(GIVEN_TAG);
        ItemStack panel = new ItemStack(TWRegistry.CONTROL_PANEL.get());
        if (!player.getInventory().add(panel)) {
            player.spawnAtLocation((ServerLevel) player.level(), panel);
        }
        player.sendSystemMessage(Component.translatable("terrawrath.welcome").withStyle(ChatFormatting.GOLD));
    }

    public static boolean isOverworldLike(Level level) {
        return level.dimensionType().hasSkyLight();
    }
}
