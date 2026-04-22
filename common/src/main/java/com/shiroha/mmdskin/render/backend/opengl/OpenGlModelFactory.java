package com.shiroha.mmdskin.render.backend.opengl;

import com.mojang.blaze3d.vertex.BufferUploader;
import com.shiroha.mmdskin.bridge.runtime.NativeRuntimeBridgeHolder;
import com.shiroha.mmdskin.render.bootstrap.ClientRenderRuntime;
import com.shiroha.mmdskin.render.material.ModelMaterial;
import com.shiroha.mmdskin.texture.runtime.TextureRepository;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL46C;
import org.lwjgl.system.MemoryUtil;

/** 文件职责：创建 CPU/OpenGL 蒙皮模型实例。 */
final class OpenGlModelFactory {
    private static final Logger logger = LogManager.getLogger();

    private OpenGlModelFactory() {
    }

    static OpenGlModelInstance create(String modelFilename, String modelDir, boolean isPMD, long layerCount) {
        ensureShaderInitialized();

        var nativeBackend = NativeRuntimeBridgeHolder.get();
        long model;
        if (isPMD) {
            model = nativeBackend.loadPmdModel(modelFilename, modelDir, layerCount);
        } else {
            model = nativeBackend.loadPmxModel(modelFilename, modelDir, layerCount);
        }
        if (model == 0) {
            logger.warn("Cannot open model: '{}'.", modelFilename);
            return null;
        }

        OpenGlModelInstance result = createFromHandle(model, modelDir);
        if (result == null) {
            nativeBackend.deleteModel(model);
        }
        return result;
    }

    static OpenGlModelInstance createFromHandle(long model, String modelDir) {
        ensureShaderInitialized();

        var nativeBackend = NativeRuntimeBridgeHolder.get();
        BufferUploader.reset();

        int vertexArrayObject = 0;
        int indexBufferObject = 0;
        int positionBufferObject = 0;
        int colorBufferObject = 0;
        int normalBufferObject = 0;
        int uv0BufferObject = 0;
        int uv1BufferObject = 0;
        int uv2BufferObject = 0;
        ModelMaterial lightMapMaterial = null;
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

            int vertexCount = (int) nativeBackend.getVertexCount(model);
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
            int indexElementSize = nativeBackend.getIndexElementSize(model);
            int indexCount = (int) nativeBackend.getIndexCount(model);
            int indexSize = indexCount * indexElementSize;
            long indexData = nativeBackend.getIndexDataAddress(model);
            ByteBuffer indexBuffer = MemoryUtil.memAlloc(indexSize);
            nativeBackend.copyNativeDataToBuffer(indexBuffer, indexData, indexSize);
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
            ModelMaterial[] mats = new ModelMaterial[nativeBackend.getMaterialCount(model)];
            for (int i = 0; i < mats.length; ++i) {
                mats[i] = new ModelMaterial();
                mats[i].name = nativeBackend.getMaterialName(model, i);
                String texFilename = nativeBackend.getMaterialTexturePath(model, i);
                mats[i].texturePath = texFilename != null ? texFilename : "";
                if (texFilename != null && !texFilename.isEmpty()) {
                    TextureRepository.Texture mgrTex = TextureRepository.GetTexture(texFilename);
                    if (mgrTex != null) {
                        mats[i].tex = mgrTex.tex;
                        mats[i].hasAlpha = mgrTex.hasAlpha;
                        TextureRepository.addRef(texFilename);
                        texKeys.add(texFilename);
                    }
                }
                mats[i].updateOutlinePolicy();
            }

            lightMapMaterial = new ModelMaterial();
            String lightMapPath = modelDir + "/lightMap.png";
            TextureRepository.Texture mgrTex = TextureRepository.GetTexture(lightMapPath);
            if (mgrTex != null) {
                lightMapMaterial.tex = mgrTex.tex;
                lightMapMaterial.hasAlpha = mgrTex.hasAlpha;
                TextureRepository.addRef(lightMapPath);
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
            long uv0Data = nativeBackend.getUvDataAddress(model);
            nativeBackend.copyNativeDataToBuffer(uv0Buffer, uv0Data, uv0Size);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, uv0BufferObject);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, uv0Buffer, GL46C.GL_DYNAMIC_DRAW);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, uv1BufferObject);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, uv1Buffer, GL46C.GL_STATIC_DRAW);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, colorBufferObject);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, colorBuffer, GL46C.GL_STATIC_DRAW);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, uv2BufferObject);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, vertexCount * 8, GL46C.GL_DYNAMIC_DRAW);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, 0);

            OpenGlModelInstance result = new OpenGlModelInstance();
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
            result.hasUvMorph = nativeBackend.getUvMorphCount(model) > 0;

            modelViewMatBuff = MemoryUtil.memAllocFloat(16);
            projMatBuff = MemoryUtil.memAllocFloat(16);
            light0Buff = MemoryUtil.memAllocFloat(3);
            light1Buff = MemoryUtil.memAllocFloat(3);
            result.modelViewMatBuff = modelViewMatBuff;
            result.projMatBuff = projMatBuff;
            result.light0Buff = light0Buff;
            result.light1Buff = light1Buff;

            int matMorphCount = nativeBackend.getMaterialMorphResultCount(model);
            if (matMorphCount > 0) {
                int floatCount = matMorphCount * 56;
                matMorphResultsByteBuf = MemoryUtil.memAlloc(floatCount * 4);
                matMorphResultsByteBuf.order(ByteOrder.LITTLE_ENDIAN);
                result.applyMaterialMorphState(matMorphCount, matMorphResultsByteBuf);
            }

            result.subMeshCount = nativeBackend.getSubMeshCount(model);
            result.subMeshDataBuf = MemoryUtil.memAlloc(result.subMeshCount * 20);
            result.subMeshDataBuf.order(ByteOrder.LITTLE_ENDIAN);

            nativeBackend.setAutoBlinkEnabled(model, true);
            return result;
        } catch (Exception e) {
            logger.error("CPU 蒙皮模型创建失败，开始清理资源: {}", e.getMessage());

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

    private static void ensureShaderInitialized() {
        if (OpenGlModelInstance.isShaderInited) {
            return;
        }
        if (!ClientRenderRuntime.get().renderBackendRegistry().isShaderEnabled()) {
            return;
        }
        OpenGlModelInstance.initShader();
    }
}
