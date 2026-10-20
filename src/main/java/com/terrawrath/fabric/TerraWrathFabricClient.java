package com.terrawrath.fabric;

import com.terrawrath.client.TWClient;
import com.terrawrath.fabric.mixin.EntityRenderersAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Fabric client entrypoint. Particles, HUD, camera, fog and ticks are hooked via mixins.
 */
public final class TerraWrathFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TWClient.init();
        TWClient.registerRenderers(new TWClient.RendererSink() {
            @Override
            public <E extends Entity> void register(EntityType<? extends E> type, EntityRendererProvider<E> provider) {
                EntityRenderersAccessor.terrawrath$register(type, provider);
            }
        });
    }
}
