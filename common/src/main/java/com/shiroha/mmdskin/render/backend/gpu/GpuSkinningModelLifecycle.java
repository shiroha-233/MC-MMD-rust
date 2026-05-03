package com.shiroha.mmdskin.render.backend.gpu;

import com.shiroha.mmdskin.render.shader.ShaderConstants;
import org.lwjgl.opengl.GL46C;
import org.lwjgl.system.MemoryUtil;

/** 文件职责：释放并统计 GPU skinning 模型实例资源。 */
final class GpuSkinningModelLifecycle {
    private GpuSkinningModelLifecycle() {
    }

    static long getVramUsage(GpuSkinningModelInstance target) {
        if (!target.initialized) {
            return 0;
        }

        long total = 0;
        int indexCount = (int) target.nativeBackendPort().getIndexCount(target.nativeModelHandle());
        total += (long) indexCount * target.indexElementSize;
        total += (long) target.vertexCount * 12 * 2;
        total += (long) target.vertexCount * 8;
        total += (long) target.vertexCount * 16 * 2;
        total += (long) target.vertexCount * 16;
        total += (long) target.vertexCount * 8 * 2;
        total += (long) target.vertexCount * 12 * 2;
        total += (long) ShaderConstants.MAX_BONES * 64;
        if (target.vertexMorphCount > 0) {
            total += target.nativeBackendPort().getGpuMorphOffsetsSize(target.nativeModelHandle());
            total += (long) target.vertexMorphCount * 4;
        }
        if (target.uvMorphCount > 0) {
            total += target.nativeBackendPort().getGpuUvMorphOffsetsSize(target.nativeModelHandle());
            total += (long) target.uvMorphCount * 4;
        }
        if (target.skinnedUvBuffer > 0) {
            total += (long) target.vertexCount * 8;
        }
        return total;
    }

    static long getRamUsage(GpuSkinningModelInstance target) {
        if (!target.initialized) {
            return 0;
        }

        long rustRam = target.nativeBackendPort().getModelMemoryUsage(target.nativeModelHandle());
        long javaRam = (long) target.vertexCount * 64;
        javaRam += 128;
        if (target.boneMatricesBuffer != null) {
            javaRam += (long) target.boneMatricesBuffer.capacity() * 4;
        }
        if (target.boneMatricesByteBuffer != null) {
            javaRam += target.boneMatricesByteBuffer.capacity();
        }
        if (target.morphWeightsBuffer != null) {
            javaRam += (long) target.morphWeightsBuffer.capacity() * 4;
        }
        if (target.morphWeightsByteBuffer != null) {
            javaRam += target.morphWeightsByteBuffer.capacity();
        }
        if (target.uvMorphWeightsBuffer != null) {
            javaRam += (long) target.uvMorphWeightsBuffer.capacity() * 4;
        }
        if (target.uvMorphWeightsByteBuffer != null) {
            javaRam += target.uvMorphWeightsByteBuffer.capacity();
        }
        if (target.materialMorphResultCountValue() > 0) {
            javaRam += (long) target.materialMorphResultCountValue() * 56 * 4 * 2;
        }
        if (target.subMeshDataBuf != null) {
            javaRam += target.subMeshDataBuf.capacity();
        }
        return rustRam + javaRam;
    }

    static void dispose(GpuSkinningModelInstance target) {
        if (!target.initialized) {
            return;
        }

        target.initialized = false;
        target.releaseBaseResources();

        GL46C.glDeleteVertexArrays(target.vertexArrayObject);
        GL46C.glDeleteBuffers(target.indexBufferObject);
        GL46C.glDeleteBuffers(target.positionBufferObject);
        GL46C.glDeleteBuffers(target.normalBufferObject);
        GL46C.glDeleteBuffers(target.uv0BufferObject);
        GL46C.glDeleteBuffers(target.boneIndicesBufferObject);
        GL46C.glDeleteBuffers(target.boneWeightsBufferObject);
        GL46C.glDeleteBuffers(target.colorBufferObject);
        GL46C.glDeleteBuffers(target.uv1BufferObject);
        GL46C.glDeleteBuffers(target.uv2BufferObject);
        GL46C.glDeleteBuffers(target.skinnedPositionsBuffer);
        GL46C.glDeleteBuffers(target.skinnedNormalsBuffer);

        if (target.boneMatrixSSBO > 0) GL46C.glDeleteBuffers(target.boneMatrixSSBO);
        if (target.morphOffsetsSSBO > 0) GL46C.glDeleteBuffers(target.morphOffsetsSSBO);
        if (target.morphWeightsSSBO > 0) GL46C.glDeleteBuffers(target.morphWeightsSSBO);
        if (target.uvMorphOffsetsSSBO > 0) GL46C.glDeleteBuffers(target.uvMorphOffsetsSSBO);
        if (target.uvMorphWeightsSSBO > 0) GL46C.glDeleteBuffers(target.uvMorphWeightsSSBO);
        if (target.skinnedUvBuffer > 0) GL46C.glDeleteBuffers(target.skinnedUvBuffer);
        target.boneMatrixSSBO = 0;
        target.morphOffsetsSSBO = 0;
        target.morphWeightsSSBO = 0;
        target.uvMorphOffsetsSSBO = 0;
        target.uvMorphWeightsSSBO = 0;
        target.skinnedUvBuffer = 0;

        if (target.lightMapMaterial != null && target.lightMapMaterial.ownsTexture && target.lightMapMaterial.tex > 0) {
            GL46C.glDeleteTextures(target.lightMapMaterial.tex);
            target.lightMapMaterial.tex = 0;
        }

        if (target.boneMatricesBuffer != null) {
            MemoryUtil.memFree(target.boneMatricesBuffer);
            target.boneMatricesBuffer = null;
        }
        if (target.boneMatricesByteBuffer != null) {
            MemoryUtil.memFree(target.boneMatricesByteBuffer);
            target.boneMatricesByteBuffer = null;
        }
        if (target.morphWeightsBuffer != null) {
            MemoryUtil.memFree(target.morphWeightsBuffer);
            target.morphWeightsBuffer = null;
        }
        target.morphWeightsByteBuffer = null;
        if (target.uvMorphWeightsBuffer != null) {
            MemoryUtil.memFree(target.uvMorphWeightsBuffer);
            target.uvMorphWeightsBuffer = null;
        }
        target.uvMorphWeightsByteBuffer = null;
        if (target.modelViewMatBuff != null) {
            MemoryUtil.memFree(target.modelViewMatBuff);
            target.modelViewMatBuff = null;
        }
        if (target.projMatBuff != null) {
            MemoryUtil.memFree(target.projMatBuff);
            target.projMatBuff = null;
        }
        if (target.subMeshDataBuf != null) {
            MemoryUtil.memFree(target.subMeshDataBuf);
            target.subMeshDataBuf = null;
        }
    }
}
