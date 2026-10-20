package com.terrawrath;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Lets common code (entities, items) reach client-only code without loading client classes on a server.
 */
public interface ClientBridge {
    ClientBridge NOOP = new ClientBridge() {};

    /** Client-side tick of any TerraWrath entity (particles, sounds, local effects). */
    default void entityClientTick(Entity entity) {}

    /** Called when the Disaster Control Panel is used on the client. */
    default void openControlPanel(Player player) {}
}
