package com.shiroha.mmdskin.bridge.runtime;

/** 文件职责：集中定义 morph 与表情应用相关的 native 能力。 */
public interface NativeMorphPort {

    void resetAllMorphs(long modelHandle);

    void setMorphWeight(long modelHandle, int morphIndex, float weight);

    void syncGpuMorphWeights(long modelHandle);

    int applyVpdMorph(long modelHandle, String filePath);
}
