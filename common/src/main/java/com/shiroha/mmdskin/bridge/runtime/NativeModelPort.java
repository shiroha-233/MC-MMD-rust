package com.shiroha.mmdskin.bridge.runtime;

/** 文件职责：定义模型运行时相关的 native 能力边界。 */
public interface NativeModelPort {

    boolean setLayerBoneMask(long modelHandle, int layer, String rootBoneName);

    boolean setLayerBoneExclude(long modelHandle, int layer, String rootBoneName);

    long getModelMemoryUsage(long modelHandle);

    void setFirstPersonMode(long modelHandle, boolean enabled);

    void getEyeBonePosition(long modelHandle, float[] output);

    void applyVrTrackingInput(long modelHandle, float[] trackingData);

    void setVrEnabled(long modelHandle, boolean enabled);

    void setVrIkParams(long modelHandle, float armIkStrength);

    int getMaterialCount(long modelHandle);

    void setMaterialVisible(long modelHandle, int materialIndex, boolean visible);

    void setAllMaterialsVisible(long modelHandle, boolean visible);

    void deleteModel(long modelHandle);
}
