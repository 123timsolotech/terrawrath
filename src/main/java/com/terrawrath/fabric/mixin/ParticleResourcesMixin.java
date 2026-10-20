package com.terrawrath.fabric.mixin;

import com.terrawrath.TerraWrath;
import com.terrawrath.client.particle.FX;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.minecraft.client.particle.ParticleResources;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Registers TerraWrath's sprite-based particle providers on Fabric (vanilla keeps the API private). */
@Mixin(ParticleResources.class)
public abstract class ParticleResourcesMixin {
    @Inject(method = "registerProviders", at = @At("TAIL"))
    private void terrawrath$providers(CallbackInfo ci) {
        try {
            Class<?> registration = Class.forName("net.minecraft.client.particle.ParticleResources$SpriteParticleRegistration",
                false, ParticleResources.class.getClassLoader());
            Method register = ParticleResources.class.getDeclaredMethod("register", ParticleType.class, registration);
            register.setAccessible(true);
            for (FX.Entry e : FX.TYPES) {
                Object factory = Proxy.newProxyInstance(registration.getClassLoader(), new Class<?>[]{registration}, (proxy, m, args) -> {
                    if ("create".equals(m.getName())) {
                        return FX.provider(e.kind(), (SpriteSet) args[0]);
                    }
                    return switch (m.getName()) {
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> "TerraWrathParticle";
                    };
                });
                register.invoke(this, e.type().get(), factory);
            }
        } catch (ReflectiveOperationException ex) {
            TerraWrath.LOG.error("TerraWrath could not register particle providers", ex);
        }
    }
}
