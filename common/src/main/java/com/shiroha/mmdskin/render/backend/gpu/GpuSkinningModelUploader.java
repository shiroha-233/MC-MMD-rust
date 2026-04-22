package com.shiroha.mmdskin.render.backend.gpu;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.MemoryUtil;

/** 文件职责：同步 GPU skinning 模型实例需要的骨骼与 morph 数据。 */
final class GpuSkinningModelUploader {
    private static final Logger logger = LogManager.getLogger();

    private GpuSkinningModelUploader() {
    }

    static void uploadBoneMatrices(GpuSkinningModelInstance target) {
        var nativeBackend = target.nativeBackendPort();
        target.boneMatricesByteBuffer.clear();

        int copiedBones = nativeBackend.copySkinningMatricesToBuffer(target.nativeModelHandle(), target.boneMatricesByteBuffer);
        if (copiedBones == 0) {
            return;
        }

        target.boneMatricesBuffer.clear();
        target.boneMatricesByteBuffer.position(0);
        FloatBuffer floatView = target.boneMatricesByteBuffer.asFloatBuffer();
        floatView.limit(copiedBones * 16);
        target.boneMatricesBuffer.put(floatView);
        target.boneMatricesBuffer.flip();

        GpuSkinningModelInstance.computeShader.uploadBoneMatrices(target.boneMatrixSSBO, target.boneMatricesBuffer, copiedBones);
    }

    static void uploadMorphData(GpuSkinningModelInstance target) {
        var nativeBackend = target.nativeBackendPort();
        if (target.vertexMorphCount <= 0) {
            return;
        }

        if (!target.morphDataUploaded) {
            long offsetsSize = nativeBackend.getGpuMorphOffsetsSize(target.nativeModelHandle());
            if (offsetsSize > 0) {
                if (offsetsSize > Integer.MAX_VALUE) {
                    logger.error("Morph data is too large ({} bytes); skipping GPU morph", offsetsSize);
                    target.vertexMorphCount = 0;
                } else {
                    ByteBuffer offsetsBuffer = MemoryUtil.memAlloc((int) offsetsSize);
                    offsetsBuffer.order(ByteOrder.LITTLE_ENDIAN);
                    try {
                        nativeBackend.copyGpuMorphOffsetsToBuffer(target.nativeModelHandle(), offsetsBuffer);
                        GpuSkinningModelInstance.computeShader.uploadMorphOffsets(target.morphOffsetsSSBO, offsetsBuffer);
                        target.morphDataUploaded = true;
                    } finally {
                        MemoryUtil.memFree(offsetsBuffer);
                    }
                }
            }
        }

        if (target.morphWeightsBuffer != null && target.morphWeightsByteBuffer != null) {
            target.morphWeightsByteBuffer.clear();
            nativeBackend.copyGpuMorphWeightsToBuffer(target.nativeModelHandle(), target.morphWeightsByteBuffer);
            target.morphWeightsBuffer.clear();
            target.morphWeightsByteBuffer.position(0);
            target.morphWeightsBuffer.put(target.morphWeightsByteBuffer.asFloatBuffer());
            target.morphWeightsBuffer.flip();
            GpuSkinningModelInstance.computeShader.updateMorphWeights(target.morphWeightsSSBO, target.morphWeightsBuffer);
        }
    }

    static void uploadUvMorphData(GpuSkinningModelInstance target) {
        var nativeBackend = target.nativeBackendPort();
        if (target.uvMorphCount <= 0) {
            return;
        }

        if (!target.uvMorphDataUploaded) {
            long offsetsSize = nativeBackend.getGpuUvMorphOffsetsSize(target.nativeModelHandle());
            if (offsetsSize > 0 && offsetsSize <= Integer.MAX_VALUE) {
                ByteBuffer offsetsBuffer = MemoryUtil.memAlloc((int) offsetsSize);
                offsetsBuffer.order(ByteOrder.LITTLE_ENDIAN);
                try {
                    nativeBackend.copyGpuUvMorphOffsetsToBuffer(target.nativeModelHandle(), offsetsBuffer);
                    GpuSkinningModelInstance.computeShader.uploadUvMorphOffsets(target.uvMorphOffsetsSSBO, offsetsBuffer);
                    target.uvMorphDataUploaded = true;
                } finally {
                    MemoryUtil.memFree(offsetsBuffer);
                }
            }
        }

        if (target.uvMorphWeightsBuffer != null && target.uvMorphWeightsByteBuffer != null) {
            target.uvMorphWeightsByteBuffer.clear();
            nativeBackend.copyGpuUvMorphWeightsToBuffer(target.nativeModelHandle(), target.uvMorphWeightsByteBuffer);
            target.uvMorphWeightsBuffer.clear();
            target.uvMorphWeightsByteBuffer.position(0);
            target.uvMorphWeightsBuffer.put(target.uvMorphWeightsByteBuffer.asFloatBuffer());
            target.uvMorphWeightsBuffer.flip();
            GpuSkinningModelInstance.computeShader.updateUvMorphWeights(target.uvMorphWeightsSSBO, target.uvMorphWeightsBuffer);
        }
    }
}
