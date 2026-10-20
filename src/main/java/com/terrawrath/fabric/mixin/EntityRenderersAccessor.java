package com.terrawrath.fabric.mixin;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EntityRenderers.class)
public interface EntityRenderersAccessor {
    @Invoker("register")
    static <T extends Entity> void terrawrath$register(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
        throw new AssertionError();
    }
}
