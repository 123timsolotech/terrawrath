package com.terrawrath.disaster;

import com.terrawrath.config.TWConfig;
import com.terrawrath.entity.DebrisEntity;
import com.terrawrath.entity.DisasterEntity;
import com.terrawrath.registry.TWRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Base server-side disaster: a three-act structure (warning, active, ending), boss bar countdown,
 * sirens, batched terrain edits and helpers for pushing entities around.
 */
public abstract class Disaster {
    public final DisasterType type;
    public final ServerLevel level;
    public final float intensity;
    public final boolean natural;
    protected final RandomSource random;
    protected final BlockQueue blocks = new BlockQueue();
    protected Vec3 center;
    protected DisasterEntity anchor;

    protected int phase = DisasterEntity.WARNING;
    protected int phaseTick;
    protected int age;
    protected final int warnTicks;
    protected int activeTicks;
    protected int endTicks;

    private final ServerBossEvent bar;
    private boolean finished;
    private int lastBarSecond = -1;
    private final List<Scheduled> scheduled = new ArrayList<>();

    private record Scheduled(int at, Runnable task) {}

    protected Disaster(ServerLevel level, DisasterType type, Vec3 center, float intensity, int warnTicks, boolean natural) {
        this.level = level;
        this.type = type;
        this.center = center;
        this.intensity = intensity;
        this.natural = natural;
        this.random = RandomSource.create();
        this.warnTicks = warnTicks;
        this.activeTicks = (int) (type.activeTicks * Mth.clamp(0.7F + intensity * 0.3F, 0.8F, 1.4F));
        this.endTicks = type.endTicks;
        this.bar = new ServerBossEvent(UUID.randomUUID(), Component.empty(), type.barColor, BossEvent.BossBarOverlay.NOTCHED_10);
        this.bar.setDarkenScreen(false);
        this.bar.setCreateWorldFog(false);
    }

    // ------------------------------------------------------------------ lifecycle

    public void start() {
        anchor = new DisasterEntity(TWRegistry.DISASTER.get(), level);
        anchor.setPos(center.x, center.y, center.z);
        anchor.setDisasterType(type);
        anchor.setIntensity(intensity);
        anchor.setPhase(DisasterEntity.WARNING);
        anchor.setTimer(warnTicks);
        level.addFreshEntity(anchor);
        onStart();
        updateBarPlayers();
        for (ServerPlayer p : playersWithin(420)) {
            playAt(p, TWRegistry.WARNING.get(), 1.0F, 1.0F);
            playAt(p, TWRegistry.SIREN.get(), 0.8F, 1.0F);
        }
    }

    public final void tick() {
        if (finished) {
            return;
        }
        age++;
        phaseTick++;
        if (anchor == null || anchor.isRemoved()) {
            // chunk unloaded or entity killed: respawn the anchor so clients keep seeing it
            anchor = new DisasterEntity(TWRegistry.DISASTER.get(), level);
            anchor.setPos(center.x, center.y, center.z);
            anchor.setDisasterType(type);
            anchor.setIntensity(intensity);
            level.addFreshEntity(anchor);
        }
        if (age % 20 == 1) {
            updateBarPlayers();
        }
        if (!scheduled.isEmpty()) {
            List<Scheduled> due = new ArrayList<>();
            scheduled.removeIf(s -> {
                if (s.at <= age) {
                    due.add(s);
                    return true;
                }
                return false;
            });
            for (Scheduled s : due) {
                s.task.run();
            }
        }
        switch (phase) {
            case DisasterEntity.WARNING -> {
                tickWarning();
                if (phaseTick % 180 == 90 && phaseTick < warnTicks - 60) {
                    for (ServerPlayer p : playersWithin(420)) {
                        playAt(p, TWRegistry.SIREN.get(), 0.7F, 1.0F);
                    }
                }
                if (phaseTick >= warnTicks) {
                    setPhase(DisasterEntity.ACTIVE);
                    onActivate();
                }
            }
            case DisasterEntity.ACTIVE -> {
                tickActive();
                if (phaseTick >= activeTicks) {
                    setPhase(DisasterEntity.ENDING);
                    onEnding();
                }
            }
            case DisasterEntity.ENDING -> {
                tickEnding();
                if (phaseTick >= endTicks) {
                    finish();
                    return;
                }
            }
            default -> {}
        }
        int length = phase == DisasterEntity.WARNING ? warnTicks : phase == DisasterEntity.ACTIVE ? activeTicks : endTicks;
        anchor.setPhase(phase);
        anchor.setTimer(Math.max(0, length - phaseTick));
        anchor.setProgress(Mth.clamp(phaseTick / (float) length, 0, 1));
        updateBar(length);
    }

    protected void setPhase(int newPhase) {
        this.phase = newPhase;
        this.phaseTick = 0;
        if (anchor != null) {
            anchor.setPhase(newPhase);
        }
    }

    /** Stops immediately (Stop All button / command). */
    public void stop() {
        if (!finished) {
            finish();
        }
    }

    private void finish() {
        finished = true;
        onStop();
        bar.removeAllPlayers();
        if (anchor != null) {
            anchor.discard();
        }
    }

    public boolean isFinished() {
        return finished;
    }

    public int processBlocks(int budget) {
        return blocks.process(level, budget);
    }

    public boolean hasPendingBlocks() {
        return !blocks.isEmpty();
    }

    public Vec3 center() {
        return center;
    }

    public int phase() {
        return phase;
    }

    public DisasterEntity anchor() {
        return anchor;
    }

    /** Remaining ticks until the disaster becomes active (0 when active). */
    public int ticksUntilActive() {
        return phase == DisasterEntity.WARNING ? Math.max(0, warnTicks - phaseTick) : 0;
    }

    // ------------------------------------------------------------------ hooks

    protected void onStart() {}

    protected void tickWarning() {}

    protected void onActivate() {}

    protected abstract void tickActive();

    protected void onEnding() {}

    protected void tickEnding() {}

    protected void onStop() {}

    /** Radius used for the boss bar and survival warnings. */
    protected double alertRadius() {
        return 320;
    }

    // ------------------------------------------------------------------ boss bar

    private void updateBarPlayers() {
        List<ServerPlayer> inRange = playersWithin(alertRadius());
        for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) {
            if (!inRange.contains(p)) {
                bar.removePlayer(p);
            }
        }
        for (ServerPlayer p : inRange) {
            bar.addPlayer(p);
        }
    }

    private void updateBar(int length) {
        int remaining = Math.max(0, length - phaseTick);
        int seconds = (remaining + 19) / 20;
        if (seconds == lastBarSecond && age % 10 != 0) {
            return;
        }
        lastBarSecond = seconds;
        Component name = Component.translatable(type.nameKey()).withStyle(ChatFormatting.BOLD);
        String time = String.format("%d:%02d", seconds / 60, seconds % 60);
        Component title = switch (phase) {
            case DisasterEntity.WARNING -> Component.literal("⚠ ").withStyle(ChatFormatting.RED)
                .append(Component.translatable("terrawrath.bar.warning", name).withStyle(ChatFormatting.RED))
                .append(Component.literal("  " + time).withStyle(ChatFormatting.WHITE));
            case DisasterEntity.ACTIVE -> Component.literal("☠ ").withStyle(ChatFormatting.GOLD)
                .append(name.copy().withStyle(ChatFormatting.GOLD))
                .append(Component.literal("  " + time).withStyle(ChatFormatting.GRAY));
            default -> Component.translatable("terrawrath.bar.ending", name).withStyle(ChatFormatting.GRAY);
        };
        bar.setName(title);
        bar.setProgress(phase == DisasterEntity.WARNING ? remaining / (float) Math.max(1, warnTicks) : remaining / (float) Math.max(1, length));
        bar.setColor(phase == DisasterEntity.WARNING ? BossEvent.BossBarColor.RED : type.barColor);
    }

    // ------------------------------------------------------------------ helpers

    protected List<ServerPlayer> playersWithin(double radius) {
        List<ServerPlayer> list = new ArrayList<>();
        double r2 = radius * radius;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(center.x, p.getY(), center.z) <= r2) {
                list.add(p);
            }
        }
        return list;
    }

    protected void playAt(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, player.getX(), player.getY() + 1, player.getZ(), sound, SoundSource.WEATHER, volume, pitch);
    }

    protected void sound(Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.WEATHER, volume, pitch);
    }

    protected <T extends ParticleOptions> void particles(T particle, Vec3 pos, int count, double spread, double speed) {
        level.sendParticles(particle, true, true, pos.x, pos.y, pos.z, count, spread, spread * 0.5, spread, speed);
    }

    protected List<Entity> entitiesWithin(Vec3 c, double radius) {
        AABB box = new AABB(c.x - radius, c.y - radius, c.z - radius, c.x + radius, c.y + radius, c.z + radius);
        return level.getEntities((Entity) null, box, e -> e.isAlive() && !(e instanceof DisasterEntity) && !(e instanceof DebrisEntity)
            && !(e instanceof Player p && (p.isSpectator() || p.isCreative() && p.getAbilities().flying)));
    }

    protected static void push(Entity e, double x, double y, double z) {
        if (e instanceof Player player) {
            // players move client-side: add to the motion they actually have and send it to their client
            player.setDeltaMovement(player.getKnownMovement().add(x, y, z));
            player.syncVelocity = true;
        } else {
            e.push(x, y, z);
        }
        e.needsSync = true;
        if (e instanceof LivingEntity living) {
            living.resetFallDistance();
        }
    }

    protected int ground(int x, int z) {
        return Terrain.ground(level, x, z);
    }

    protected boolean terrain() {
        return TWConfig.VALUES.terrainDestruction;
    }

    protected DebrisEntity debris(Vec3 pos, BlockState state, Vec3 velocity) {
        return DebrisEntity.spawn(level, pos, Terrain.debrisLook(state), velocity, 1.0F);
    }

    protected boolean chance(double p) {
        return random.nextDouble() < p;
    }

    protected double rand(double min, double max) {
        return min + random.nextDouble() * (max - min);
    }

    protected BlockPos.MutableBlockPos mpos(int x, int y, int z) {
        return new BlockPos.MutableBlockPos(x, y, z);
    }

    protected void schedule(int delayTicks, Runnable task) {
        scheduled.add(new Scheduled(age + Math.max(1, delayTicks), task));
    }

    /** Lifts living things out of terrain that grew around them (volcano, ice spikes). */
    protected void unstick(Vec3 around, double radius) {
        for (Entity e : entitiesWithin(around, radius)) {
            if (!(e instanceof LivingEntity)) {
                continue;
            }
            BlockPos feet = e.blockPosition();
            BlockPos head = BlockPos.containing(e.getEyePosition());
            if (!level.getBlockState(head).getCollisionShape(level, head).isEmpty()
                || !level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) {
                int top = Terrain.ground(level, feet.getX(), feet.getZ());
                e.teleportTo(e.getX(), top + 1.05, e.getZ());
                e.needsSync = true;
            }
        }
    }
}
