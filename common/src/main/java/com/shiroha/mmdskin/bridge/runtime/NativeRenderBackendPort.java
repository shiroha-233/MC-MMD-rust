package com.shiroha.mmdskin.bridge.runtime;

import java.nio.ByteBuffer;

/** 文件职责：定义渲染后端读取模型数据与驱动 native 更新的能力边界。 */
public interface NativeRenderBackendPort extends NativeModelPort, NativeModelLoadPort, NativeModelQueryPort, NativeScenePort {

    void changeModelAnimation(long modelHandle, long animationHandle, long layer);

    void transitionLayerTo(long modelHandle, long layer, long animationHandle, float transitionTime);

    void setLayerLoop(long modelHandle, long layer, boolean loop);

    void resetModelPhysics(long modelHandle);

    void setPhysicsEnabled(long modelHandle, boolean enabled);

    void updateModel(long modelHandle, float deltaTime);

    void updateAnimationOnly(long modelHandle, float deltaTime);

    int getIndexElementSize(long modelHandle);

    long getIndexDataAddress(long modelHandle);

    long getPositionDataAddress(long modelHandle);

    long getNormalDataAddress(long modelHandle);

    long getUvDataAddress(long modelHandle);

    void copyNativeDataToBuffer(ByteBuffer targetBuffer, long sourceAddress, int size);

    int copyOriginalPositionsToBuffer(long modelHandle, ByteBuffer targetBuffer, int vertexCount);

    int copyOriginalNormalsToBuffer(long modelHandle, ByteBuffer targetBuffer, int vertexCount);

    int copyBoneIndicesToBuffer(long modelHandle, ByteBuffer targetBuffer, int vertexCount);

    int copyBoneWeightsToBuffer(long modelHandle, ByteBuffer targetBuffer, int vertexCount);

    int copySkinningMatricesToBuffer(long modelHandle, ByteBuffer targetBuffer);

    void initGpuSkinningData(long modelHandle);

    void initGpuMorphData(long modelHandle);

    void initGpuUvMorphData(long modelHandle);

    long copyGpuMorphOffsetsToBuffer(long modelHandle, ByteBuffer targetBuffer);

    int copyGpuMorphWeightsToBuffer(long modelHandle, ByteBuffer targetBuffer);

    long copyGpuUvMorphOffsetsToBuffer(long modelHandle, ByteBuffer targetBuffer);

    int copyGpuUvMorphWeightsToBuffer(long modelHandle, ByteBuffer targetBuffer);

    int getMaterialMorphResultCount(long modelHandle);

    int copyMaterialMorphResultsToBuffer(long modelHandle, ByteBuffer targetBuffer);

    int getSubMeshCount(long modelHandle);

    int batchGetSubMeshData(long modelHandle, ByteBuffer targetBuffer);
}
