package com.shiroha.mmdskin.renderer.runtime.model.opengl;

import com.shiroha.mmdskin.NativeFunc;
import org.lwjgl.opengl.GL46C;
import org.lwjgl.system.MemoryUtil;

final class MMDModelOpenGLLifecycle {
    private MMDModelOpenGLLifecycle() {
    }

    static void dispose(MMDModelOpenGL target) {
        target.releaseBaseResources();

        if (target.posBuffer != null) { MemoryUtil.memFree(target.posBuffer); target.posBuffer = null; }
        if (target.colorBuffer != null) { MemoryUtil.memFree(target.colorBuffer); target.colorBuffer = null; }
        if (target.norBuffer != null) { MemoryUtil.memFree(target.norBuffer); target.norBuffer = null; }
        if (target.uv0Buffer != null) { MemoryUtil.memFree(target.uv0Buffer); target.uv0Buffer = null; }
        if (target.uv1Buffer != null) { MemoryUtil.memFree(target.uv1Buffer); target.uv1Buffer = null; }
        if (target.uv2Buffer != null) { MemoryUtil.memFree(target.uv2Buffer); target.uv2Buffer = null; }

        if (target.modelViewMatBuff != null) { MemoryUtil.memFree(target.modelViewMatBuff); target.modelViewMatBuff = null; }
        if (target.projMatBuff != null) { MemoryUtil.memFree(target.projMatBuff); target.projMatBuff = null; }
        if (target.light0Buff != null) { MemoryUtil.memFree(target.light0Buff); target.light0Buff = null; }
        if (target.light1Buff != null) { MemoryUtil.memFree(target.light1Buff); target.light1Buff = null; }
        if (target.subMeshDataBuf != null) { MemoryUtil.memFree(target.subMeshDataBuf); target.subMeshDataBuf = null; }

        if (target.lightMapMaterial != null && target.lightMapMaterial.ownsTexture && target.lightMapMaterial.tex > 0) {
            GL46C.glDeleteTextures(target.lightMapMaterial.tex);
            target.lightMapMaterial.tex = 0;
        }

        GL46C.glDeleteVertexArrays(target.vertexArrayObject);
        GL46C.glDeleteBuffers(target.indexBufferObject);
        GL46C.glDeleteBuffers(target.vertexBufferObject);
        GL46C.glDeleteBuffers(target.colorBufferObject);
        GL46C.glDeleteBuffers(target.normalBufferObject);
        GL46C.glDeleteBuffers(target.texcoordBufferObject);
        GL46C.glDeleteBuffers(target.uv1BufferObject);
        GL46C.glDeleteBuffers(target.uv2BufferObject);
    }

    static long getVramUsage(MMDModelOpenGL target) {
        long total = 0;
        int indexCount = (int) NativeFunc.GetInst().GetIndexCount(target.nativeModelHandle());
        total += (long) indexCount * target.indexElementSize;
        total += (long) target.vertexCount * 12 * 2;
        total += (long) target.vertexCount * 16;
        total += (long) target.vertexCount * 8 * 3;
        return total;
    }

    static long getRamUsage(MMDModelOpenGL target) {
        if (target.nativeModelHandle() == 0) {
            return 0;
        }

        long rustRam = NativeFunc.GetInst().GetModelMemoryUsage(target.nativeModelHandle());
        long javaRam = (long) target.vertexCount * 64;
        javaRam += 152;
        if (target.materialMorphResultCountValue() > 0) {
            javaRam += (long) target.materialMorphResultCountValue() * 56 * 4 * 2;
        }
        return rustRam + javaRam;
    }
}
