package com.terrawrath.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Client-only camera rig for the cinematic orbit. Never added to a server level.
 */
public class CameraEntity extends Entity {
    public CameraEntity(EntityType<? extends CameraEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** Sets position and rotation while keeping last tick's values for smooth interpolation. */
    public void moveCamera(double x, double y, double z, float yaw, float pitch) {
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        this.xOld = this.getX();
        this.yOld = this.getY();
        this.zOld = this.getZ();
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        this.setPos(x, y, z);
        this.setYRot(yaw);
        this.setXRot(pitch);
    }

    public void snapCamera(double x, double y, double z, float yaw, float pitch) {
        this.setPos(x, y, z);
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.xo = this.xOld = x;
        this.yo = this.yOld = y;
        this.zo = this.zOld = z;
        this.yRotO = yaw;
        this.xRotO = pitch;
    }

    @Override
    public void tick() {}

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {}

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {}
}
