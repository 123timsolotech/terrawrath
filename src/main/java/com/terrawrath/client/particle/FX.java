package com.terrawrath.client.particle;

import com.terrawrath.config.TWConfig;
import com.terrawrath.registry.Ref;
import com.terrawrath.registry.TWRegistry;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.RandomSource;

/**
 * Client particle factory with a per-tick budget so heavy disasters never tank the frame rate.
 */
public final class FX {
    public enum Kind { GLOW, EMBER, SPARK, SMOKE, DUST, ASH, RAIN, SNOW, SPRAY, FLAME, SHARD }

    private static final Map<Kind, SpriteSet> SPRITES = new HashMap<>();
    private static int budget;
    private static final RandomSource RANDOM = RandomSource.create();

    private FX() {}

    /** Every TerraWrath particle type and the style it uses. */
    public record Entry(Ref<SimpleParticleType> type, Kind kind) {}

    public static final java.util.List<Entry> TYPES = java.util.List.of(
        new Entry(TWRegistry.P_GLOW, Kind.GLOW), new Entry(TWRegistry.P_EMBER, Kind.EMBER), new Entry(TWRegistry.P_SPARK, Kind.SPARK),
        new Entry(TWRegistry.P_SMOKE, Kind.SMOKE), new Entry(TWRegistry.P_DUST, Kind.DUST), new Entry(TWRegistry.P_ASH, Kind.ASH),
        new Entry(TWRegistry.P_RAIN, Kind.RAIN), new Entry(TWRegistry.P_SNOW, Kind.SNOW), new Entry(TWRegistry.P_SPRAY, Kind.SPRAY),
        new Entry(TWRegistry.P_FLAME, Kind.FLAME), new Entry(TWRegistry.P_SHARD, Kind.SHARD));

    /** Builds the provider for one particle kind and remembers its sprite set for direct spawning. */
    public static ParticleProvider<SimpleParticleType> provider(Kind kind, SpriteSet sprites) {
        SPRITES.put(kind, sprites);
        return (options, level, x, y, z, xa, ya, za, random) -> styled(kind, level, x, y, z, xa, ya, za);
    }

    /** Called once per client tick: refills the particle budget according to the particle setting. */
    public static void tickBudget() {
        Minecraft mc = Minecraft.getInstance();
        TWParticle.tickTracker();
        ParticleStatus status = mc.options.particles().get();
        int base = switch (status) {
            case ALL -> 260;
            case DECREASED -> 120;
            case MINIMAL -> 30;
        };
        // shader packs run every particle through expensive programs: spend far fewer of them
        double mult = TWConfig.VALUES.particleMultiplier * (com.terrawrath.client.render.ShaderCompat.shaderPack() ? 0.35 : 1.0);
        budget = (int) (base * mult);
        int cap = (int) ((status == ParticleStatus.ALL ? 4500 : status == ParticleStatus.DECREASED ? 2200 : 600) * mult);
        if (TWParticle.LIVE > cap) {
            budget = 0;
        } else if (TWParticle.LIVE > cap * 0.75) {
            budget /= 3;
        }

    }

    /** True if {@code n} more particles may be spawned this tick. */
    public static boolean allow(int n) {
        if (budget <= 0) {
            return false;
        }
        budget -= n;
        return true;
    }

    public static float scale() {
        return (float) TWConfig.VALUES.particleMultiplier;
    }

    /** Creates (and adds) a particle with sensible defaults for its kind; returns it for further styling, or null. */
    public static TWParticle spawn(Kind kind, double x, double y, double z, double xd, double yd, double zd) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        SpriteSet sprites = SPRITES.get(kind);
        if (level == null || sprites == null || budget <= 0) {
            return null;
        }
        budget--;
        TWParticle p = styled(kind, level, x, y, z, xd, yd, zd);
        mc.particleEngine.add(p);
        return p;
    }

    private static TWParticle styled(Kind kind, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
        TWParticle p = new TWParticle(level, x, y, z, xd, yd, zd, SPRITES.get(kind));
        RandomSource r = RANDOM;
        switch (kind) {
            case GLOW -> p.glow().size(0.6F, 0.1F).life(20 + r.nextInt(10)).alpha(0.9F, 0).color(0xC9A6FF);
            case EMBER -> p.glow().size(0.18F + r.nextFloat() * 0.12F, 0.04F).life(30 + r.nextInt(30)).alpha(1, 0.2F)
                .color(0xFFD27A, 0xFF3A0A).gravity(0.06F).drag(0.96F);
            case SPARK -> p.glow().size(0.16F + r.nextFloat() * 0.1F, 0.02F).life(10 + r.nextInt(10)).alpha(1, 0)
                .color(0xFFFFFF, 0xFFB050).gravity(0.5F).drag(0.92F);
            case SMOKE -> p.animated().size(1.5F + r.nextFloat(), 4.5F + r.nextFloat() * 2).life(60 + r.nextInt(40)).alpha(0.55F, 0)
                .color(0x4A4441, 0x2A2725).lift(0.0015F).drag(0.95F).spin((r.nextFloat() - 0.5F) * 0.03F).fadeIn(0.15F);
            case DUST -> p.size(1.2F + r.nextFloat(), 3.5F + r.nextFloat() * 2).life(40 + r.nextInt(40)).alpha(0.5F, 0)
                .color(0xB39E80, 0x9C8970).drag(0.93F).spin((r.nextFloat() - 0.5F) * 0.04F).fadeIn(0.2F);
            case ASH -> p.size(0.08F + r.nextFloat() * 0.06F).life(90 + r.nextInt(60)).alpha(0.9F, 0.5F)
                .color(0x2E2B2A).gravity(0.03F).drag(0.97F).spin((r.nextFloat() - 0.5F) * 0.3F);
            case RAIN -> p.size(0.35F).life(18 + r.nextInt(8)).alpha(0.55F, 0.4F).color(0xB8C8E8).gravity(1.2F).drag(1.0F).collide();
            case SNOW -> p.size(0.08F + r.nextFloat() * 0.1F).life(60 + r.nextInt(50)).alpha(0.95F, 0.6F).color(0xFFFFFF)
                .gravity(0.025F).drag(0.98F).spin((r.nextFloat() - 0.5F) * 0.2F);
            case SPRAY -> p.size(0.6F + r.nextFloat() * 0.6F, 2.2F + r.nextFloat()).life(18 + r.nextInt(14)).alpha(0.75F, 0)
                .color(0xF0FAFF).gravity(0.12F).drag(0.95F);
            case FLAME -> p.glow().animated().size(0.7F + r.nextFloat() * 0.4F, 0.15F).life(12 + r.nextInt(8)).alpha(1, 0.1F)
                .color(0xFFFFFF, 0xFF6A20).drag(0.9F);
            case SHARD -> p.size(0.15F + r.nextFloat() * 0.15F).life(30 + r.nextInt(20)).alpha(1, 0.7F)
                .color(0xD8F4FF).gravity(0.6F).drag(0.96F).spin((r.nextFloat() - 0.5F) * 0.6F).collide().bright();
        }
        return p;
    }
}
