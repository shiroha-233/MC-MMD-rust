package com.shiroha.mmdskin;

import java.nio.ByteBuffer;

public class NativeFunc {
    private static volatile NativeFunc inst;
    private static final Object lock = new Object();

    public static boolean isAndroid() { return NativeLibraryLoader.isAndroid(); }

    public static NativeFunc GetInst() {
        if (inst == null) {
            synchronized (lock) {
                if (inst == null) {
                    NativeFunc newInst = new NativeFunc();
                    NativeLibraryLoader.loadAndVerify(newInst);
                    inst = newInst;
                }
            }
        }
        return inst;
    }

    public native String GetVersion();

    public native byte ReadByte(long data, long pos);

    public native void CopyDataToByteBuffer(ByteBuffer buffer, long data, long pos);

    public native long LoadModelPMX(String filename, String dir, long layerCount);

    public native long LoadModelPMD(String filename, String dir, long layerCount);

    public native long LoadModelVRM(String filename, String dir, long layerCount);

    public native boolean IsVrmModel(long model);

    public native void DeleteModel(long model);

    public native void UpdateModel(long model, float deltaTime);

    public native long GetVertexCount(long model);

    public native long GetPoss(long model);

    public native long GetNormals(long model);

    public native long GetUVs(long model);

    public native long GetIndexElementSize(long model);

    public native long GetIndexCount(long model);

    public native long GetIndices(long model);

    public native long GetMaterialCount(long model);

    public native String GetMaterialTex(long model, long pos);

    public native String GetMaterialSpTex(long model, long pos);

    public native String GetMaterialToonTex(long model, long pos);

    public native long GetMaterialAmbient(long model, long pos);

    public native long GetMaterialDiffuse(long model, long pos);

    public native long GetMaterialSpecular(long model, long pos);

    public native float GetMaterialSpecularPower(long model, long pos);

    public native float GetMaterialAlpha(long model, long pos);

    public native long GetMaterialTextureMulFactor(long model, long pos);

    public native long GetMaterialTextureAddFactor(long model, long pos);

    public native int GetMaterialSpTextureMode(long model, long pos);

    public native long GetMaterialSpTextureMulFactor(long model, long pos);

    public native long GetMaterialSpTextureAddFactor(long model, long pos);

    public native long GetMaterialToonTextureMulFactor(long model, long pos);

    public native long GetMaterialToonTextureAddFactor(long model, long pos);

    public native boolean GetMaterialBothFace(long model, long pos);

    public native long GetSubMeshCount(long model);

    public native int GetSubMeshMaterialID(long model, long pos);

    public native int GetSubMeshBeginIndex(long model, long pos);

    public native int GetSubMeshVertexCount(long model, long pos);

    public native void ChangeModelAnim(long model, long anim, long layer);

    public native void TransitionLayerTo(long model, long layer, long anim, float transitionTime);

    public native void SetLayerLoop(long model, long layer, boolean loop);

    public native boolean IsLayerAnimationFinished(long model, long layer);

    public native boolean SetLayerBoneMask(long model, long layer, String rootBoneName);

    public native boolean SetLayerBoneExclude(long model, long layer, String rootBoneName);

    public native void SeekLayer(long model, long layer, float frame);

    public native void ResetModelPhysics(long model);

    public native long CreateMat();

    public native void DeleteMat(long mat);

    public native boolean CopyMatToBuffer(long mat, java.nio.ByteBuffer buffer);

    public native void GetRightHandMat(long model, long mat);

    public native void GetLeftHandMat(long model, long mat);

    public native long LoadTexture(String filename);

    public native void DeleteTexture(long tex);

    public native int GetTextureX(long tex);

    public native int GetTextureY(long tex);

    public native long GetTextureData(long tex);

    public native boolean TextureHasAlpha(long tex);

    public native long LoadAnimation(long model, String filename);

    public native void DeleteAnimation(long anim);

    public native boolean PreloadFbxFile(String path);

    public native String ListFbxStacks(String path);

    public native void ClearFbxCache();

    public native boolean HasCameraData(long anim);

    public native float GetAnimMaxFrame(long anim);

    public native void GetCameraTransform(long anim, float frame, ByteBuffer buffer);

    public native boolean HasBoneData(long anim);

    public native boolean HasMorphData(long anim);

    public native void MergeAnimation(long target, long source);

    public native void SetHeadAngle(long model, float x, float y, float z, boolean flag);

    public native void SetModelTransform(long model,
        float m00, float m01, float m02, float m03,
        float m10, float m11, float m12, float m13,
        float m20, float m21, float m22, float m23,
        float m30, float m31, float m32, float m33);

    public native void SetModelPositionAndYaw(long model, float posX, float posY, float posZ, float yaw);

    public native void SetEyeAngle(long model, float eyeX, float eyeY);

    public native void SetEyeMaxAngle(long model, float maxAngle);

    public native void SetEyeTrackingEnabled(long model, boolean enabled);

    public native boolean IsEyeTrackingEnabled(long model);

    public native void SetAutoBlinkEnabled(long model, boolean enabled);

    public native boolean IsAutoBlinkEnabled(long model);

    public native void SetBlinkParams(long model, float interval, float duration);

    public native boolean InitPhysics(long model);

    public native void ResetPhysics(long model);

    public native void SetPhysicsEnabled(long model, boolean enabled);

    public native boolean IsPhysicsEnabled(long model);

    public native boolean HasPhysics(long model);

    public native String GetPhysicsDebugInfo(long model);

    public native boolean IsMaterialVisible(long model, int index);

    public native void SetMaterialVisible(long model, int index, boolean visible);

    public native int SetMaterialVisibleByName(long model, String name, boolean visible);

    public native void SetAllMaterialsVisible(long model, boolean visible);

    public native String GetMaterialName(long model, int index);

    public native String GetMaterialNames(long model);

    public native int GetBoneCount(long model);

    public native long GetSkinningMatrices(long model);

    public native int CopySkinningMatricesToBuffer(long model, java.nio.ByteBuffer buffer);

    public native long GetBoneIndices(long model);

    public native int CopyBoneIndicesToBuffer(long model, java.nio.ByteBuffer buffer, int vertexCount);

    public native long GetBoneWeights(long model);

    public native int CopyBoneWeightsToBuffer(long model, java.nio.ByteBuffer buffer, int vertexCount);

    public native long GetOriginalPositions(long model);

    public native int CopyOriginalPositionsToBuffer(long model, java.nio.ByteBuffer buffer, int vertexCount);

    public native long GetOriginalNormals(long model);

    public native int CopyOriginalNormalsToBuffer(long model, java.nio.ByteBuffer buffer, int vertexCount);

    public native String GetGpuSkinningDebugInfo(long model);

    public native void UpdateAnimationOnly(long model, float deltaTime);

    public native void InitGpuSkinningData(long model);

    public native void InitGpuMorphData(long model);

    public native int GetVertexMorphCount(long model);

    public native long GetGpuMorphOffsets(long model);

    public native long GetGpuMorphOffsetsSize(long model);

    public native long GetGpuMorphWeights(long model);

    public native void SyncGpuMorphWeights(long model);

    public native long CopyGpuMorphOffsetsToBuffer(long model, java.nio.ByteBuffer buffer);

    public native int CopyGpuMorphWeightsToBuffer(long model, java.nio.ByteBuffer buffer);

    public native boolean IsGpuMorphInitialized(long model);

    public native int ApplyVpdMorph(long model, String filename);

    public native void ResetAllMorphs(long model);

    public native boolean SetMorphWeightByName(long model, String morphName, float weight);

    public native long GetMorphCount(long model);

    public native String GetMorphName(long model, int index);

    public native float GetMorphWeight(long model, int index);

    public native void SetMorphWeight(long model, int index, float weight);

    public native void InitGpuUvMorphData(long model);

    public native int GetUvMorphCount(long model);

    public native long GetGpuUvMorphOffsetsSize(long model);

    public native long CopyGpuUvMorphOffsetsToBuffer(long model, java.nio.ByteBuffer buffer);

    public native int CopyGpuUvMorphWeightsToBuffer(long model, java.nio.ByteBuffer buffer);

    public native int GetMaterialMorphResultCount(long model);

    public native int CopyMaterialMorphResultsToBuffer(long model, java.nio.ByteBuffer buffer);

    public native int BatchGetSubMeshData(long model, java.nio.ByteBuffer buffer);

    public native void SetPhysicsConfig(
        boolean enabled,
        float gravityY,
        float physicsFps,
        int maxSubstepCount,
        float inertiaStrength,
        float maxLinearVelocity,
        float maxAngularVelocity,
        boolean jointsEnabled,
        boolean kinematicFilter,
        boolean debugLog
    );

    public native void SetFirstPersonMode(long model, boolean enabled);

    public native boolean IsFirstPersonMode(long model);

    public native float GetHeadBonePositionY(long model);

    public native void GetEyeBonePosition(long model, float[] out);

    public native String GetBoneNames(long model);

    public native int CopyBonePositionsToBuffer(long model, java.nio.ByteBuffer buffer);

    public native int CopyRealtimeUVsToBuffer(long model, java.nio.ByteBuffer buffer);

    public native long GetModelMemoryUsage(long model);

    public native void SetVRTrackingData(long model, float[] trackingData);

    public native void SetVREnabled(long model, boolean enabled);

    public native void SetVRIKParams(long model, float armIKStrength);

    public native void SetVRHandMode(long model, int mode);
}
