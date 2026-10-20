package com.terrawrath.fabric.mixin;

import com.terrawrath.client.ClientFX;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void terrawrath$fog(Camera camera, int renderDistance, DeltaTracker deltaTracker, float darken, ClientLevel level,
                                CallbackInfoReturnable<FogData> cir) {
        ClientFX.applyFog(cir.getReturnValue());
    }
}
