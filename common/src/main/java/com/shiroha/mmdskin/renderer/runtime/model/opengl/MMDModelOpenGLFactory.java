package com.shiroha.mmdskin.renderer.runtime.model.opengl;

import com.shiroha.mmdskin.NativeFunc;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.shiroha.mmdskin.renderer.runtime.texture.MMDTextureManager;
import com.shiroha.mmdskin.renderer.runtime.model.shared.MMDMaterial;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL46C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

final class MMDModelOpenGLFactory {
    private static final Logger logger = LogManager.getLogger();

    private MMDModelOpenGLFactory() {
    }

    static MMDModelOpenGL create(String modelFilename, String modelDir, boolean isPMD, long layerCount) {
        if (!MMDModelOpenGL.isShaderInited && MMDModelOpenGL.isMMDShaderEnabled) {
            MMDModelOpenGL.InitShader();
        }

        NativeFunc nf = NativeFunc.GetInst();
        long model;
        if (isPMD) {
            model = nf.LoadModelPMD(modelFilename, modelDir, layerCount);
        } else {
            model = nf.LoadModelPMX(modelFilename, modelDir, layerCount);
        }
        if (model == 0) {
            logger.warn(String.format("Cannot open model: '%s'.", modelFilename));
            return null;
        }

        MMDModelOpenGL result = createFromHandle(model, modelDir);
        if (result == null) {
            nf.DeleteModel(model);
        }
        return result;
    }

    static MMDModelOpenGL createFromHandle(long model, String modelDir) {
        if (!MMDModelOpenGL.isShaderInited && MMDModelOpenGL.isMMDShaderEnabled) {
            MMDModelOpenGL.InitShader();
        }

        NativeFunc nf = NativeFunc.GetInst();
        BufferUploader.reset();

        int vertexArrayObject = 0;
        int indexBufferObject = 0;
        int positionBufferObject = 0;
        int colorBufferObject = 0;
        int normalBufferObject = 0;
        int uv0BufferObject = 0;
        int uv1BufferObject = 0;
        int uv2BufferObject = 0;
        MMDMaterial lightMapMaterial = null;
        FloatBuffer modelViewMatBuff = null;
        FloatBuffer projMatBuff = null;
        FloatBuffer light0Buff = null;
        FloatBuffer light1Buff = null;
        ByteBuffer matMorphResultsByteBuf = null;

        try {
            vertexArrayObject = GL46C.glGenVertexArrays();
            indexBufferObject = GL46C.glGenBuffers();
            positionBufferObject = GL46C.glGenBuffers();
            colorBufferObject = GL46C.glGenBuffers();
            normalBufferObject = GL46C.glGenBuffers();
            uv0BufferObject = GL46C.glGenBuffers();
            uv1BufferObject = GL46C.glGenBuffers();
            uv2BufferObject = GL46C.glGenBuffers();

            int vertexCount = (int) nf.GetVertexCount(model);
            ByteBuffer posBuffer = MemoryUtil.memAlloc(vertexCount * 12);
            ByteBuffer colorBuffer = MemoryUtil.memAlloc(vertexCount * 16);
            ByteBuffer norBuffer = MemoryUtil.memAlloc(vertexCount * 12);
            ByteBuffer uv0Buffer = MemoryUtil.memAlloc(vertexCount * 8);
            ByteBuffer uv1Buffer = MemoryUtil.memAlloc(vertexCount * 8);
            ByteBuffer uv2Buffer = MemoryUtil.memAlloc(vertexCount * 8);
            colorBuffer.order(ByteOrder.LITTLE_ENDIAN);
            uv1Buffer.order(ByteOrder.LITTLE_ENDIAN);
            uv2Buffer.order(ByteOrder.LITTLE_ENDIAN);

            GL46C.glBindVertexArray(vertexArrayObject);
            int indexElementSize = (int) nf.GetIndexElementSize(model);
            int indexCount = (int) nf.GetIndexCount(model);
            int indexSize = indexCount * indexElementSize;
            long indexData = nf.GetIndices(model);
            ByteBuffer indexBuffer = MemoryUtil.memAlloc(indexSize);
            nf.CopyDataToByteBuffer(indexBuffer, indexData, indexSize);
            indexBuffer.position(0);
            GL46C.glBindBuffer(GL46C.GL_ELEMENT_ARRAY_BUFFER, indexBufferObject);
            GL46C.glBufferData(GL46C.GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL46C.GL_STATIC_DRAW);
            MemoryUtil.memFree(indexBuffer);

            int indexType = switch (indexElementSize) {
                case 1 -> GL46C.GL_UNSIGNED_BYTE;
                case 2 -> GL46C.GL_UNSIGNED_SHORT;
                case 4 -> GL46C.GL_UNSIGNED_INT;
                default -> 0;
            };

            List<String> texKeys = new ArrayList<>();
            MMDMaterial[] mats = new MMDMaterial[(int) nf.GetMaterialCount(model)];
            for (int i = 0; i < mats.length; ++i) {
                mats[i] = new MMDMaterial();
                String texFilename = nf.GetMaterialTex(model, i);
                if (texFilename != null && !texFilename.isEmpty()) {
                    MMDTextureManager.Texture mgrTex = MMDTextureManager.GetTexture(texFilename);
                    if (mgrTex != null) {
                        mats[i].tex = mgrTex.tex;
                        mats[i].hasAlpha = mgrTex.hasAlpha;
                        MMDTextureManager.addRef(texFilename);
                        texKeys.add(texFilename);
                    }
                }
            }

            lightMapMaterial = new MMDMaterial();
            String lightMapPath = modelDir + "/lightMap.png";
            MMDTextureManager.Texture mgrTex = MMDTextureManager.GetTexture(lightMapPath);
            if (mgrTex != null) {
                lightMapMaterial.tex = mgrTex.tex;
                lightMapMaterial.hasAlpha = mgrTex.hasAlpha;
                MMDTextureManager.addRef(lightMapPath);
                texKeys.add(lightMapPath);
            } else {
                lightMapMaterial.tex = GL46C.glGenTextures();
                lightMapMaterial.ownsTexture = true;
                GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, lightMapMaterial.tex);
                ByteBuffer texBuffer = ByteBuffer.allocateDirect(16 * 16 * 4);
                texBuffer.order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i < 16 * 16; i++) {
                    texBuffer.put((byte) 255);
                    texBuffer.put((byte) 255);
                    texBuffer.put((byte) 255);
                    texBuffer.put((byte) 255);
                }
                texBuffer.flip();
                GL46C.glTexImage2D(GL46C.GL_TEXTURE_2D, 0, GL46C.GL_RGBA, 16, 16, 0, GL46C.GL_RGBA, GL46C.GL_UNSIGNED_BYTE, texBuffer);
                GL46C.glTexParameteri(GL46C.GL_TEXTURE_2D, GL46C.GL_TEXTURE_MAX_LEVEL, 0);
                GL46C.glTexParameteri(GL46C.GL_TEXTURE_2D, GL46C.GL_TEXTURE_MIN_FILTER, GL46C.GL_LINEAR);
                GL46C.glTexParameteri(GL46C.GL_TEXTURE_2D, GL46C.GL_TEXTURE_MAG_FILTER, GL46C.GL_LINEAR);
                GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, 0);
                lightMapMaterial.hasAlpha = true;
            }

            for (int i = 0; i < vertexCount; i++) {
                colorBuffer.putFloat(1.0f);
                colorBuffer.putFloat(1.0f);
                colorBuffer.putFloat(1.0f);
                colorBuffer.putFloat(1.0f);
            }
            colorBuffer.flip();

            for (int i = 0; i < vertexCount; i++) {
                uv1Buffer.putInt(15);
                uv1Buffer.putInt(15);
            }
            uv1Buffer.flip();

            int posAndNorSize = vertexCount * 12;
            int uv0Size = vertexCount * 8;
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, positionBufferObject);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, posAndNorSize, GL46C.GL_DYNAMIC_DRAW);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, normalBufferObject);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, posAndNorSize, GL46C.GL_DYNAMIC_DRAW);
            long uv0Data = nf.GetUVs(model);
            nf.CopyDataToByteBuffer(uv0Buffer, uv0Data, uv0Size);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, uv0BufferObject);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, uv0Buffer, GL46C.GL_DYNAMIC_DRAW);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, uv1BufferObject);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, uv1Buffer, GL46C.GL_STATIC_DRAW);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, colorBufferObject);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, colorBuffer, GL46C.GL_STATIC_DRAW);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, uv2BufferObject);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, vertexCount * 8, GL46C.GL_DYNAMIC_DRAW);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, 0);

            MMDModelOpenGL result = new MMDModelOpenGL();
            result.applyBaseState(model, modelDir, texKeys);
            result.vertexCount = vertexCount;
            result.posBuffer = posBuffer;
            result.colorBuffer = colorBuffer;
            result.norBuffer = norBuffer;
            result.uv0Buffer = uv0Buffer;
            result.uv1Buffer = uv1Buffer;
            result.uv2Buffer = uv2Buffer;
            result.indexBufferObject = indexBufferObject;
            result.vertexBufferObject = positionBufferObject;
            result.colorBufferObject = colorBufferObject;
            result.texcoordBufferObject = uv0BufferObject;
            result.uv1BufferObject = uv1BufferObject;
            result.uv2BufferObject = uv2BufferObject;
            result.normalBufferObject = normalBufferObject;
            result.vertexArrayObject = vertexArrayObject;
            result.indexElementSize = indexElementSize;
            result.indexType = indexType;
            result.mats = mats;
            result.lightMapMaterial = lightMapMaterial;
            result.hasUvMorph = nf.GetUvMorphCount(model) > 0;

            modelViewMatBuff = MemoryUtil.memAllocFloat(16);
            projMatBuff = MemoryUtil.memAllocFloat(16);
            light0Buff = MemoryUtil.memAllocFloat(3);
            light1Buff = MemoryUtil.memAllocFloat(3);
            result.modelViewMatBuff = modelViewMatBuff;
            result.projMatBuff = projMatBuff;
            result.light0Buff = light0Buff;
            result.light1Buff = light1Buff;

            int matMorphCount = nf.GetMaterialMorphResultCount(model);
            if (matMorphCount > 0) {
                int floatCount = matMorphCount * 56;
                matMorphResultsByteBuf = MemoryUtil.memAlloc(floatCount * 4);
                matMorphResultsByteBuf.order(ByteOrder.LITTLE_ENDIAN);
                result.applyMaterialMorphState(matMorphCount, matMorphResultsByteBuf);
            }

            result.subMeshCount = (int) nf.GetSubMeshCount(model);
            result.subMeshDataBuf = MemoryUtil.memAlloc(result.subMeshCount * 20);
            result.subMeshDataBuf.order(ByteOrder.LITTLE_ENDIAN);

            nf.SetAutoBlinkEnabled(model, true);
            return result;
        } catch (Exception e) {
            logger.error("CPU 蒙皮模型创建失败，清理资源: {}", e.getMessage());

            if (vertexArrayObject > 0) GL46C.glDeleteVertexArrays(vertexArrayObject);
            if (indexBufferObject > 0) GL46C.glDeleteBuffers(indexBufferObject);
            if (positionBufferObject > 0) GL46C.glDeleteBuffers(positionBufferObject);
            if (colorBufferObject > 0) GL46C.glDeleteBuffers(colorBufferObject);
            if (normalBufferObject > 0) GL46C.glDeleteBuffers(normalBufferObject);
            if (uv0BufferObject > 0) GL46C.glDeleteBuffers(uv0BufferObject);
            if (uv1BufferObject > 0) GL46C.glDeleteBuffers(uv1BufferObject);
            if (uv2BufferObject > 0) GL46C.glDeleteBuffers(uv2BufferObject);
            if (lightMapMaterial != null && lightMapMaterial.ownsTexture && lightMapMaterial.tex > 0) {
                GL46C.glDeleteTextures(lightMapMaterial.tex);
            }
            if (modelViewMatBuff != null) MemoryUtil.memFree(modelViewMatBuff);
            if (projMatBuff != null) MemoryUtil.memFree(projMatBuff);
            if (light0Buff != null) MemoryUtil.memFree(light0Buff);
            if (light1Buff != null) MemoryUtil.memFree(light1Buff);
            if (matMorphResultsByteBuf != null) MemoryUtil.memFree(matMorphResultsByteBuf);
            return null;
        }
    }
}
