package com.shiroha.mmdskin.bridge.runtime;

/** 文件职责：定义场景姿态与空间定位相关的 native 能力边界。 */
public interface NativeScenePort {

    void setHeadAngle(long modelHandle, float x, float y, float z, boolean worldSpace);

    void setModelPositionAndYaw(long modelHandle, float x, float y, float z, float yawRadians);

    void setAutoBlinkEnabled(long modelHandle, boolean enabled);

    void setEyeTrackingEnabled(long modelHandle, boolean enabled);

    void setEyeMaxAngle(long modelHandle, float maxAngle);

    void setEyeAngle(long modelHandle, float eyeX, float eyeY);
}
