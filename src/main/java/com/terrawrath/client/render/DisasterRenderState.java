package com.terrawrath.client.render;

import com.terrawrath.disaster.DisasterType;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * Everything a disaster visual needs for one frame (already interpolated).
 */
public class DisasterRenderState extends EntityRenderState {
    public DisasterType type = DisasterType.TORNADO;
    public int phase;
    public float progress;
    public float presence;
    public float intensity;
    public float radius;
    public float a, b, c, d;
    public float time;
    public float flash;
    public double camX, camY, camZ;
    /** Ground height below the disaster (top of the motion-blocking heightmap). */
    public double ground;
}
