package com.terrawrath.entity;

import com.terrawrath.TerraWrath;
import com.terrawrath.registry.TWRegistry;
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
import net.minecraft.world.phys.Vec3;

/**
 * An expanding glowing ring + dust wall. Its arrival on the client triggers the flash and impact punch.
 */
public class ShockwaveEntity extends Entity {
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(ShockwaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_POWER = SynchedEntityData.defineId(ShockwaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(ShockwaveEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(ShockwaveEntity.class, EntityDataSerializers.INT);

    public boolean clientStarted;

    public ShockwaveEntity(EntityType<? extends ShockwaveEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /**
     * @param power 0..1+ : drives flash brightness and camera punch
     */
    public static ShockwaveEntity spawn(ServerLevel level, Vec3 pos, float maxRadius, float power, int color, int life) {
        ShockwaveEntity s = new ShockwaveEntity(TWRegistry.SHOCKWAVE_ENTITY.get(), level);
        s.setPos(pos.x, pos.y, pos.z);
        s.entityData.set(DATA_RADIUS, maxRadius);
        s.entityData.set(DATA_POWER, power);
        s.entityData.set(DATA_COLOR, color);
        s.entityData.set(DATA_LIFE, life);
        level.addFreshEntity(s);
        return s;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RADIUS, 30.0F);
        builder.define(DATA_POWER, 1.0F);
        builder.define(DATA_COLOR, 0xFFC23D);
        builder.define(DATA_LIFE, 40);
    }

    public float maxRadius() { return this.entityData.get(DATA_RADIUS); }
    public float power() { return this.entityData.get(DATA_POWER); }
    public int color() { return this.entityData.get(DATA_COLOR); }
    public int life() { return this.entityData.get(DATA_LIFE); }

    @Override
    public void tick() {
        if (this.level().isClientSide()) {
            TerraWrath.CLIENT.entityClientTick(this);
        } else if (this.tickCount > life()) {
            this.discard();
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
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
