// 负责声明 ABI 4 的包私有 static native 入口，禁止业务模块直接接触 JNI。
package com.shiroha.mmdskin.bridge;

import java.nio.ByteBuffer;

final class NativeBindings {
    static native String GetVersion();

    static native int getAbiVersion();

    static native long LoadModelPMX(String filename, String directory, long layerCount);

    static native long LoadModelPMD(String filename, String directory, long layerCount);

    static native long LoadModelVRM(String filename, String directory, long layerCount);

    static native boolean IsVrmModel(long modelHandle);

    static native void UpdateModel(long modelHandle, float deltaSeconds);

    static native boolean SetGpuSkinningEnabled(long modelHandle, boolean enabled);

    static native long GetVertexCount(long modelHandle);

    static native long GetIndexCount(long modelHandle);

    static native long GetMaterialCount(long modelHandle);

    static native void ChangeModelAnim(long modelHandle, long animationHandle, long layer);

    static native void TransitionLayerTo(long modelHandle, long layer, long animationHandle,
                                         float transitionSeconds);

    static native void SetLayerLoop(long modelHandle, long layer, boolean loop);

    static native boolean IsLayerAnimationFinished(long modelHandle, long layer);

    static native boolean SetLayerBoneMask(long modelHandle, long layer, String rootBoneName);

    static native boolean SetLayerBoneExclude(long modelHandle, long layer, String rootBoneName);

    static native void SeekLayer(long modelHandle, long layer, float frame);

    static native void ResetModelPhysics(long modelHandle);

    static native long LoadAnimation(long modelHandle, String filename);

    static native boolean HasCameraData(long animationHandle);

    static native float GetAnimMaxFrame(long animationHandle);

    static native void GetCameraTransform(long animationHandle, float frame, ByteBuffer destination);

    static native boolean HasBoneData(long animationHandle);

    static native boolean HasMorphData(long animationHandle);

    static native void MergeAnimation(long targetHandle, long sourceHandle);

    static native void SetModelPositionAndYaw(long modelHandle, float x, float y, float z,
                                              float yawRadians);

    static native void SetEyeMaxAngle(long modelHandle, float maxAngle);

    static native void SetEyeTrackingEnabled(long modelHandle, boolean enabled);

    static native void SetAutoBlinkEnabled(long modelHandle, boolean enabled);

    static native void SetPhysicsEnabled(long modelHandle, boolean enabled);

    static native void SetPhysicsConfig(boolean enabled, float gravityY, float physicsFps,
                                        int maxSubstepCount, float inertiaStrength,
                                        float maxLinearVelocity, float maxAngularVelocity,
                                        boolean jointsEnabled, boolean kinematicFilter,
                                        boolean collisionEnabled, int collisionStabilityMode,
                                        boolean debugLog);

    static native boolean IsMaterialVisible(long modelHandle, int materialIndex);

    static native void SetMaterialVisible(long modelHandle, int materialIndex, boolean visible);

    static native void SetAllMaterialsVisible(long modelHandle, boolean visible);

    static native String GetMaterialName(long modelHandle, int materialIndex);

    static native int GetBoneCount(long modelHandle);

    static native int ApplyVpdMorph(long modelHandle, String filename);

    static native void ResetAllMorphs(long modelHandle);

    static native long GetMorphCount(long modelHandle);

    static native String GetMorphName(long modelHandle, int morphIndex);

    static native void SetMorphWeight(long modelHandle, int morphIndex, float weight);

    static native void SetFirstPersonMode(long modelHandle, boolean enabled);

    static native void GetEyeBonePosition(long modelHandle, float[] output);

    static native String GetBoneNames(long modelHandle);

    static native int CopyBonePositionsToBuffer(long modelHandle, ByteBuffer destination);

    static native int CopyRealtimeUVsToBuffer(long modelHandle, ByteBuffer destination);

    static native long GetModelMemoryUsage(long modelHandle);

    static native void SetVRTrackingData(long modelHandle, float[] trackingData);

    static native void SetVREnabled(long modelHandle, boolean enabled);

    static native void SetVRIKParams(long modelHandle, float armIkStrength);

    static native int describeMesh(long modelHandle, ByteBuffer destination);

    static native int copyIndices(long modelHandle, ByteBuffer destination);

    static native int copyFrameVertices(long modelHandle, ByteBuffer destination);

    static native int copyDrawCommands(long modelHandle, ByteBuffer destination);

    static native int copyBonePaletteMatrices(long modelHandle, ByteBuffer destination);

    static native int copyHandMatrix(long modelHandle, int hand, ByteBuffer destination);

    static native String getTexturePath(long modelHandle, int textureIndex);

    static native long decodeTexture(String path);

    static native int describeTexture(long textureHandle, ByteBuffer destination);

    static native int copyTexturePixels(long textureHandle, ByteBuffer destination);

    static native int textureHasAlpha(long textureHandle);

    static native int releaseModel(long modelHandle);

    static native int releaseAnimation(long animationHandle);

    static native int releaseTexture(long textureHandle);

    private NativeBindings() {
    }
}
