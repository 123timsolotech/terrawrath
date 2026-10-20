package com.terrawrath.forge;

import com.terrawrath.TerraWrath;
import com.terrawrath.command.DisasterCommand;
import com.terrawrath.disaster.DisasterManager;
import com.terrawrath.registry.TWRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;

/**
 * Forge entrypoint: registers TerraWrath content through DeferredRegister and wires Forge events
 * to the loader-independent code.
 */
@Mod(TerraWrath.MODID)
public final class TerraWrathForge {
    public TerraWrathForge(FMLJavaModLoadingContext context) {
        var modBus = context.getModBusGroup();
        TWRegistry.bootstrap();
        TWRegistry.tabBuilder = () -> CreativeModeTab.builder().withTabsBefore(CreativeModeTabs.SPAWN_EGGS);

        Map<ResourceKey<?>, DeferredRegister<?>> registers = new LinkedHashMap<>();
        for (TWRegistry.Entry<?> entry : TWRegistry.ENTRIES) {
            add(registers, entry);
        }
        registers.values().forEach(r -> r.register(modBus));

        FMLCommonSetupEvent.getBus(modBus).addListener(event -> TerraWrath.commonInit());
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> DisasterManager.tick(event.server()));
        RegisterCommandsEvent.BUS.addListener(event -> DisasterCommand.register(event.getDispatcher()));
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                DisasterManager.onPlayerJoin(player);
            }
        });
        ServerStoppedEvent.BUS.addListener(event -> DisasterManager.serverStopped());

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClient.init(modBus);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> void add(Map<ResourceKey<?>, DeferredRegister<?>> registers, TWRegistry.Entry<T> entry) {
        DeferredRegister<T> reg = (DeferredRegister<T>) registers.computeIfAbsent(entry.registry(),
            key -> DeferredRegister.create((ResourceKey<? extends Registry<T>>) (ResourceKey) key, TerraWrath.MODID));
        reg.register(entry.name(), entry::create);
    }
}
