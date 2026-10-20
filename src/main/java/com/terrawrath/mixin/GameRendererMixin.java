package com.terrawrath.mixin;

import com.terrawrath.client.gui.LensEffect;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Grabs the finished frame (world, post effects and any shader pack) right before the GUI is drawn, so the
 * gravitational lens tiles in the HUD can sample the current frame.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"), require = 0)
    private void terrawrath$grabForLens(CallbackInfo ci) {
        LensEffect.grabFrame();
    }
}
