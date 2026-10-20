package com.terrawrath.registry;

import com.terrawrath.TerraWrath;
import com.terrawrath.entity.BoltEntity;
import com.terrawrath.entity.CameraEntity;
import com.terrawrath.entity.DebrisEntity;
import com.terrawrath.entity.DisasterEntity;
import com.terrawrath.entity.MeteorEntity;
import com.terrawrath.entity.ShockwaveEntity;
import com.terrawrath.item.ControlPanelItem;
import com.terrawrath.item.DisasterDetectorItem;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * Every registry object TerraWrath adds. Platform shims iterate {@link #ENTRIES} and register them
 * with DeferredRegister (Forge) or the vanilla registries directly (Fabric).
 */
public final class TWRegistry {
    public record Entry<T>(ResourceKey<? extends Registry<T>> registry, String name, Supplier<T> factory, Ref<T> ref) {
        public T create() {
            T value = factory.get();
            ref.set(value);
            return value;
        }
    }

    public static final List<Entry<?>> ENTRIES = new ArrayList<>();

    /** Platform supplies a builder (Forge and Fabric position modded tabs differently). */
    public static Supplier<CreativeModeTab.Builder> tabBuilder = () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0);

    // ------------------------------------------------------------------ sounds
    public static final Ref<SoundEvent> WIND_LOOP = sound("wind_loop");
    public static final Ref<SoundEvent> RUMBLE_LOOP = sound("rumble_loop");
    public static final Ref<SoundEvent> BLIZZARD_LOOP = sound("blizzard_loop");
    public static final Ref<SoundEvent> STORM_LOOP = sound("storm_loop");
    public static final Ref<SoundEvent> WAVE_ROAR_LOOP = sound("wave_roar_loop");
    public static final Ref<SoundEvent> LAVA_LOOP = sound("lava_loop");
    public static final Ref<SoundEvent> HOLE_LOOP = sound("hole_loop");
    public static final Ref<SoundEvent> SIREN = sound("siren");
    public static final Ref<SoundEvent> WARNING = sound("warning");
    public static final Ref<SoundEvent> METEOR_WHISTLE = sound("meteor_whistle");
    public static final Ref<SoundEvent> ERUPTION = sound("eruption");
    public static final Ref<SoundEvent> WAVE_CRASH = sound("wave_crash");
    public static final Ref<SoundEvent> COLLAPSE = sound("collapse");
    public static final Ref<SoundEvent> SUPERNOVA = sound("supernova");
    public static final Ref<SoundEvent> SHOCKWAVE = sound("shockwave");
    public static final Ref<SoundEvent> ICE_BURST = sound("ice_burst");
    public static final Ref<SoundEvent> BEEP = sound("beep");
    public static final Ref<SoundEvent> UI_CLICK = sound("ui_click");
    public static final Ref<SoundEvent> WHOOSH = sound("whoosh");
    public static final Ref<SoundEvent> THUNDER = sound("thunder");
    public static final Ref<SoundEvent> IMPACT = sound("impact");
    public static final Ref<SoundEvent> CRACK = sound("crack");

    // ------------------------------------------------------------------ particles
    public static final Ref<SimpleParticleType> P_GLOW = particle("glow");
    public static final Ref<SimpleParticleType> P_EMBER = particle("ember");
    public static final Ref<SimpleParticleType> P_SPARK = particle("spark");
    public static final Ref<SimpleParticleType> P_SMOKE = particle("smoke");
    public static final Ref<SimpleParticleType> P_DUST = particle("dust");
    public static final Ref<SimpleParticleType> P_ASH = particle("ash");
    public static final Ref<SimpleParticleType> P_RAIN = particle("rain");
    public static final Ref<SimpleParticleType> P_SNOW = particle("snow");
    public static final Ref<SimpleParticleType> P_SPRAY = particle("spray");
    public static final Ref<SimpleParticleType> P_FLAME = particle("flame");
    public static final Ref<SimpleParticleType> P_SHARD = particle("shard");

    // ------------------------------------------------------------------ entities
    public static final Ref<EntityType<DisasterEntity>> DISASTER = entity("disaster",
        key -> EntityType.Builder.<DisasterEntity>of(DisasterEntity::new, MobCategory.MISC)
            .sized(2.0F, 2.0F).clientTrackingRange(32).updateInterval(1)
            .noSave().noSummon().fireImmune().noLootTable().build(key));
    public static final Ref<EntityType<DebrisEntity>> DEBRIS = entity("debris",
        key -> EntityType.Builder.<DebrisEntity>of(DebrisEntity::new, MobCategory.MISC)
            .sized(0.9F, 0.9F).clientTrackingRange(16).updateInterval(2)
            .noSave().noSummon().noLootTable().build(key));
    public static final Ref<EntityType<MeteorEntity>> METEOR = entity("meteor",
        key -> EntityType.Builder.<MeteorEntity>of(MeteorEntity::new, MobCategory.MISC)
            .sized(1.5F, 1.5F).clientTrackingRange(32).updateInterval(1)
            .noSave().noSummon().fireImmune().noLootTable().build(key));
    public static final Ref<EntityType<BoltEntity>> BOLT = entity("bolt",
        key -> EntityType.Builder.<BoltEntity>of(BoltEntity::new, MobCategory.MISC)
            .sized(0.5F, 0.5F).clientTrackingRange(32).updateInterval(1000)
            .noSave().noSummon().fireImmune().noLootTable().build(key));
    public static final Ref<EntityType<ShockwaveEntity>> SHOCKWAVE_ENTITY = entity("shockwave",
        key -> EntityType.Builder.<ShockwaveEntity>of(ShockwaveEntity::new, MobCategory.MISC)
            .sized(1.0F, 1.0F).clientTrackingRange(32).updateInterval(1000)
            .noSave().noSummon().fireImmune().noLootTable().build(key));
    public static final Ref<EntityType<CameraEntity>> CAMERA = entity("camera",
        key -> EntityType.Builder.<CameraEntity>of(CameraEntity::new, MobCategory.MISC)
            .sized(0.1F, 0.1F).clientTrackingRange(1)
            .noSave().noSummon().fireImmune().noLootTable().build(key));

    // ------------------------------------------------------------------ items
    public static final Ref<Item> CONTROL_PANEL = item("control_panel",
        key -> new ControlPanelItem(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.EPIC)));
    public static final Ref<Item> DETECTOR = item("disaster_detector",
        key -> new DisasterDetectorItem(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.RARE)));

    // ------------------------------------------------------------------ creative tab
    public static final Ref<CreativeModeTab> TAB = add(Registries.CREATIVE_MODE_TAB, "terrawrath",
        () -> tabBuilder.get()
            .title(Component.translatable("itemGroup.terrawrath"))
            .icon(() -> CONTROL_PANEL.get().getDefaultInstance())
            .displayItems(tabContents())
            .build());

    private TWRegistry() {}

    /**
     * Creative tab contents. CreativeModeTab.Output is protected in vanilla's source (public at runtime), so the
     * generator is a dynamic proxy that works identically on Forge and Fabric.
     */
    private static CreativeModeTab.DisplayItemsGenerator tabContents() {
        return (CreativeModeTab.DisplayItemsGenerator) java.lang.reflect.Proxy.newProxyInstance(
            TWRegistry.class.getClassLoader(), new Class<?>[]{CreativeModeTab.DisplayItemsGenerator.class}, (proxy, method, args) -> {
                if ("accept".equals(method.getName()) && args != null && args.length == 2) {
                    Object output = args[1];
                    java.lang.reflect.Method accept = null;
                    for (Class<?> c : output.getClass().getInterfaces()) {
                        try {
                            accept = c.getMethod("accept", net.minecraft.world.level.ItemLike.class);
                            break;
                        } catch (NoSuchMethodException ignored) {
                        }
                    }
                    if (accept == null) {
                        Class<?> outClass = Class.forName("net.minecraft.world.item.CreativeModeTab$Output", false, CreativeModeTab.class.getClassLoader());
                        accept = outClass.getMethod("accept", net.minecraft.world.level.ItemLike.class);
                    }
                    accept.setAccessible(true);
                    accept.invoke(output, CONTROL_PANEL.get());
                    accept.invoke(output, DETECTOR.get());
                    return null;
                }
                return switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "TerraWrathTabContents";
                    default -> null;
                };
            });
    }

    public static void bootstrap() {
        // touching the class runs the static initialisers above
    }

    private static <T> Ref<T> add(ResourceKey<? extends Registry<T>> registry, String name, Supplier<T> factory) {
        Ref<T> ref = new Ref<>(name);
        ENTRIES.add(new Entry<>(registry, name, factory, ref));
        return ref;
    }

    private static Ref<SoundEvent> sound(String name) {
        return add(Registries.SOUND_EVENT, name, () -> SoundEvent.createVariableRangeEvent(TerraWrath.id(name)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Ref<SimpleParticleType> particle(String name) {
        return (Ref) add((ResourceKey) Registries.PARTICLE_TYPE, name, () -> new SimpleParticleType(true) {});
    }

    private static <E extends net.minecraft.world.entity.Entity> Ref<EntityType<E>> entity(
        String name, Function<ResourceKey<EntityType<?>>, EntityType<E>> factory
    ) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, TerraWrath.id(name));
        @SuppressWarnings({"unchecked", "rawtypes"})
        Ref<EntityType<E>> ref = (Ref) add((ResourceKey) Registries.ENTITY_TYPE, name, () -> factory.apply(key));
        return ref;
    }

    private static Ref<Item> item(String name, Function<ResourceKey<Item>, Item> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, TerraWrath.id(name));
        return add(Registries.ITEM, name, () -> factory.apply(key));
    }
}
