package com.terrawrath;

import com.mojang.logging.LogUtils;
import com.terrawrath.config.TWConfig;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

/**
 * Loader-independent entry point. The Forge (@Mod) and Fabric (ModInitializer) shims both call into here.
 */
public final class TerraWrath {
    public static final String MODID = "terrawrath";
    public static final Logger LOG = LogUtils.getLogger();

    /** Set by the client entrypoint; stays a no-op on dedicated servers. */
    public static ClientBridge CLIENT = ClientBridge.NOOP;

    private TerraWrath() {}

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    public static void commonInit() {
        TWConfig.load();
        LOG.info("TerraWrath armed. {} disasters loaded.", com.terrawrath.disaster.DisasterType.values().length);
    }
}
