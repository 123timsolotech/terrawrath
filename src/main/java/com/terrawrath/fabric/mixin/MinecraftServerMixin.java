package com.terrawrath.fabric.mixin;

import com.terrawrath.disaster.DisasterManager;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "tickServer", at = @At("TAIL"))
    private void terrawrath$tick(BooleanSupplier haveTime, CallbackInfo ci) {
        DisasterManager.tick((MinecraftServer) (Object) this);
    }

    @Inject(method = "stopServer", at = @At("TAIL"))
    private void terrawrath$stopped(CallbackInfo ci) {
        DisasterManager.serverStopped();
    }
}
