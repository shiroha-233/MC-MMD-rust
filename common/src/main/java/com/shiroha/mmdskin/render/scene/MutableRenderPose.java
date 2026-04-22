package com.shiroha.mmdskin.render.scene;

import org.joml.Vector3f;

/** 文件职责：承载一次渲染计算得到的可变姿态参数，避免热路径重复创建分散参数对象。 */
public final class MutableRenderPose {
    public float bodyYaw;
    public float bodyPitch;
    public final Vector3f translation = new Vector3f();

    public void reset() {
        bodyYaw = 0.0f;
        bodyPitch = 0.0f;
        translation.zero();
    }
}
