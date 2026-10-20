package com.terrawrath.client.render;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.terrawrath.TerraWrath;
import java.lang.reflect.Method;

/**
 * Shader-pack support. Iris (and its Forge port) only knows how to run vanilla render pipelines through a
 * shader pack; mods register their own through Iris' public API. This does that by reflection, so TerraWrath
 * keeps working with no extra dependency when Iris is absent.
 */
public final class ShaderCompat {
    private static boolean done;
    private static Object iris;
    private static Method packInUse;
    private static Method shadowPass;

    private ShaderCompat() {}

    public static void init() {
        if (done) {
            return;
        }
        done = true;
        Class<?> api;
        Class<?> program;
        try {
            api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            program = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
        } catch (ClassNotFoundException e) {
            return;
        }
        try {
            iris = api.getMethod("getInstance").invoke(null);
            packInUse = api.getMethod("isShaderPackInUse");
            shadowPass = api.getMethod("isRenderingShadowPass");
            Method assign = api.getMethod("assignPipeline", RenderPipeline.class, program);
            int n = 0;
            n += assign(iris, assign, program, TWRenderTypes.GLOW_PIPELINE, "EMISSIVE_ENTITIES");
            n += assign(iris, assign, program, TWRenderTypes.SHEER_PIPELINE, "ENTITIES_TRANSLUCENT");
            n += assign(iris, assign, program, TWRenderTypes.SMOKE_PIPELINE, "ENTITIES_TRANSLUCENT");
            n += assign(iris, assign, program, TWRenderTypes.BEAM_PIPELINE, "BASIC");
            n += assign(iris, assign, program, TWRenderTypes.ADDITIVE_PARTICLE_PIPELINE, "PARTICLES_TRANSLUCENT");
            n += assign(iris, assign, program, TWRenderTypes.SOFT_PARTICLE_PIPELINE, "PARTICLES_TRANSLUCENT");
            try {
                Class<?> shadowProgram = Class.forName("net.irisshaders.iris.api.v0.IrisShadowProgram");
                Method assignShadow = api.getMethod("assignPipelineShadow", RenderPipeline.class, shadowProgram);
                assign(iris, assignShadow, shadowProgram, TWRenderTypes.GLOW_PIPELINE, "SHADOW_LIGHTNING");
                assign(iris, assignShadow, shadowProgram, TWRenderTypes.BEAM_PIPELINE, "SHADOW_LIGHTNING");
                assign(iris, assignShadow, shadowProgram, TWRenderTypes.SHEER_PIPELINE, "SHADOW_TRANSLUCENT");
                assign(iris, assignShadow, shadowProgram, TWRenderTypes.SMOKE_PIPELINE, "SHADOW_TRANSLUCENT");
            } catch (ReflectiveOperationException ignored) {
                // older Iris without shadow assignment
            }
            TerraWrath.LOG.info("TerraWrath: registered {} render pipelines with the Iris shader API", n);
        } catch (Throwable t) {
            TerraWrath.LOG.warn("TerraWrath: could not register render pipelines with Iris: {}", t.toString());
        }
    }

    /** True while a shader pack is active (Iris/Oculus). */
    public static boolean shaderPack() {
        return call(packInUse);
    }

    /** True while Iris renders its shadow map: TerraWrath's glowing sky geometry casts no shadows. */
    public static boolean shadowPass() {
        return call(shadowPass);
    }

    private static boolean call(Method m) {
        if (m == null) {
            return false;
        }
        try {
            return (Boolean) m.invoke(iris);
        } catch (Throwable t) {
            return false;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int assign(Object iris, Method assign, Class<?> program, RenderPipeline pipeline, String name) {
        try {
            Object value = Enum.valueOf((Class<? extends Enum>) program, name);
            assign.invoke(iris, pipeline, value);
            return 1;
        } catch (Throwable t) {
            TerraWrath.LOG.warn("TerraWrath: Iris program {} unavailable for {}: {}", name, pipeline.getLocation(), t.toString());
            return 0;
        }
    }
}
