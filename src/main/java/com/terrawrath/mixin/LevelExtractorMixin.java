package com.terrawrath.mixin;

import com.terrawrath.entity.BoltEntity;
import com.terrawrath.entity.DisasterEntity;
import com.terrawrath.entity.MeteorEntity;
import com.terrawrath.entity.ShockwaveEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Disasters are hundreds of blocks across: never hide them just because the chunk section holding their
 * anchor point is occluded (a wave anchored at sea level, a bolt striking behind a hill...).
 */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
    private void terrawrath$alwaysVisible(Entity entity, Frustum frustum, double camX, double camY, double camZ, float partialTicks,
                                          long chunkFadeDuration, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof DisasterEntity || entity instanceof BoltEntity || entity instanceof ShockwaveEntity || entity instanceof MeteorEntity) {
            cir.setReturnValue(true);
        }
    }
}
