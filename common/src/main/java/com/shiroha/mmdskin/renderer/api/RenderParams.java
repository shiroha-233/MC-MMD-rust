package com.shiroha.mmdskin.renderer.api;

import org.joml.Vector3f;

/**
 * 渲染参数数据类。
 */
public class RenderParams {
    public float bodyYaw;
    public float bodyPitch;
    public Vector3f translation;

    public RenderParams() {
        this.bodyYaw = 0.0f;
        this.bodyPitch = 0.0f;
        this.translation = new Vector3f(0.0f);
    }
}
