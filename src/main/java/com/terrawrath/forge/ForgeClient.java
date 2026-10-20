package com.terrawrath.forge;

import com.terrawrath.TerraWrath;
import com.terrawrath.client.ClientFX;
import com.terrawrath.client.TWClient;
import com.terrawrath.client.gui.HudOverlay;
import com.terrawrath.client.particle.FX;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;

/**
 * Forge client wiring: renderers, particle providers, HUD layer, camera shake, fog and client ticks.
 */
public final class ForgeClient {
    private ForgeClient() {}

    public static void init(BusGroup modBus) {
        TWClient.init();
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(event ->
            TWClient.registerRenderers(new TWClient.RendererSink() {
                @Override
                public <E extends net.minecraft.world.entity.Entity> void register(net.minecraft.world.entity.EntityType<? extends E> type,
                                                                                   net.minecraft.client.renderer.entity.EntityRendererProvider<E> provider) {
                    event.registerEntityRenderer(type, provider);
                }
            }));
        RegisterParticleProvidersEvent.BUS.addListener(event -> {
            for (FX.Entry e : FX.TYPES) {
                event.registerSpriteSet(e.type().get(), sprites -> FX.provider(e.kind(), sprites));
            }
        });
        AddGuiOverlayLayersEvent.BUS.addListener(event ->
            event.getLayeredDraw().add(TerraWrath.id("disaster_overlay"), HudOverlay::render));
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> TWClient.clientTick());
        ViewportEvent.ComputeCameraAngles.BUS.addListener(event -> {
            float[] s = ClientFX.cameraShake((float) event.getPartialTick());
            if (s != null) {
                event.setYaw(event.getYaw() + s[0]);
                event.setPitch(event.getPitch() + s[1]);
                event.setRoll(event.getRoll() + s[2]);
            }
        });
        ViewportEvent.ComputeFov.BUS.addListener(event -> {
            float extra = ClientFX.fovBoost((float) event.getPartialTick());
            if (extra != 0) {
                event.setFOV(event.getFOV() + extra);
            }
        });
        ViewportEvent.RenderFog.BUS.addListener(event -> {
            ClientFX.applyFog(event.getData());
            event.getColor().set(event.getData().color);
            return false;
        });
    }
}
