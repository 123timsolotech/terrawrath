package com.terrawrath.entity;

import com.terrawrath.TerraWrath;
import com.terrawrath.registry.TWRegistry;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * A burning rock falling from the sky: meteors, lava bombs and the colossal final meteor.
 */
public class MeteorEntity extends Entity {
    public static final int KIND_METEOR = 0;
    public static final int KIND_LAVA_BOMB = 1;
    public static final int KIND_COLOSSAL = 2;

    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Vector3fc> DATA_TARGET = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3fc> DATA_VELOCITY = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.VECTOR3);

    private Consumer<MeteorEntity> onImpact;
    private double gravity;
    private int life;

    public MeteorEntity(EntityType<? extends MeteorEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static MeteorEntity launch(ServerLevel level, Vec3 from, Vec3 velocity, Vec3 target, int kind, float size,
                                      double gravity, Consumer<MeteorEntity> onImpact) {
        MeteorEntity m = new MeteorEntity(TWRegistry.METEOR.get(), level);
        m.setPos(from.x, from.y, from.z);
        m.setDeltaMovement(velocity);
        m.entityData.set(DATA_KIND, kind);
        m.entityData.set(DATA_SIZE, size);
        m.entityData.set(DATA_TARGET, new Vector3f((float) target.x, (float) target.y, (float) target.z));
        m.entityData.set(DATA_VELOCITY, new Vector3f((float) velocity.x, (float) velocity.y, (float) velocity.z));
        m.gravity = gravity;
        m.onImpact = onImpact;
        level.addFreshEntity(m);
        return m;
    }

    @Override
    protected InterpolationHandler createInterpolationHandler() {
        return LinearInterpolationHandler.create(this, 1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_KIND, KIND_METEOR);
        builder.define(DATA_SIZE, 1.0F);
        builder.define(DATA_TARGET, new Vector3f());
        builder.define(DATA_VELOCITY, new Vector3f(0, -1, 0));
    }

    public int kind() { return this.entityData.get(DATA_KIND); }
    public float size() { return this.entityData.get(DATA_SIZE); }
    public Vector3fc target() { return this.entityData.get(DATA_TARGET); }
    public Vector3fc launchVelocity() { return this.entityData.get(DATA_VELOCITY); }

    @Override
    public void tick() {
        if (this.level().isClientSide()) {
            TerraWrath.CLIENT.entityClientTick(this);
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        if (++life > 1200 || this.getY() < level.getMinY()) {
            this.discard();
            return;
        }
        Vec3 v = this.getDeltaMovement().add(0, -gravity, 0);
        this.setDeltaMovement(v);
        Vec3 from = this.position();
        Vec3 to = from.add(v);
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 at = hit.getLocation();
            this.setPos(at.x, at.y, at.z);
            impact(level);
            return;
        }
        if (!level.isLoaded(BlockPos.containing(to))) {
            Vector3fc t = target();
            if (from.distanceToSqr(t.x(), t.y(), t.z()) < v.lengthSqr() * 4) {
                impact(level);
                return;
            }
        }
        this.setPos(to.x, to.y, to.z);
    }

    private void impact(ServerLevel level) {
        if (onImpact != null) {
            onImpact.accept(this);
        }
        this.discard();
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
    public boolean isPushable() {
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
