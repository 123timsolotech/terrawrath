package com.terrawrath.client;

import com.terrawrath.ClientBridge;
import com.terrawrath.TerraWrath;
import com.terrawrath.client.gui.ControlPanelScreen;
import com.terrawrath.client.render.BoltRenderer;
import com.terrawrath.client.render.DebrisRenderer;
import com.terrawrath.client.render.DisasterRenderer;
import com.terrawrath.client.render.MeteorRenderer;
import com.terrawrath.client.render.ShockwaveRenderer;
import com.terrawrath.registry.TWRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;

/**
 * Loader-independent client bootstrap. Platform shims call {@link #init()}, {@link #registerRenderers}
 * and forward ticks/render hooks here.
 */
public final class TWClient implements ClientBridge {
    public interface RendererSink {
        <E extends Entity> void register(EntityType<? extends E> type, EntityRendererProvider<E> provider);
    }

    private TWClient() {}

    public static void init() {
        TerraWrath.CLIENT = new TWClient();
    }

    public static void registerRenderers(RendererSink sink) {
        sink.register(TWRegistry.DISASTER.get(), DisasterRenderer::new);
        sink.register(TWRegistry.DEBRIS.get(), DebrisRenderer::new);
        sink.register(TWRegistry.METEOR.get(), MeteorRenderer::new);
        sink.register(TWRegistry.BOLT.get(), BoltRenderer::new);
        sink.register(TWRegistry.SHOCKWAVE_ENTITY.get(), ShockwaveRenderer::new);
        sink.register(TWRegistry.CAMERA.get(), NoopRenderer::new);
    }

    /** End of every client tick. */
    public static void clientTick() {
        com.terrawrath.client.render.ShaderCompat.init();
        com.terrawrath.client.particle.FX.tickBudget();
        ClientFX.tick();
        CinematicCamera.tick();
        AutoTest.tick();
    }

    @Override
    public void entityClientTick(Entity entity) {
        DisasterVisuals.tick(entity);
    }

    @Override
    public void openControlPanel(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player) {
            mc.gui.setScreen(new ControlPanelScreen());
        }
    }
}
