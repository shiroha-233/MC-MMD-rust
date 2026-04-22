package com.shiroha.mmdskin.bridge.runtime;

import com.shiroha.mmdskin.NativeFunc;
import java.nio.ByteBuffer;

/** 文件职责：收口动画句柄的 native 调用。 */
public final class NativeAnimationBridge implements NativeAnimationPort {

    @Override
    public long loadAnimation(long modelHandle, String animationPath) {
        return NativeFunc.GetInst().LoadAnimation(modelHandle, animationPath);
    }

    @Override
    public void deleteAnimation(long animationHandle) {
        NativeFunc.GetInst().DeleteAnimation(animationHandle);
    }

    @Override
    public void mergeAnimation(long mergedAnimationHandle, long sourceAnimationHandle) {
        NativeFunc.GetInst().MergeAnimation(mergedAnimationHandle, sourceAnimationHandle);
    }

    @Override
    public boolean hasCameraData(long animationHandle) {
        return NativeFunc.GetInst().HasCameraData(animationHandle);
    }

    @Override
    public boolean hasBoneData(long animationHandle) {
        return NativeFunc.GetInst().HasBoneData(animationHandle);
    }

    @Override
    public boolean hasMorphData(long animationHandle) {
        return NativeFunc.GetInst().HasMorphData(animationHandle);
    }

    @Override
    public float getAnimationMaxFrame(long animationHandle) {
        return NativeFunc.GetInst().GetAnimMaxFrame(animationHandle);
    }

    @Override
    public void seekLayer(long modelHandle, long layer, float frame) {
        NativeFunc.GetInst().SeekLayer(modelHandle, layer, frame);
    }

    @Override
    public void getCameraTransform(long animationHandle, float frame, ByteBuffer targetBuffer) {
        NativeFunc.GetInst().GetCameraTransform(animationHandle, frame, targetBuffer);
    }
}
