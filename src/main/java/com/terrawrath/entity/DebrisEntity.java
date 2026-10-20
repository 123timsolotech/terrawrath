package com.terrawrath.entity;

import com.terrawrath.TerraWrath;
import com.terrawrath.config.TWConfig;
import com.terrawrath.registry.TWRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A real block ripped out of the world: it tumbles through the air, orbits disasters and smashes down.
 */
public class DebrisEntity extends Entity {
    public static final int MODE_BALLISTIC = 0;
    public static final int MODE_ORBIT = 1;
    public static final AtomicInteger LIVE = new AtomicInteger();

    private static final EntityDataAccessor<BlockState> DATA_BLOCK = SynchedEntityData.defineId(DebrisEntity.class, EntityDataSerializers.BLOCK_STATE);
    private static final EntityDataAccessor<Float> DATA_SCALE = SynchedEntityData.defineId(DebrisEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_BURNING = SynchedEntityData.defineId(DebrisEntity.class, EntityDataSerializers.BOOLEAN);

    // server-side motion state
    private int mode = MODE_BALLISTIC;
    private Entity anchor;
    private double orbitRadius;
    private double orbitAngle;
    private double orbitHeight;
    private double angularSpeed;
    private double radialSpeed;
    private double climbSpeed;
    private double maxHeight;
    private double releaseSpeed;
    private boolean consumeAtCenter;
    private int life;
    private int maxLife = 500;
    private float placeChance = 0.25F;
    private boolean counted;

    public DebrisEntity(EntityType<? extends DebrisEntity> type, Level level) {
        super(type, level);
    }

    public static DebrisEntity spawn(ServerLevel level, Vec3 pos, BlockState state, Vec3 velocity, float scale) {
        if (LIVE.get() >= TWConfig.VALUES.maxDebrisEntities) {
            return null;
        }
        DebrisEntity e = new DebrisEntity(TWRegistry.DEBRIS.get(), level);
        e.setPos(pos.x, pos.y, pos.z);
        e.setBlock(state);
        e.entityData.set(DATA_SCALE, scale);
        e.setDeltaMovement(velocity);
        e.counted = true;
        LIVE.incrementAndGet();
        level.addFreshEntity(e);
        return e;
    }

    @Override
    protected InterpolationHandler createInterpolationHandler() {
        return LinearInterpolationHandler.create(this, 2);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BLOCK, Blocks.DIRT.defaultBlockState());
        builder.define(DATA_SCALE, 1.0F);
        builder.define(DATA_BURNING, false);
    }

    public BlockState block() { return this.entityData.get(DATA_BLOCK); }
    public void setBlock(BlockState state) { this.entityData.set(DATA_BLOCK, state); }
    public float scale() { return this.entityData.get(DATA_SCALE); }
    public boolean burning() { return this.entityData.get(DATA_BURNING); }
    public void setBurning(boolean b) { this.entityData.set(DATA_BURNING, b); }

    public DebrisEntity placeChance(float chance) { this.placeChance = chance; return this; }
    public DebrisEntity maxLife(int ticks) { this.maxLife = ticks; return this; }

    /**
     * Orbit an anchor (tornado funnel, black hole) in a spiral, then get flung (or swallowed).
     */
    public DebrisEntity orbit(Entity anchor, double radius, double angle, double height, double angularSpeed,
                              double radialSpeed, double climbSpeed, double maxHeight, double releaseSpeed, boolean consumeAtCenter) {
        this.mode = MODE_ORBIT;
        this.anchor = anchor;
        this.orbitRadius = radius;
        this.orbitAngle = angle;
        this.orbitHeight = height;
        this.angularSpeed = angularSpeed;
        this.radialSpeed = radialSpeed;
        this.climbSpeed = climbSpeed;
        this.maxHeight = maxHeight;
        this.releaseSpeed = releaseSpeed;
        this.consumeAtCenter = consumeAtCenter;
        return this;
    }

    public void release(Vec3 velocity) {
        this.mode = MODE_BALLISTIC;
        this.anchor = null;
        this.setDeltaMovement(velocity);
    }

    @Override
    public void tick() {
        if (this.level().isClientSide()) {
            TerraWrath.CLIENT.entityClientTick(this);
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        if (++life > maxLife || this.getY() < level.getMinY() - 8) {
            this.discard();
            return;
        }
        if (mode == MODE_ORBIT) {
            tickOrbit(level);
        } else {
            tickBallistic(level);
        }
    }

    private void tickOrbit(ServerLevel level) {
        if (anchor == null || anchor.isRemoved()) {
            release(new Vec3(Math.cos(orbitAngle) * releaseSpeed, 0.4, Math.sin(orbitAngle) * releaseSpeed));
            return;
        }
        double speedBoost = consumeAtCenter ? Math.min(4.0, 18.0 / Math.max(orbitRadius, 3.0)) : 1.0;
        orbitAngle += angularSpeed * speedBoost;
        orbitRadius = Math.max(0.5, orbitRadius + radialSpeed * (consumeAtCenter ? speedBoost : 1.0));
        if (consumeAtCenter) {
            // drift toward the disk plane while spiralling in
            orbitHeight += (0.0 - orbitHeight) * 0.035 + climbSpeed * 0.0;
        } else {
            orbitHeight += climbSpeed;
        }
        double x = anchor.getX() + Math.cos(orbitAngle) * orbitRadius;
        double y = anchor.getY() + orbitHeight;
        double z = anchor.getZ() + Math.sin(orbitAngle) * orbitRadius;
        Vec3 old = this.position();
        this.setPos(x, y, z);
        this.setDeltaMovement(new Vec3(x - old.x, y - old.y, z - old.z));
        if (consumeAtCenter && orbitRadius < 4.5) {
            this.discard();
            return;
        }
        if (!consumeAtCenter && orbitHeight >= maxHeight) {
            // fling it far away along the tangent
            double tx = -Math.sin(orbitAngle), tz = Math.cos(orbitAngle);
            double rx = Math.cos(orbitAngle), rz = Math.sin(orbitAngle);
            release(new Vec3((tx * 0.8 + rx * 0.6) * releaseSpeed, 0.25 + this.random.nextDouble() * 0.5, (tz * 0.8 + rz * 0.6) * releaseSpeed));
        }
        hitEntities(level, 4.0F);
    }

    private void tickBallistic(ServerLevel level) {
        Vec3 v = this.getDeltaMovement();
        v = new Vec3(v.x * 0.985, v.y - 0.06, v.z * 0.985);
        this.setDeltaMovement(v);
        this.move(MoverType.SELF, v);
        if (v.lengthSqr() > 0.16) {
            hitEntities(level, (float) Math.min(10.0, v.length() * 4.0));
        }
        if (this.onGround() || this.horizontalCollision || this.verticalCollision) {
            land(level);
        }
    }

    private void hitEntities(ServerLevel level, float damage) {
        if ((this.tickCount & 1) != 0) {
            return;
        }
        AABB box = this.getBoundingBox().inflate(0.4);
        List<LivingEntity> hits = level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive);
        for (LivingEntity hit : hits) {
            hit.hurtServer(level, level.damageSources().fallingBlock(this), damage);
            Vec3 push = this.getDeltaMovement().scale(0.6);
            hit.push(push.x, push.y * 0.5 + 0.15, push.z);
        }
    }

    private void land(ServerLevel level) {
        BlockPos pos = this.blockPosition();
        BlockState state = block();
        level.levelEvent(2001, pos, Block.getId(state));
        if (TWConfig.VALUES.terrainDestruction && this.random.nextFloat() < placeChance
            && level.getBlockState(pos).canBeReplaced() && !state.isAir()) {
            BlockState placed = burning() && this.random.nextBoolean() ? Blocks.MAGMA_BLOCK.defaultBlockState() : state;
            level.setBlock(pos, placed, Block.UPDATE_ALL);
            if (burning() && level.getBlockState(pos.above()).isAir() && this.random.nextInt(3) == 0) {
                level.setBlock(pos.above(), Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        this.discard();
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        super.onRemoval(reason);
        if (counted) {
            counted = false;
            LIVE.decrementAndGet();
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256 * 256;
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
    protected void readAdditionalSaveData(ValueInput input) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {}

    public float spinSeed() {
        return (this.getId() * 0.6180339F) % 1.0F;
    }

    public static float wrap(float f) {
        return Mth.wrapDegrees(f);
    }
}
