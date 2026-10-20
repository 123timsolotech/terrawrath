package com.terrawrath.fabric;

import com.terrawrath.TerraWrath;
import com.terrawrath.registry.TWRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;

/**
 * Fabric entrypoint: registers everything straight into the vanilla registries (no Fabric API required).
 * Server hooks (tick, commands, joins) are wired with mixins in {@code com.terrawrath.fabric.mixin}.
 */
public final class TerraWrathFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        TWRegistry.bootstrap();
        // Fabric API (if present) paginates modded creative tabs; otherwise we skip the tab to avoid position clashes
        // (the module was renamed to fabric-creative-tab-api-v1 in the Mojang-named Fabric API)
        FabricLoader loader = FabricLoader.getInstance();
        boolean tabsSupported = loader.isModLoaded("fabric-creative-tab-api-v1") || loader.isModLoaded("fabric-item-group-api-v1")
            || loader.isModLoaded("fabric-api");
        TWRegistry.tabBuilder = () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0);
        for (TWRegistry.Entry<?> entry : TWRegistry.ENTRIES) {
            if (entry.registry() == Registries.CREATIVE_MODE_TAB && !tabsSupported) {
                continue;
            }
            register(entry);
        }
        TerraWrath.commonInit();
    }

    @SuppressWarnings("unchecked")
    private static <T> void register(TWRegistry.Entry<T> entry) {
        Registry<T> registry = (Registry<T>) BuiltInRegistries.REGISTRY.getValue(entry.registry().identifier());
        if (registry == null) {
            throw new IllegalStateException("TerraWrath: unknown registry " + entry.registry());
        }
        Registry.register(registry, TerraWrath.id(entry.name()), entry.create());
    }
}
