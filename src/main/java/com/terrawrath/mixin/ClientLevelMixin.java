package com.terrawrath.mixin;

import com.terrawrath.client.ClientFX;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds TerraWrath's atmosphere layers to the client's environment attributes, so disasters can tint the sky,
 * the sky light falling on the world, the clouds and the stars (shared by Forge and Fabric).
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "addEnvironmentAttributeLayers", at = @At("RETURN"))
    private void terrawrath$atmosphere(EnvironmentAttributeSystem.Builder builder, CallbackInfoReturnable<EnvironmentAttributeSystem.Builder> cir) {
        builder.addTimeBasedLayer(EnvironmentAttributes.SKY_COLOR, (value, tick) -> ClientFX.skyColor(value));
        builder.addTimeBasedLayer(EnvironmentAttributes.FOG_COLOR, (value, tick) -> ClientFX.fogColor(value));
        builder.addTimeBasedLayer(EnvironmentAttributes.CLOUD_COLOR, (value, tick) -> ClientFX.cloudColor(value));
        builder.addTimeBasedLayer(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, (value, tick) -> ClientFX.sunriseColor(value));
        builder.addTimeBasedLayer(EnvironmentAttributes.SKY_LIGHT_FACTOR, (value, tick) -> ClientFX.skyLightFactor(value));
        builder.addTimeBasedLayer(EnvironmentAttributes.SKY_LIGHT_COLOR, (value, tick) -> ClientFX.skyLightColor(value));
        builder.addTimeBasedLayer(EnvironmentAttributes.STAR_BRIGHTNESS, (value, tick) -> ClientFX.starBrightness(value));
    }
}
