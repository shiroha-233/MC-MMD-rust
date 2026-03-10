package com.shiroha.mmdskin.renderer.runtime.model.gpu;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.renderer.runtime.texture.MMDTextureManager;
import com.shiroha.mmdskin.renderer.pipeline.shader.ShaderConstants;
import com.shiroha.mmdskin.renderer.pipeline.shader.SkinningComputeShader;
import com.shiroha.mmdskin.renderer.pipeline.shader.ToonShaderCpu;
import com.shiroha.mmdskin.renderer.pipeline.shader.ToonConfig;
import com.shiroha.mmdskin.renderer.runtime.model.AbstractMMDModel;
import com.shiroha.mmdskin.renderer.runtime.model.shared.MMDMaterial;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL46C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * GPU 蒙皮 MMD 模型渲染器。
 */
public class MMDModelGpuSkinning extends AbstractMMDModel {
    static SkinningComputeShader computeShader;
    static ToonShaderCpu toonShaderCpu;
    static final ToonConfig toonConfig = ToonConfig.getInstance();

    int vertexCount;

    int vertexArrayObject;
    int indexBufferObject;

    int positionBufferObject;
    int normalBufferObject;
    int uv0BufferObject;
    int boneIndicesBufferObject;
    int boneWeightsBufferObject;

    int colorBufferObject;
    int uv1BufferObject;
    int uv2BufferObject;

    int skinnedPositionsBuffer;
    int skinnedNormalsBuffer;

    int boneMatrixSSBO = 0;

    @SuppressWarnings("unused")
    ByteBuffer posBuffer;
    @SuppressWarnings("unused")
    ByteBuffer norBuffer;
    @SuppressWarnings("unused")
    ByteBuffer uv0Buffer;
    @SuppressWarnings("unused")
    ByteBuffer colorBuffer;
    @SuppressWarnings("unused")
    ByteBuffer uv1Buffer;
    ByteBuffer uv2Buffer;
    FloatBuffer boneMatricesBuffer;
    FloatBuffer modelViewMatBuff;
    FloatBuffer projMatBuff;

    ByteBuffer boneMatricesByteBuffer;

    int vertexMorphCount = 0;
    boolean morphDataUploaded = false;
    FloatBuffer morphWeightsBuffer;
    ByteBuffer morphWeightsByteBuffer;
    int morphOffsetsSSBO = 0;
    int morphWeightsSSBO = 0;

    int uvMorphCount = 0;
    boolean uvMorphDataUploaded = false;
    FloatBuffer uvMorphWeightsBuffer;
    ByteBuffer uvMorphWeightsByteBuffer;
    int uvMorphOffsetsSSBO = 0;
    int uvMorphWeightsSSBO = 0;
    int skinnedUvBuffer = 0;

    int indexElementSize;
    int indexType;
    MMDMaterial[] mats;
    MMDMaterial lightMapMaterial;

    final Vector3f light0Direction = new Vector3f();
    final Vector3f light1Direction = new Vector3f();

    int shaderProgram;
    int cachedShaderProgram = -1;
    int positionLocation, normalLocation;
    int uv0Location, uv1Location, uv2Location;
    int colorLocation;

    int I_positionLocation, I_normalLocation;
    int I_uv0Location, I_uv2Location, I_colorLocation;

    int subMeshCount;

    ByteBuffer subMeshDataBuf;

    PoseStack currentDeliverStack;

    boolean initialized = false;

    private MMDModelGpuSkinning() {}

    public static MMDModelGpuSkinning Create(String modelFilename, String modelDir, boolean isPMD, long layerCount) {
        NativeFunc nf = getNf();

        if (computeShader == null) {
            computeShader = new SkinningComputeShader();
            if (!computeShader.init()) {
                logger.error("蒙皮 Compute Shader 初始化失败，回退到 CPU 蒙皮");
                computeShader = null;
                return null;
            }
        }

        long model;
        if (isPMD) {
            model = nf.LoadModelPMD(modelFilename, modelDir, layerCount);
        } else {
            model = nf.LoadModelPMX(modelFilename, modelDir, layerCount);
        }

        if (model == 0) {
            logger.warn("无法打开模型: '{}'", modelFilename);
            return null;
        }

        MMDModelGpuSkinning result = createFromHandle(model, modelDir);
        if (result == null) {

            nf.DeleteModel(model);
        }
        return result;
    }

    public static MMDModelGpuSkinning createFromHandle(long model, String modelDir) {
        NativeFunc nf = getNf();

        if (computeShader == null) {
            computeShader = new SkinningComputeShader();
            if (!computeShader.init()) {
                logger.error("蒙皮 Compute Shader 初始化失败，回退到 CPU 蒙皮");
                computeShader = null;

                return null;
            }
        }

        int vao = 0, indexVbo = 0, posVbo = 0, norVbo = 0, uv0Vbo = 0;
        int boneIdxVbo = 0, boneWgtVbo = 0, colorVbo = 0, uv1Vbo = 0, uv2Vbo = 0;
        int[] outputBuffers = null;
        int boneMatrixSSBO = 0;
        int[] morphBuffers = null;
        FloatBuffer boneMatricesBuffer = null;
        ByteBuffer boneMatricesByteBuffer = null;
        FloatBuffer modelViewMatBuff = null;
        FloatBuffer projMatBuff = null;
        FloatBuffer morphWeightsBuffer = null;
        int[] uvMorphBuffers = null;
        FloatBuffer uvMorphWeightsBuf = null;
        int skinnedUvBuf = 0;
        ByteBuffer matMorphResultsByteBuf = null;
        ByteBuffer subMeshDataBufLocal = null;
        MMDMaterial lightMapMaterial = null;

        try {

            nf.InitGpuSkinningData(model);

            BufferUploader.reset();

            int vertexCount = (int) nf.GetVertexCount(model);
            int boneCount = nf.GetBoneCount(model);

            if (boneCount > ShaderConstants.MAX_BONES) {
                logger.warn("模型骨骼数量 ({}) 超过最大支持 ({})，部分骨骼可能无法正确渲染",
                    boneCount, ShaderConstants.MAX_BONES);
            }

            vao = GL46C.glGenVertexArrays();
            indexVbo = GL46C.glGenBuffers();
            posVbo = GL46C.glGenBuffers();
            norVbo = GL46C.glGenBuffers();
            uv0Vbo = GL46C.glGenBuffers();
            boneIdxVbo = GL46C.glGenBuffers();
            boneWgtVbo = GL46C.glGenBuffers();
            colorVbo = GL46C.glGenBuffers();
            uv1Vbo = GL46C.glGenBuffers();
            uv2Vbo = GL46C.glGenBuffers();

            GL46C.glBindVertexArray(vao);

            int indexElementSize = (int) nf.GetIndexElementSize(model);
            int indexCount = (int) nf.GetIndexCount(model);
            int indexSize = indexCount * indexElementSize;
            long indexData = nf.GetIndices(model);
            ByteBuffer indexBuffer = ByteBuffer.allocateDirect(indexSize);
            nf.CopyDataToByteBuffer(indexBuffer, indexData, indexSize);
            indexBuffer.position(0);
            GL46C.glBindBuffer(GL46C.GL_ELEMENT_ARRAY_BUFFER, indexVbo);
            GL46C.glBufferData(GL46C.GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL46C.GL_STATIC_DRAW);

            int indexType = switch (indexElementSize) {
                case 1 -> GL46C.GL_UNSIGNED_BYTE;
                case 2 -> GL46C.GL_UNSIGNED_SHORT;
                case 4 -> GL46C.GL_UNSIGNED_INT;
                default -> 0;
            };

            ByteBuffer posBuffer = ByteBuffer.allocateDirect(vertexCount * 12);
            posBuffer.order(ByteOrder.LITTLE_ENDIAN);
            int copiedPos = nf.CopyOriginalPositionsToBuffer(model, posBuffer, vertexCount);
            if (copiedPos == 0) {
                logger.warn("原始顶点位置数据复制失败");
            }
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, posVbo);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, posBuffer, GL46C.GL_STATIC_DRAW);

            ByteBuffer norBuffer = ByteBuffer.allocateDirect(vertexCount * 12);
            norBuffer.order(ByteOrder.LITTLE_ENDIAN);
            int copiedNor = nf.CopyOriginalNormalsToBuffer(model, norBuffer, vertexCount);
            if (copiedNor == 0) {
                logger.warn("原始法线数据复制失败");
            }
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, norVbo);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, norBuffer, GL46C.GL_STATIC_DRAW);

            ByteBuffer uv0Buffer = ByteBuffer.allocateDirect(vertexCount * 8);
            uv0Buffer.order(ByteOrder.LITTLE_ENDIAN);
            long uvData = nf.GetUVs(model);
            nf.CopyDataToByteBuffer(uv0Buffer, uvData, vertexCount * 8);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, uv0Vbo);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, uv0Buffer, GL46C.GL_STATIC_DRAW);

            ByteBuffer boneIndicesByteBuffer = ByteBuffer.allocateDirect(vertexCount * 16);
            boneIndicesByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            int copiedIdx = nf.CopyBoneIndicesToBuffer(model, boneIndicesByteBuffer, vertexCount);
            if (copiedIdx == 0) {
                logger.warn("骨骼索引数据复制失败");
            }
            IntBuffer boneIndicesBuffer = boneIndicesByteBuffer.asIntBuffer();
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, boneIdxVbo);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, boneIndicesBuffer, GL46C.GL_STATIC_DRAW);

            ByteBuffer boneWeightsByteBuffer = ByteBuffer.allocateDirect(vertexCount * 16);
            boneWeightsByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            int copiedWgt = nf.CopyBoneWeightsToBuffer(model, boneWeightsByteBuffer, vertexCount);
            if (copiedWgt == 0) {
                logger.warn("骨骼权重数据复制失败");
            }
            FloatBuffer boneWeightsFloatBuffer = boneWeightsByteBuffer.asFloatBuffer();
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, boneWgtVbo);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, boneWeightsFloatBuffer, GL46C.GL_STATIC_DRAW);

            ByteBuffer colorBuffer = ByteBuffer.allocateDirect(vertexCount * 16);
            colorBuffer.order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < vertexCount; i++) {
                colorBuffer.putFloat(1.0f);
                colorBuffer.putFloat(1.0f);
                colorBuffer.putFloat(1.0f);
                colorBuffer.putFloat(1.0f);
            }
            colorBuffer.flip();

            ByteBuffer uv1Buffer = ByteBuffer.allocateDirect(vertexCount * 8);
            uv1Buffer.order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < vertexCount; i++) {
                uv1Buffer.putInt(15);
                uv1Buffer.putInt(15);
            }
            uv1Buffer.flip();
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, uv1Vbo);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, uv1Buffer, GL46C.GL_STATIC_DRAW);

            ByteBuffer uv2Buffer = ByteBuffer.allocateDirect(vertexCount * 8);
            uv2Buffer.order(ByteOrder.LITTLE_ENDIAN);

            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, colorVbo);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, colorBuffer, GL46C.GL_STATIC_DRAW);

            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, uv2Vbo);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, vertexCount * 8, GL46C.GL_DYNAMIC_DRAW);

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

            boneMatricesBuffer = MemoryUtil.memAllocFloat(boneCount * 16);
            boneMatricesByteBuffer = MemoryUtil.memAlloc(boneCount * 64);
            boneMatricesByteBuffer.order(ByteOrder.LITTLE_ENDIAN);

            outputBuffers = SkinningComputeShader.createOutputBuffers(vertexCount);

            boneMatrixSSBO = SkinningComputeShader.createBoneMatrixBuffer();

            modelViewMatBuff = MemoryUtil.memAllocFloat(16);
            projMatBuff = MemoryUtil.memAllocFloat(16);

            nf.InitGpuMorphData(model);
            int morphCount = (int) nf.GetVertexMorphCount(model);
            if (morphCount > 0) {
                morphWeightsBuffer = MemoryUtil.memAllocFloat(morphCount);
                morphBuffers = SkinningComputeShader.createMorphBuffers(morphCount);
            }

            nf.InitGpuUvMorphData(model);
            int uvMorphCnt = nf.GetUvMorphCount(model);
            if (uvMorphCnt > 0) {
                uvMorphWeightsBuf = MemoryUtil.memAllocFloat(uvMorphCnt);
                uvMorphBuffers = SkinningComputeShader.createUvMorphBuffers(uvMorphCnt);
                skinnedUvBuf = SkinningComputeShader.createSkinnedUvBuffer(vertexCount);
            } else {

                skinnedUvBuf = SkinningComputeShader.createSkinnedUvBuffer(vertexCount);
            }

            int matMorphCount = nf.GetMaterialMorphResultCount(model);
            if (matMorphCount > 0) {
                int floatCount = matMorphCount * 56;
                matMorphResultsByteBuf = MemoryUtil.memAlloc(floatCount * 4);
                matMorphResultsByteBuf.order(ByteOrder.LITTLE_ENDIAN);
            }

            MMDModelGpuSkinning result = new MMDModelGpuSkinning();
            result.model = model;
            result.modelDir = modelDir;
            result.vertexCount = vertexCount;
            result.vertexArrayObject = vao;
            result.indexBufferObject = indexVbo;
            result.positionBufferObject = posVbo;
            result.normalBufferObject = norVbo;
            result.uv0BufferObject = uv0Vbo;
            result.boneIndicesBufferObject = boneIdxVbo;
            result.boneWeightsBufferObject = boneWgtVbo;
            result.colorBufferObject = colorVbo;
            result.uv1BufferObject = uv1Vbo;
            result.uv2BufferObject = uv2Vbo;
            result.skinnedPositionsBuffer = outputBuffers[0];
            result.skinnedNormalsBuffer = outputBuffers[1];
            result.boneMatrixSSBO = boneMatrixSSBO;
            result.posBuffer = posBuffer;
            result.norBuffer = norBuffer;
            result.uv0Buffer = uv0Buffer;
            result.colorBuffer = colorBuffer;
            result.uv1Buffer = uv1Buffer;
            result.uv2Buffer = uv2Buffer;
            result.boneMatricesBuffer = boneMatricesBuffer;
            result.boneMatricesByteBuffer = boneMatricesByteBuffer;
            result.indexElementSize = indexElementSize;
            result.indexType = indexType;
            result.mats = mats;
            result.lightMapMaterial = lightMapMaterial;
            result.textureKeys = texKeys;
            result.modelViewMatBuff = modelViewMatBuff;
            result.projMatBuff = projMatBuff;
            result.vertexMorphCount = morphCount;
            if (morphCount > 0) {
                result.morphWeightsBuffer = morphWeightsBuffer;
                result.morphWeightsByteBuffer = ByteBuffer.allocateDirect(morphCount * 4);
                result.morphWeightsByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                result.morphOffsetsSSBO = morphBuffers[0];
                result.morphWeightsSSBO = morphBuffers[1];
            }

            result.uvMorphCount = uvMorphCnt;
            result.skinnedUvBuffer = skinnedUvBuf;
            if (uvMorphCnt > 0) {
                result.uvMorphWeightsBuffer = uvMorphWeightsBuf;
                result.uvMorphWeightsByteBuffer = ByteBuffer.allocateDirect(uvMorphCnt * 4);
                result.uvMorphWeightsByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                result.uvMorphOffsetsSSBO = uvMorphBuffers[0];
                result.uvMorphWeightsSSBO = uvMorphBuffers[1];
            }

            result.materialMorphResultCount = matMorphCount;
            result.materialMorphResultsByteBuffer = matMorphResultsByteBuf;
            result.subMeshCount = (int) nf.GetSubMeshCount(model);
            subMeshDataBufLocal = MemoryUtil.memAlloc(result.subMeshCount * 20);
            subMeshDataBufLocal.order(ByteOrder.LITTLE_ENDIAN);
            result.subMeshDataBuf = subMeshDataBufLocal;
            result.initialized = true;

            nf.SetAutoBlinkEnabled(model, true);

            GL46C.glBindVertexArray(0);
            return result;

        } catch (Exception e) {

            logger.error("GPU 蒙皮模型创建失败，清理资源: {}", e.getMessage());

            if (vao > 0) GL46C.glDeleteVertexArrays(vao);
            if (indexVbo > 0) GL46C.glDeleteBuffers(indexVbo);
            if (posVbo > 0) GL46C.glDeleteBuffers(posVbo);
            if (norVbo > 0) GL46C.glDeleteBuffers(norVbo);
            if (uv0Vbo > 0) GL46C.glDeleteBuffers(uv0Vbo);
            if (boneIdxVbo > 0) GL46C.glDeleteBuffers(boneIdxVbo);
            if (boneWgtVbo > 0) GL46C.glDeleteBuffers(boneWgtVbo);
            if (colorVbo > 0) GL46C.glDeleteBuffers(colorVbo);
            if (uv1Vbo > 0) GL46C.glDeleteBuffers(uv1Vbo);
            if (uv2Vbo > 0) GL46C.glDeleteBuffers(uv2Vbo);
            if (outputBuffers != null) {
                GL46C.glDeleteBuffers(outputBuffers[0]);
                GL46C.glDeleteBuffers(outputBuffers[1]);
            }
            if (boneMatrixSSBO > 0) GL46C.glDeleteBuffers(boneMatrixSSBO);
            if (morphBuffers != null) {
                GL46C.glDeleteBuffers(morphBuffers[0]);
                GL46C.glDeleteBuffers(morphBuffers[1]);
            }
            if (uvMorphBuffers != null) {
                GL46C.glDeleteBuffers(uvMorphBuffers[0]);
                GL46C.glDeleteBuffers(uvMorphBuffers[1]);
            }
            if (skinnedUvBuf > 0) GL46C.glDeleteBuffers(skinnedUvBuf);
            if (lightMapMaterial != null && lightMapMaterial.ownsTexture && lightMapMaterial.tex > 0) {
                GL46C.glDeleteTextures(lightMapMaterial.tex);
            }

            if (boneMatricesBuffer != null) MemoryUtil.memFree(boneMatricesBuffer);
            if (boneMatricesByteBuffer != null) MemoryUtil.memFree(boneMatricesByteBuffer);
            if (modelViewMatBuff != null) MemoryUtil.memFree(modelViewMatBuff);
            if (projMatBuff != null) MemoryUtil.memFree(projMatBuff);
            if (morphWeightsBuffer != null) MemoryUtil.memFree(morphWeightsBuffer);
            if (uvMorphWeightsBuf != null) MemoryUtil.memFree(uvMorphWeightsBuf);
            if (matMorphResultsByteBuf != null) MemoryUtil.memFree(matMorphResultsByteBuf);
            if (subMeshDataBufLocal != null) MemoryUtil.memFree(subMeshDataBufLocal);

            return null;
        }
    }

    @Override
    protected boolean isReady() {
        return initialized;
    }

    @Override
    protected void onUpdate(float deltaTime) {
        getNf().UpdateAnimationOnly(model, deltaTime);
    }

    @Override
    protected void doRenderModel(Entity entityIn, float entityYaw, float entityPitch, Vector3f entityTrans, PoseStack deliverStack, int packedLight) {
        MMDModelGpuSkinningRenderer.render(this, entityIn, entityYaw, entityPitch, entityTrans, deliverStack);
    }

    void updateLocation(int program) {

        if (program == cachedShaderProgram) return;
        cachedShaderProgram = program;

        positionLocation = GlStateManager._glGetAttribLocation(program, "Position");
        normalLocation = GlStateManager._glGetAttribLocation(program, "Normal");
        uv0Location = GlStateManager._glGetAttribLocation(program, "UV0");
        uv1Location = GlStateManager._glGetAttribLocation(program, "UV1");
        uv2Location = GlStateManager._glGetAttribLocation(program, "UV2");
        colorLocation = GlStateManager._glGetAttribLocation(program, "Color");

        I_positionLocation = GlStateManager._glGetAttribLocation(program, "iris_Position");
        I_normalLocation = GlStateManager._glGetAttribLocation(program, "iris_Normal");
        I_uv0Location = GlStateManager._glGetAttribLocation(program, "iris_UV0");
        I_uv2Location = GlStateManager._glGetAttribLocation(program, "iris_UV2");
        I_colorLocation = GlStateManager._glGetAttribLocation(program, "iris_Color");
    }

    void setUniforms(ShaderInstance shader, PoseStack deliverStack) {
        setupShaderUniforms(shader, deliverStack, light0Direction, light1Direction, lightMapMaterial.tex);
    }

    NativeFunc nativeFunc() {
        return getNf();
    }

    long nativeModelHandle() {
        return model;
    }

    Quaternionf workingQuaternion() {
        return tempQuat;
    }

    float modelScaleValue() {
        return getModelScale();
    }

    int materialMorphResultCountValue() {
        return materialMorphResultCount;
    }

    void loadMaterialMorphResults() {
        fetchMaterialMorphResults();
    }

    float effectiveMaterialAlpha(int materialIndex, float baseAlpha) {
        return getEffectiveMaterialAlpha(materialIndex, baseAlpha);
    }

    void releaseBaseResources() {
        releaseTextures();
        disposeModelHandle();
        disposeMaterialMorphBuffers();
    }

    @Override
    public long getVramUsage() {
        return MMDModelGpuSkinningLifecycle.getVramUsage(this);
    }

    @Override
    public long getRamUsage() {
        return MMDModelGpuSkinningLifecycle.getRamUsage(this);
    }

    @Override
    public void dispose() {
        MMDModelGpuSkinningLifecycle.dispose(this);
    }

    @Deprecated
    public static void Delete(MMDModelGpuSkinning model) {
        if (model != null) model.dispose();
    }

}
