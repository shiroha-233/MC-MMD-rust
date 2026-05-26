/* 文件职责：定义 morph 与表情应用相关的 native 能力边界。 */
package com.shiroha.mmdskin.bridge.runtime;

public interface NativeMorphPort {

    void resetAllMorphs(long modelHandle);

    void setMorphWeight(long modelHandle, int morphIndex, float weight);

    void syncGpuMorphWeights(long modelHandle);

    int applyVpdMorph(long modelHandle, String filePath);
}
