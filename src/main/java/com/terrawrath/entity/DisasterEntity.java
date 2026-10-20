package com.terrawrath.entity;

import com.terrawrath.TerraWrath;
import com.terrawrath.disaster.DisasterType;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The visible heart of a disaster. The server-side {@code Disaster} object drives it; its synced data
 * is everything the client needs to render the disaster, tint the sky, shake the camera and play sound.
 */
public class DisasterEntity extends Entity {
    public static final int WARNING = 0;
    public static final int ACTIVE = 1;
    public static final int ENDING = 2;

    private static final EntityDataAccessor<Integer> DATA_TYPE = SynchedEntityData.defineId(DisasterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(DisasterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TIMER = SynchedEntityData.defineId(DisasterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_EVENT = SynchedEntityData.defineId(DisasterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_INTENSITY = SynchedEntityData.defineId(DisasterEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(DisasterEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PROGRESS = SynchedEntityData.defineId(DisasterEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SHAKE = SynchedEntityData.defineId(DisasterEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_A = SynchedEntityData.defineId(DisasterEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_B = SynchedEntityData.defineId(DisasterEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_C = SynchedEntityData.defineId(DisasterEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_D = SynchedEntityData.defineId(DisasterEntity.class, EntityDataSerializers.FLOAT);

    /** Client-only scratch state owned by the visual code (smoothing, cached geometry, sound handles). */
    public Object clientState;

    public DisasterEntity(EntityType<? extends DisasterEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_TYPE, 0);
        builder.define(DATA_PHASE, WARNING);
        builder.define(DATA_TIMER, 0);
        builder.define(DATA_EVENT, 0);
        builder.define(DATA_INTENSITY, 1.0F);
        builder.define(DATA_RADIUS, 20.0F);
        builder.define(DATA_PROGRESS, 0.0F);
        builder.define(DATA_SHAKE, 0.0F);
        builder.define(DATA_A, 0.0F);
        builder.define(DATA_B, 0.0F);
        builder.define(DATA_C, 0.0F);
        builder.define(DATA_D, 0.0F);
    }

    @Override
    public void tick() {
        if (this.level().isClientSide()) {
            TerraWrath.CLIENT.entityClientTick(this);
        }
    }

    // ------------------------------------------------------------------ synced accessors
    public DisasterType disasterType() { return DisasterType.byOrdinal(this.entityData.get(DATA_TYPE)); }
    public void setDisasterType(DisasterType type) { this.entityData.set(DATA_TYPE, type.ordinal()); }
    public int phase() { return this.entityData.get(DATA_PHASE); }
    public void setPhase(int phase) { this.entityData.set(DATA_PHASE, phase); }
    public int timer() { return this.entityData.get(DATA_TIMER); }
    public void setTimer(int ticks) { this.entityData.set(DATA_TIMER, ticks); }
    /** Incremented by the server to trigger one-shot client effects (flash, punch). */
    public int eventCounter() { return this.entityData.get(DATA_EVENT); }
    public void fireEvent() { this.entityData.set(DATA_EVENT, this.entityData.get(DATA_EVENT) + 1); }
    public float intensity() { return this.entityData.get(DATA_INTENSITY); }
    public void setIntensity(float v) { this.entityData.set(DATA_INTENSITY, v); }
    public float radius() { return this.entityData.get(DATA_RADIUS); }
    public void setRadius(float v) { if (v != radius()) this.entityData.set(DATA_RADIUS, v); }
    public float progress() { return this.entityData.get(DATA_PROGRESS); }
    public void setProgress(float v) { this.entityData.set(DATA_PROGRESS, v); }
    public float shake() { return this.entityData.get(DATA_SHAKE); }
    public void setShake(float v) { if (Math.abs(v - shake()) > 0.005F) this.entityData.set(DATA_SHAKE, v); }
    public float a() { return this.entityData.get(DATA_A); }
    public float b() { return this.entityData.get(DATA_B); }
    public float c() { return this.entityData.get(DATA_C); }
    public float d() { return this.entityData.get(DATA_D); }
    public void setA(float v) { if (v != a()) this.entityData.set(DATA_A, v); }
    public void setB(float v) { if (v != b()) this.entityData.set(DATA_B, v); }
    public void setC(float v) { if (v != c()) this.entityData.set(DATA_C, v); }
    public void setD(float v) { if (v != d()) this.entityData.set(DATA_D, v); }

    // ------------------------------------------------------------------ inert entity behaviour
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE_ENTITY;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {}
}
