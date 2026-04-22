package com.shiroha.mmdskin.bridge.runtime;

import com.shiroha.mmdskin.NativeFunc;
import java.nio.ByteBuffer;

/** 文件职责：集中封装渲染运行时需要的 native 调用。 */
public final class NativeRuntimeBridge implements
        NativeRuntimePort,
        NativeModelPort,
        NativeModelLoadPort,
        NativeRenderBackendPort,
        NativeMatrixPort,
        NativeModelQueryPort,
        NativeMorphPort,
        NativeScenePort,
        PlatformCapabilityPort {

    private NativeFunc nativeFunc() {
        return NativeFunc.GetInst();
    }

    @Override
    public long createMatrix() {
        return nativeFunc().CreateMat();
    }

    @Override
    public void deleteMatrix(long matrixHandle) {
        nativeFunc().DeleteMat(matrixHandle);
    }

    @Override
    public boolean setLayerBoneMask(long modelHandle, int layer, String rootBoneName) {
        return nativeFunc().SetLayerBoneMask(modelHandle, layer, rootBoneName);
    }

    @Override
    public boolean setLayerBoneExclude(long modelHandle, int layer, String rootBoneName) {
        return nativeFunc().SetLayerBoneExclude(modelHandle, layer, rootBoneName);
    }

    @Override
    public long getModelMemoryUsage(long modelHandle) {
        return nativeFunc().GetModelMemoryUsage(modelHandle);
    }

    @Override
    public void changeModelAnimation(long modelHandle, long animationHandle, long layer) {
        nativeFunc().ChangeModelAnim(modelHandle, animationHandle, layer);
    }

    @Override
    public void transitionLayerTo(long modelHandle, long layer, long animationHandle, float transitionTime) {
        nativeFunc().TransitionLayerTo(modelHandle, layer, animationHandle, transitionTime);
    }

    @Override
    public void setLayerLoop(long modelHandle, long layer, boolean loop) {
        nativeFunc().SetLayerLoop(modelHandle, layer, loop);
    }

    @Override
    public void resetModelPhysics(long modelHandle) {
        nativeFunc().ResetModelPhysics(modelHandle);
    }

    @Override
    public void setPhysicsEnabled(long modelHandle, boolean enabled) {
        nativeFunc().SetPhysicsEnabled(modelHandle, enabled);
    }

    @Override
    public void updateModel(long modelHandle, float deltaTime) {
        nativeFunc().UpdateModel(modelHandle, deltaTime);
    }

    @Override
    public void updateAnimationOnly(long modelHandle, float deltaTime) {
        nativeFunc().UpdateAnimationOnly(modelHandle, deltaTime);
    }

    @Override
    public long loadPmxModel(String modelFilePath, String modelDir, long layerCount) {
        return nativeFunc().LoadModelPMX(modelFilePath, modelDir, layerCount);
    }

    @Override
    public long loadPmdModel(String modelFilePath, String modelDir, long layerCount) {
        return nativeFunc().LoadModelPMD(modelFilePath, modelDir, layerCount);
    }

    @Override
    public long loadVrmModel(String modelFilePath, String modelDir, long layerCount) {
        return nativeFunc().LoadModelVRM(modelFilePath, modelDir, layerCount);
    }

    @Override
    public void populateHandMatrix(long modelHandle, long handMatrixHandle, boolean mainHand) {
        if (mainHand) {
            nativeFunc().GetRightHandMat(modelHandle, handMatrixHandle);
        } else {
            nativeFunc().GetLeftHandMat(modelHandle, handMatrixHandle);
        }
    }

    @Override
    public boolean copyMatrixToBuffer(long matrixHandle, ByteBuffer targetBuffer) {
        return nativeFunc().CopyMatToBuffer(matrixHandle, targetBuffer);
    }

    @Override
    public void setFirstPersonMode(long modelHandle, boolean enabled) {
        nativeFunc().SetFirstPersonMode(modelHandle, enabled);
    }

    @Override
    public void getEyeBonePosition(long modelHandle, float[] output) {
        nativeFunc().GetEyeBonePosition(modelHandle, output);
    }

    @Override
    public void applyVrTrackingInput(long modelHandle, float[] trackingData) {
        nativeFunc().ApplyVRTrackingInput(modelHandle, trackingData);
    }

    @Override
    public void setVrEnabled(long modelHandle, boolean enabled) {
        nativeFunc().SetVREnabled(modelHandle, enabled);
    }

    @Override
    public void setVrIkParams(long modelHandle, float armIkStrength) {
        nativeFunc().SetVRIKParams(modelHandle, armIkStrength);
    }

    @Override
    public boolean isAndroid() {
        return NativeFunc.isAndroid();
    }

    @Override
    public int getMaterialCount(long modelHandle) {
        return (int) nativeFunc().GetMaterialCount(modelHandle);
    }

    @Override
    public void setMaterialVisible(long modelHandle, int materialIndex, boolean visible) {
        nativeFunc().SetMaterialVisible(modelHandle, materialIndex, visible);
    }

    @Override
    public void setAllMaterialsVisible(long modelHandle, boolean visible) {
        nativeFunc().SetAllMaterialsVisible(modelHandle, visible);
    }

    @Override
    public void deleteModel(long modelHandle) {
        nativeFunc().DeleteModel(modelHandle);
    }

    @Override
    public int getBoneCount(long modelHandle) {
        return nativeFunc().GetBoneCount(modelHandle);
    }

    @Override
    public long getVertexCount(long modelHandle) {
        return nativeFunc().GetVertexCount(modelHandle);
    }

    @Override
    public long getIndexCount(long modelHandle) {
        return nativeFunc().GetIndexCount(modelHandle);
    }

    @Override
    public String getBoneNames(long modelHandle) {
        return nativeFunc().GetBoneNames(modelHandle);
    }

    @Override
    public int getIndexElementSize(long modelHandle) {
        return (int) nativeFunc().GetIndexElementSize(modelHandle);
    }

    @Override
    public long getIndexDataAddress(long modelHandle) {
        return nativeFunc().GetIndices(modelHandle);
    }

    @Override
    public long getPositionDataAddress(long modelHandle) {
        return nativeFunc().GetPoss(modelHandle);
    }

    @Override
    public long getNormalDataAddress(long modelHandle) {
        return nativeFunc().GetNormals(modelHandle);
    }

    @Override
    public long getUvDataAddress(long modelHandle) {
        return nativeFunc().GetUVs(modelHandle);
    }

    @Override
    public void copyNativeDataToBuffer(ByteBuffer targetBuffer, long sourceAddress, int size) {
        nativeFunc().CopyDataToByteBuffer(targetBuffer, sourceAddress, size);
    }

    @Override
    public int copyBonePositionsToBuffer(long modelHandle, ByteBuffer targetBuffer) {
        return nativeFunc().CopyBonePositionsToBuffer(modelHandle, targetBuffer);
    }

    @Override
    public int copyOriginalPositionsToBuffer(long modelHandle, ByteBuffer targetBuffer, int vertexCount) {
        return nativeFunc().CopyOriginalPositionsToBuffer(modelHandle, targetBuffer, vertexCount);
    }

    @Override
    public int copyOriginalNormalsToBuffer(long modelHandle, ByteBuffer targetBuffer, int vertexCount) {
        return nativeFunc().CopyOriginalNormalsToBuffer(modelHandle, targetBuffer, vertexCount);
    }

    @Override
    public int copyBoneIndicesToBuffer(long modelHandle, ByteBuffer targetBuffer, int vertexCount) {
        return nativeFunc().CopyBoneIndicesToBuffer(modelHandle, targetBuffer, vertexCount);
    }

    @Override
    public int copyBoneWeightsToBuffer(long modelHandle, ByteBuffer targetBuffer, int vertexCount) {
        return nativeFunc().CopyBoneWeightsToBuffer(modelHandle, targetBuffer, vertexCount);
    }

    @Override
    public int copySkinningMatricesToBuffer(long modelHandle, ByteBuffer targetBuffer) {
        return nativeFunc().CopySkinningMatricesToBuffer(modelHandle, targetBuffer);
    }

    @Override
    public int copyRealtimeUvsToBuffer(long modelHandle, ByteBuffer targetBuffer) {
        return nativeFunc().CopyRealtimeUVsToBuffer(modelHandle, targetBuffer);
    }

    @Override
    public int getVertexMorphCount(long modelHandle) {
        return nativeFunc().GetVertexMorphCount(modelHandle);
    }

    @Override
    public int getUvMorphCount(long modelHandle) {
        return nativeFunc().GetUvMorphCount(modelHandle);
    }

    @Override
    public long getGpuMorphOffsetsSize(long modelHandle) {
        return nativeFunc().GetGpuMorphOffsetsSize(modelHandle);
    }

    @Override
    public long copyGpuMorphOffsetsToBuffer(long modelHandle, ByteBuffer targetBuffer) {
        return nativeFunc().CopyGpuMorphOffsetsToBuffer(modelHandle, targetBuffer);
    }

    @Override
    public int copyGpuMorphWeightsToBuffer(long modelHandle, ByteBuffer targetBuffer) {
        return nativeFunc().CopyGpuMorphWeightsToBuffer(modelHandle, targetBuffer);
    }

    @Override
    public long getGpuUvMorphOffsetsSize(long modelHandle) {
        return nativeFunc().GetGpuUvMorphOffsetsSize(modelHandle);
    }

    @Override
    public long copyGpuUvMorphOffsetsToBuffer(long modelHandle, ByteBuffer targetBuffer) {
        return nativeFunc().CopyGpuUvMorphOffsetsToBuffer(modelHandle, targetBuffer);
    }

    @Override
    public int copyGpuUvMorphWeightsToBuffer(long modelHandle, ByteBuffer targetBuffer) {
        return nativeFunc().CopyGpuUvMorphWeightsToBuffer(modelHandle, targetBuffer);
    }

    @Override
    public void initGpuSkinningData(long modelHandle) {
        nativeFunc().InitGpuSkinningData(modelHandle);
    }

    @Override
    public void initGpuMorphData(long modelHandle) {
        nativeFunc().InitGpuMorphData(modelHandle);
    }

    @Override
    public void initGpuUvMorphData(long modelHandle) {
        nativeFunc().InitGpuUvMorphData(modelHandle);
    }

    @Override
    public int getMorphCount(long modelHandle) {
        return (int) nativeFunc().GetMorphCount(modelHandle);
    }

    @Override
    public String getMorphName(long modelHandle, int morphIndex) {
        return nativeFunc().GetMorphName(modelHandle, morphIndex);
    }

    @Override
    public boolean isVrmModel(long modelHandle) {
        return nativeFunc().IsVrmModel(modelHandle);
    }

    @Override
    public String getMaterialName(long modelHandle, int materialIndex) {
        return nativeFunc().GetMaterialName(modelHandle, materialIndex);
    }

    @Override
    public String getMaterialTexturePath(long modelHandle, int materialIndex) {
        return nativeFunc().GetMaterialTex(modelHandle, materialIndex);
    }

    @Override
    public boolean isMaterialVisible(long modelHandle, int materialIndex) {
        return nativeFunc().IsMaterialVisible(modelHandle, materialIndex);
    }

    @Override
    public int getMaterialMorphResultCount(long modelHandle) {
        return nativeFunc().GetMaterialMorphResultCount(modelHandle);
    }

    @Override
    public int copyMaterialMorphResultsToBuffer(long modelHandle, ByteBuffer targetBuffer) {
        return nativeFunc().CopyMaterialMorphResultsToBuffer(modelHandle, targetBuffer);
    }

    @Override
    public int getSubMeshCount(long modelHandle) {
        return (int) nativeFunc().GetSubMeshCount(modelHandle);
    }

    @Override
    public int batchGetSubMeshData(long modelHandle, ByteBuffer targetBuffer) {
        return nativeFunc().BatchGetSubMeshData(modelHandle, targetBuffer);
    }

    @Override
    public void resetAllMorphs(long modelHandle) {
        nativeFunc().ResetAllMorphs(modelHandle);
    }

    @Override
    public void setMorphWeight(long modelHandle, int morphIndex, float weight) {
        nativeFunc().SetMorphWeight(modelHandle, morphIndex, weight);
    }

    @Override
    public void syncGpuMorphWeights(long modelHandle) {
        nativeFunc().SyncGpuMorphWeights(modelHandle);
    }

    @Override
    public int applyVpdMorph(long modelHandle, String filePath) {
        return nativeFunc().ApplyVpdMorph(modelHandle, filePath);
    }

    @Override
    public void setHeadAngle(long modelHandle, float x, float y, float z, boolean worldSpace) {
        nativeFunc().SetHeadAngle(modelHandle, x, y, z, worldSpace);
    }

    @Override
    public void setModelPositionAndYaw(long modelHandle, float x, float y, float z, float yawRadians) {
        nativeFunc().SetModelPositionAndYaw(modelHandle, x, y, z, yawRadians);
    }

    @Override
    public void setAutoBlinkEnabled(long modelHandle, boolean enabled) {
        nativeFunc().SetAutoBlinkEnabled(modelHandle, enabled);
    }

    @Override
    public void setEyeTrackingEnabled(long modelHandle, boolean enabled) {
        nativeFunc().SetEyeTrackingEnabled(modelHandle, enabled);
    }

    @Override
    public void setEyeMaxAngle(long modelHandle, float maxAngle) {
        nativeFunc().SetEyeMaxAngle(modelHandle, maxAngle);
    }

    @Override
    public void setEyeAngle(long modelHandle, float eyeX, float eyeY) {
        nativeFunc().SetEyeAngle(modelHandle, eyeX, eyeY);
    }
}
