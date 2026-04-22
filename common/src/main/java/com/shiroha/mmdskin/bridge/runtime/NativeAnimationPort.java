package com.shiroha.mmdskin.bridge.runtime;

import java.nio.ByteBuffer;

/** 文件职责：集中定义动画句柄、时间线与相机采样相关的 native 能力。 */
public interface NativeAnimationPort {

    long loadAnimation(long modelHandle, String animationPath);

    void deleteAnimation(long animationHandle);

    void mergeAnimation(long mergedAnimationHandle, long sourceAnimationHandle);

    boolean hasCameraData(long animationHandle);

    boolean hasBoneData(long animationHandle);

    boolean hasMorphData(long animationHandle);

    float getAnimationMaxFrame(long animationHandle);

    void seekLayer(long modelHandle, long layer, float frame);

    void getCameraTransform(long animationHandle, float frame, ByteBuffer targetBuffer);
}
