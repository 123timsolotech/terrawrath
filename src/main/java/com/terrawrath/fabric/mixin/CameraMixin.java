package com.terrawrath.fabric.mixin;

import com.terrawrath.client.ClientFX;
import net.minecraft.client.Camera;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Camera shake and FOV on Fabric (Forge uses ViewportEvent.ComputeCameraAngles / ComputeFov). */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private float xRot;
    @Shadow
    private float yRot;
    @Shadow
    @Final
    private Quaternionf rotation;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void terrawrath$shake(float partialTicks, CallbackInfo ci) {
        float[] s = ClientFX.cameraShake(partialTicks);
        if (s != null) {
            this.setRotation(this.yRot + s[0], this.xRot + s[1]);
            this.rotation.rotateZ(s[2] * ((float) Math.PI / 180F));
        }
    }

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void terrawrath$fov(float partialTicks, CallbackInfoReturnable<Float> cir) {
        float extra = ClientFX.fovBoost(partialTicks);
        if (extra != 0) {
            cir.setReturnValue(cir.getReturnValueF() + extra);
        }
    }
}
