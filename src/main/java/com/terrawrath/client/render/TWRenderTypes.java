package com.terrawrath.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.terrawrath.TerraWrath;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;

/**
 * TerraWrath's own render pipelines: additive glow, soft depth-free smoke and an additive particle layer.
 */
public final class TWRenderTypes {
    private static final DepthStencilState TEST_NO_WRITE = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false);
    /** Disaster geometry is sky-scale: fog it as if it were 2.5x closer so it stays visible past the render distance. */
    private static final float FAR_FOG = 0.4F;

    /** Additive (src-alpha, one), emissive, textured, double sided, no depth write. */
    public static final RenderPipeline GLOW_PIPELINE = RenderPipeline.builder(snippet("ENTITY_NO_LIGHTMAP_SNIPPET"), snippet("EYES_SNIPPET"))
        .withLocation(TerraWrath.id("pipeline/glow"))
        .withVertexShader(TerraWrath.id("core/tw_entity"))
        .withShaderDefine("TW_FOG_SCALE", FAR_FOG)
        .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
        .withDepthStencilState(TEST_NO_WRITE)
        .withCull(false)
        .build();

    /** Alpha-blended, lit by the world lightmap, double sided, no depth write (for layered smoke/cloud/water shells). */
    public static final RenderPipeline SMOKE_PIPELINE = RenderPipeline.builder(snippet("ENTITY_SNIPPET"))
        .withLocation(TerraWrath.id("pipeline/smoke"))
        .withVertexShader(TerraWrath.id("core/tw_entity"))
        .withShaderDefine("TW_FOG_SCALE", FAR_FOG)
        .withShaderDefine("ALPHA_CUTOUT", 0.01F)
        .withShaderDefine("NO_OVERLAY")
        .withShaderDefine("NO_CARDINAL_LIGHTING")
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withDepthStencilState(TEST_NO_WRITE)
        .withCull(false)
        .build();

    /** Alpha-blended, emissive (ignores light), double sided, no depth write. */
    public static final RenderPipeline SHEER_PIPELINE = RenderPipeline.builder(snippet("ENTITY_NO_LIGHTMAP_SNIPPET"), snippet("EYES_SNIPPET"))
        .withLocation(TerraWrath.id("pipeline/sheer"))
        .withVertexShader(TerraWrath.id("core/tw_entity"))
        .withShaderDefine("TW_FOG_SCALE", FAR_FOG)
        .withShaderDefine("ALPHA_CUTOUT", 0.01F)
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withDepthStencilState(TEST_NO_WRITE)
        .withCull(false)
        .build();

    /** Untextured additive geometry (bolts, rings, jets) without depth write so layers stack. */
    public static final RenderPipeline BEAM_PIPELINE = RenderPipeline.builder(snippet("LIGHTNING_SNIPPET"))
        .withLocation(TerraWrath.id("pipeline/beam"))
        .withVertexShader(TerraWrath.id("core/tw_lightning"))
        .withShaderDefine("TW_FOG_SCALE", FAR_FOG)
        .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
        .withDepthStencilState(TEST_NO_WRITE)
        .withCull(false)
        .build();

    /** Additive particles (embers, sparks, flames, glows). */
    public static final RenderPipeline ADDITIVE_PARTICLE_PIPELINE = RenderPipeline.builder(snippet("PARTICLE_SNIPPET"))
        .withLocation(TerraWrath.id("pipeline/additive_particle"))
        .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
        .withDepthStencilState(TEST_NO_WRITE)
        .build();

    /** Soft translucent particles that don't write depth (smoke, dust, ash, snow). */
    public static final RenderPipeline SOFT_PARTICLE_PIPELINE = RenderPipeline.builder(snippet("PARTICLE_SNIPPET"))
        .withLocation(TerraWrath.id("pipeline/soft_particle"))
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withDepthStencilState(TEST_NO_WRITE)
        .build();

    public static final SingleQuadParticle.Layer ADDITIVE_LAYER = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, ADDITIVE_PARTICLE_PIPELINE);
    public static final SingleQuadParticle.Layer SOFT_LAYER = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, SOFT_PARTICLE_PIPELINE);

    private static final Map<Identifier, RenderType> GLOW = new HashMap<>();
    private static final Map<Identifier, RenderType> SMOKE = new HashMap<>();
    private static final Map<Identifier, RenderType> SHEER = new HashMap<>();
    private static RenderType beam;

    private TWRenderTypes() {}

    /** Vanilla keeps these snippets private (Forge widens them); reflection works on both loaders. */
    private static RenderPipeline.Snippet snippet(String name) {
        try {
            java.lang.reflect.Field f = RenderPipelines.class.getDeclaredField(name);
            f.setAccessible(true);
            return (RenderPipeline.Snippet) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("TerraWrath: missing render snippet " + name, e);
        }
    }

    private static java.lang.reflect.Method createMethod;

    /** RenderType.create is package-private in vanilla. */
    private static RenderType createType(String name, RenderSetup setup) {
        try {
            if (createMethod == null) {
                createMethod = RenderType.class.getDeclaredMethod("create", String.class, RenderSetup.class);
                createMethod.setAccessible(true);
            }
            return (RenderType) createMethod.invoke(null, name, setup);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("TerraWrath: cannot create render type " + name, e);
        }
    }

    public static RenderType glow(Identifier texture) {
        return GLOW.computeIfAbsent(texture, t -> createType("terrawrath_glow", RenderSetup.builder(GLOW_PIPELINE)
            .withTexture("Sampler0", t, () -> RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR))
            .sortOnUpload()
            .createRenderSetup()));
    }

    public static RenderType smoke(Identifier texture) {
        return SMOKE.computeIfAbsent(texture, t -> createType("terrawrath_smoke", RenderSetup.builder(SMOKE_PIPELINE)
            .withTexture("Sampler0", t, () -> RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR))
            .useLightmap()
            .sortOnUpload()
            .createRenderSetup()));
    }

    public static RenderType sheer(Identifier texture) {
        return SHEER.computeIfAbsent(texture, t -> createType("terrawrath_sheer", RenderSetup.builder(SHEER_PIPELINE)
            .withTexture("Sampler0", t, () -> RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR))
            .sortOnUpload()
            .createRenderSetup()));
    }

    public static RenderType beam() {
        if (beam == null) {
            beam = createType("terrawrath_beam", RenderSetup.builder(BEAM_PIPELINE).sortOnUpload().createRenderSetup());
        }
        return beam;
    }

    public static Identifier tex(String name) {
        return TerraWrath.id("textures/entity/" + name + ".png");
    }
}
