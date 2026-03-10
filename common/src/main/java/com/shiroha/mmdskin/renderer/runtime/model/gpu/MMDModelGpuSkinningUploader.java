package com.shiroha.mmdskin.renderer.runtime.model.gpu;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

final class MMDModelGpuSkinningUploader {
    private static final Logger logger = LogManager.getLogger();

    private MMDModelGpuSkinningUploader() {
    }

    static void uploadBoneMatrices(MMDModelGpuSkinning target) {
        var nativeFunc = target.nativeFunc();
        target.boneMatricesByteBuffer.clear();

        int copiedBones = nativeFunc.CopySkinningMatricesToBuffer(target.nativeModelHandle(), target.boneMatricesByteBuffer);
        if (copiedBones == 0) {
            return;
        }

        target.boneMatricesBuffer.clear();
        target.boneMatricesByteBuffer.position(0);
        FloatBuffer floatView = target.boneMatricesByteBuffer.asFloatBuffer();
        floatView.limit(copiedBones * 16);
        target.boneMatricesBuffer.put(floatView);
        target.boneMatricesBuffer.flip();

        MMDModelGpuSkinning.computeShader.uploadBoneMatrices(target.boneMatrixSSBO, target.boneMatricesBuffer, copiedBones);
    }

    static void uploadMorphData(MMDModelGpuSkinning target) {
        var nativeFunc = target.nativeFunc();
        if (target.vertexMorphCount <= 0) {
            return;
        }

        if (!target.morphDataUploaded) {
            long offsetsSize = nativeFunc.GetGpuMorphOffsetsSize(target.nativeModelHandle());
            if (offsetsSize > 0) {
                if (offsetsSize > Integer.MAX_VALUE) {
                    logger.error("Morph 数据过大 ({} bytes)，超过 2GB 限制，跳过 GPU Morph", offsetsSize);
                    target.vertexMorphCount = 0;
                } else {
                    ByteBuffer offsetsBuffer = MemoryUtil.memAlloc((int) offsetsSize);
                    offsetsBuffer.order(ByteOrder.LITTLE_ENDIAN);
                    try {
                        nativeFunc.CopyGpuMorphOffsetsToBuffer(target.nativeModelHandle(), offsetsBuffer);
                        MMDModelGpuSkinning.computeShader.uploadMorphOffsets(target.morphOffsetsSSBO, offsetsBuffer);
                        target.morphDataUploaded = true;
                    } finally {
                        MemoryUtil.memFree(offsetsBuffer);
                    }
                }
            }
        }

        if (target.morphWeightsBuffer != null && target.morphWeightsByteBuffer != null) {
            target.morphWeightsByteBuffer.clear();
            nativeFunc.CopyGpuMorphWeightsToBuffer(target.nativeModelHandle(), target.morphWeightsByteBuffer);
            target.morphWeightsBuffer.clear();
            target.morphWeightsByteBuffer.position(0);
            target.morphWeightsBuffer.put(target.morphWeightsByteBuffer.asFloatBuffer());
            target.morphWeightsBuffer.flip();
            MMDModelGpuSkinning.computeShader.updateMorphWeights(target.morphWeightsSSBO, target.morphWeightsBuffer);
        }
    }

    static void uploadUvMorphData(MMDModelGpuSkinning target) {
        var nativeFunc = target.nativeFunc();
        if (target.uvMorphCount <= 0) {
            return;
        }

        if (!target.uvMorphDataUploaded) {
            long offsetsSize = nativeFunc.GetGpuUvMorphOffsetsSize(target.nativeModelHandle());
            if (offsetsSize > 0 && offsetsSize <= Integer.MAX_VALUE) {
                ByteBuffer offsetsBuffer = MemoryUtil.memAlloc((int) offsetsSize);
                offsetsBuffer.order(ByteOrder.LITTLE_ENDIAN);
                try {
                    nativeFunc.CopyGpuUvMorphOffsetsToBuffer(target.nativeModelHandle(), offsetsBuffer);
                    MMDModelGpuSkinning.computeShader.uploadUvMorphOffsets(target.uvMorphOffsetsSSBO, offsetsBuffer);
                    target.uvMorphDataUploaded = true;
                } finally {
                    MemoryUtil.memFree(offsetsBuffer);
                }
            }
        }

        if (target.uvMorphWeightsBuffer != null && target.uvMorphWeightsByteBuffer != null) {
            target.uvMorphWeightsByteBuffer.clear();
            nativeFunc.CopyGpuUvMorphWeightsToBuffer(target.nativeModelHandle(), target.uvMorphWeightsByteBuffer);
            target.uvMorphWeightsBuffer.clear();
            target.uvMorphWeightsByteBuffer.position(0);
            target.uvMorphWeightsBuffer.put(target.uvMorphWeightsByteBuffer.asFloatBuffer());
            target.uvMorphWeightsBuffer.flip();
            MMDModelGpuSkinning.computeShader.updateUvMorphWeights(target.uvMorphWeightsSSBO, target.uvMorphWeightsBuffer);
        }
    }
}
