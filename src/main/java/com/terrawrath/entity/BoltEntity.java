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
 * A giant branching lightning bolt (visual). The disaster applies damage/fire itself.
 */
public class BoltEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(BoltEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(BoltEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_HEIGHT = SynchedEntityData.defineId(BoltEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_WIDTH = SynchedEntityData.defineId(BoltEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_SILENT = SynchedEntityData.defineId(BoltEntity.class, EntityDataSerializers.BOOLEAN);

    public static final int LIFETIME = 12;
    public boolean clientStarted;

    public BoltEntity(EntityType<? extends BoltEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static BoltEntity strike(ServerLevel level, Vec3 ground, float height, int color, float width, boolean silent) {
        BoltEntity b = new BoltEntity(TWRegistry.BOLT.get(), level);
        b.setPos(ground.x, ground.y, ground.z);
        b.entityData.set(DATA_SEED, level.getRandom().nextInt());
        b.entityData.set(DATA_COLOR, color);
        b.entityData.set(DATA_HEIGHT, height);
        b.entityData.set(DATA_WIDTH, width);
        b.entityData.set(DATA_SILENT, silent);
        level.addFreshEntity(b);
        return b;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SEED, 0);
        builder.define(DATA_COLOR, 0xD8CCFF);
        builder.define(DATA_HEIGHT, 120.0F);
        builder.define(DATA_WIDTH, 1.0F);
        builder.define(DATA_SILENT, false);
    }

    public int seed() { return this.entityData.get(DATA_SEED); }
    public int color() { return this.entityData.get(DATA_COLOR); }
    public float height() { return this.entityData.get(DATA_HEIGHT); }
    public float width() { return this.entityData.get(DATA_WIDTH); }
    public boolean silent() { return this.entityData.get(DATA_SILENT); }

    @Override
    public void tick() {
        if (this.level().isClientSide()) {
            TerraWrath.CLIENT.entityClientTick(this);
        } else if (this.tickCount > LIFETIME) {
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
