package com.terrawrath.client.particle;

import com.terrawrath.client.render.TWRenderTypes;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * One configurable particle class for everything: embers, sparks, smoke, dust, ash, rain, snow, spray, flames.
 * Colour, size and alpha animate over the particle's life; glowing kinds render additive at full brightness.
 */
public class TWParticle extends SingleQuadParticle {
    private final SpriteSet sprites;
    private boolean animate;
    private boolean additive;
    private boolean fullBright;
    private float size0 = 0.2F, size1 = 0.2F;
    private float alpha0 = 1, alpha1 = 0;
    private int color0 = 0xFFFFFF, color1 = 0xFFFFFF;
    private float rollSpeed;
    private float drag = 0.98F;
    private float lift;
    private float fadeIn = 0.08F;
    /** Live TerraWrath particles, tracked by expiry tick so evicted particles can't leak the count. */
    public static int LIVE;
    private static final int[] EXPIRING = new int[1024];
    private static long clock;
    private long expiry = -1;

    /** Advance the tracker once per client tick. */
    public static void tickTracker() {
        clock++;
        int slot = (int) (clock & 1023);
        LIVE -= EXPIRING[slot];
        EXPIRING[slot] = 0;
        if (LIVE < 0) {
            LIVE = 0;
        }
    }

    private void track() {
        if (expiry >= 0) {
            EXPIRING[(int) (expiry & 1023)]--;
            LIVE--;
        }
        expiry = clock + Math.min(1000, Math.max(1, this.lifetime + 1));
        EXPIRING[(int) (expiry & 1023)]++;
        LIVE++;
    }

    public TWParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0, sprites.get(level.getRandom()));
        this.sprites = sprites;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.hasPhysics = false;
        this.gravity = 0;
        this.friction = 1.0F;
        this.lifetime = 40;
        this.roll = this.oRoll = this.random.nextFloat() * Mth.TWO_PI;
        track();
    }

    public TWParticle color(int rgb) { this.color0 = this.color1 = rgb; return applyColor(0); }
    public TWParticle color(int start, int end) { this.color0 = start; this.color1 = end; return applyColor(0); }
    public TWParticle size(float s) { this.size0 = this.size1 = s; this.quadSize = s; return this; }
    public TWParticle size(float start, float end) { this.size0 = start; this.size1 = end; this.quadSize = start; return this; }
    public TWParticle alpha(float start, float end) { this.alpha0 = start; this.alpha1 = end; return this; }
    public TWParticle life(int ticks) { this.lifetime = Math.max(1, ticks); track(); return this; }
    public TWParticle gravity(float g) { this.gravity = g; return this; }
    public TWParticle drag(float d) { this.drag = d; return this; }
    public TWParticle lift(float l) { this.lift = l; return this; }
    public TWParticle spin(float r) { this.rollSpeed = r; return this; }
    public TWParticle rollTo(float r) { this.roll = this.oRoll = r; return this; }
    public TWParticle glow() { this.additive = true; this.fullBright = true; return this; }
    public TWParticle bright() { this.fullBright = true; return this; }
    public TWParticle animated() { this.animate = true; setSpriteFromAge(sprites); return this; }
    public TWParticle collide() { this.hasPhysics = true; return this; }
    public TWParticle fadeIn(float f) { this.fadeIn = f; return this; }

    private TWParticle applyColor(float t) {
        int c = color0 == color1 ? color0 : lerp(color0, color1, t);
        this.rCol = (c >> 16 & 255) / 255F;
        this.gCol = (c >> 8 & 255) / 255F;
        this.bCol = (c & 255) / 255F;
        return this;
    }

    private static int lerp(int a, int b, float t) {
        int r = (int) Mth.lerp(t, a >> 16 & 255, b >> 16 & 255);
        int g = (int) Mth.lerp(t, a >> 8 & 255, b >> 8 & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return r << 16 | g << 8 | bl;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.oRoll = this.roll;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        float t = this.age / (float) this.lifetime;
        this.yd -= 0.04 * this.gravity;
        this.yd += this.lift;
        this.move(this.xd, this.yd, this.zd);
        this.xd *= drag;
        this.yd *= drag;
        this.zd *= drag;
        this.roll += rollSpeed;
        applyColor(t);
        float a = Mth.lerp(t, alpha0, alpha1);
        if (fadeIn > 0 && t < fadeIn) {
            a *= t / fadeIn;
        }
        this.alpha = Mth.clamp(a, 0, 1);
        this.quadSize = Mth.lerp(t, size0, size1);
        if (animate) {
            setSpriteFromAge(sprites);
        }
    }

    @Override
    public float getQuadSize(float partialTicks) {
        float t = Mth.clamp((this.age + partialTicks) / this.lifetime, 0, 1);
        return Mth.lerp(t, size0, size1);
    }

    @Override
    protected int getLightCoords(float partialTicks) {
        return fullBright ? LightCoordsUtil.FULL_BRIGHT : super.getLightCoords(partialTicks);
    }

    @Override
    protected Layer getLayer() {
        return additive ? TWRenderTypes.ADDITIVE_LAYER : TWRenderTypes.SOFT_LAYER;
    }

    @Override
    public ParticleRenderType getGroup() {
        return ParticleRenderType.SINGLE_QUADS;
    }
}
