package com.shiroha.mmdskin.render.backend.gpu;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.bridge.runtime.NativeRenderBackendPort;
import com.shiroha.mmdskin.bridge.runtime.NativeRuntimeBridgeHolder;
import com.shiroha.mmdskin.render.backend.BaseModelInstance;
import com.shiroha.mmdskin.render.material.ModelMaterial;
import com.shiroha.mmdskin.render.shader.ShaderConstants;
import com.shiroha.mmdskin.render.shader.SkinningComputeShader;
import com.shiroha.mmdskin.render.shader.ToonConfig;
import com.shiroha.mmdskin.render.shader.ToonShaderCpu;
import com.shiroha.mmdskin.texture.runtime.TextureRepository;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL46C;
import org.lwjgl.system.MemoryUtil;

/** 文件职责：承载 GPU skinning 模型实例状态。 */
public class GpuSkinningModelInstance extends BaseModelInstance {
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

    int boneMatrixSSBO;

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

    int vertexMorphCount;
    boolean morphDataUploaded;
    FloatBuffer morphWeightsBuffer;
    ByteBuffer morphWeightsByteBuffer;
    int morphOffsetsSSBO;
    int morphWeightsSSBO;

    int uvMorphCount;
    boolean uvMorphDataUploaded;
    FloatBuffer uvMorphWeightsBuffer;
    ByteBuffer uvMorphWeightsByteBuffer;
    int uvMorphOffsetsSSBO;
    int uvMorphWeightsSSBO;
    int skinnedUvBuffer;

    int indexElementSize;
    int indexType;
    ModelMaterial[] mats;
    ModelMaterial lightMapMaterial;

    final Vector3f light0Direction = new Vector3f();
    final Vector3f light1Direction = new Vector3f();

    int shaderProgram;
    int cachedShaderProgram = -1;
    int positionLocation;
    int normalLocation;
    int uv0Location;
    int uv1Location;
    int uv2Location;
    int colorLocation;

    int I_positionLocation;
    int I_normalLocation;
    int I_uv0Location;
    int I_uv2Location;
    int I_colorLocation;

    int subMeshCount;
    ByteBuffer subMeshDataBuf;
    PoseStack currentDeliverStack;

    boolean initialized;
    long lastGpuUploadRevision = -1L;
    int lastBlockBrightness = Integer.MIN_VALUE;
    int lastSkyBrightness = Integer.MIN_VALUE;

    private GpuSkinningModelInstance() {
    }

    public static GpuSkinningModelInstance create(String modelFilename, String modelDir, boolean isPMD, long layerCount) {
        var nativeBackend = NativeRuntimeBridgeHolder.get();
        if (!ensureComputeShaderInitialized()) {
            return null;
        }

        long model = isPMD
                ? nativeBackend.loadPmdModel(modelFilename, modelDir, layerCount)
                : nativeBackend.loadPmxModel(modelFilename, modelDir, layerCount);
        if (model == 0) {
            logger.warn("Cannot open model: '{}'", modelFilename);
            return null;
        }

        GpuSkinningModelInstance result = createFromHandle(model, modelDir);
        if (result == null) {
            nativeBackend.deleteModel(model);
        }
        return result;
    }

    public static GpuSkinningModelInstance createFromHandle(long model, String modelDir) {
        var nativeBackend = NativeRuntimeBridgeHolder.get();
        if (!ensureComputeShaderInitialized()) {
            return null;
        }

        int vao = 0;
        int indexVbo = 0;
        int posVbo = 0;
        int norVbo = 0;
        int uv0Vbo = 0;
        int boneIdxVbo = 0;
        int boneWgtVbo = 0;
        int colorVbo = 0;
        int uv1Vbo = 0;
        int uv2Vbo = 0;
        int[] outputBuffers = null;
        int boneMatrixSsbo = 0;
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
        ModelMaterial lightMapMaterial = null;

        try {
            nativeBackend.initGpuSkinningData(model);
            BufferUploader.reset();

            int vertexCount = (int) nativeBackend.getVertexCount(model);
            int boneCount = nativeBackend.getBoneCount(model);
            if (boneCount > ShaderConstants.MAX_BONES) {
                logger.warn("Bone count ({}) exceeds max supported ({}); rendering may degrade",
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

            int indexElementSize = nativeBackend.getIndexElementSize(model);
            int indexCount = (int) nativeBackend.getIndexCount(model);
            int indexSize = indexCount * indexElementSize;
            long indexData = nativeBackend.getIndexDataAddress(model);
            ByteBuffer indexBuffer = ByteBuffer.allocateDirect(indexSize);
            nativeBackend.copyNativeDataToBuffer(indexBuffer, indexData, indexSize);
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
            if (nativeBackend.copyOriginalPositionsToBuffer(model, posBuffer, vertexCount) == 0) {
                logger.warn("Failed to copy original vertex positions");
            }
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, posVbo);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, posBuffer, GL46C.GL_STATIC_DRAW);

            ByteBuffer norBuffer = ByteBuffer.allocateDirect(vertexCount * 12);
            norBuffer.order(ByteOrder.LITTLE_ENDIAN);
            if (nativeBackend.copyOriginalNormalsToBuffer(model, norBuffer, vertexCount) == 0) {
                logger.warn("Failed to copy original normals");
            }
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, norVbo);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, norBuffer, GL46C.GL_STATIC_DRAW);

            ByteBuffer uv0Buffer = ByteBuffer.allocateDirect(vertexCount * 8);
            uv0Buffer.order(ByteOrder.LITTLE_ENDIAN);
            long uvData = nativeBackend.getUvDataAddress(model);
            nativeBackend.copyNativeDataToBuffer(uv0Buffer, uvData, vertexCount * 8);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, uv0Vbo);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, uv0Buffer, GL46C.GL_STATIC_DRAW);

            ByteBuffer boneIndicesByteBuffer = ByteBuffer.allocateDirect(vertexCount * 16);
            boneIndicesByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            if (nativeBackend.copyBoneIndicesToBuffer(model, boneIndicesByteBuffer, vertexCount) == 0) {
                logger.warn("Failed to copy bone indices");
            }
            IntBuffer boneIndicesBuffer = boneIndicesByteBuffer.asIntBuffer();
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, boneIdxVbo);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, boneIndicesBuffer, GL46C.GL_STATIC_DRAW);

            ByteBuffer boneWeightsByteBuffer = ByteBuffer.allocateDirect(vertexCount * 16);
            boneWeightsByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            if (nativeBackend.copyBoneWeightsToBuffer(model, boneWeightsByteBuffer, vertexCount) == 0) {
                logger.warn("Failed to copy bone weights");
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

            List<String> textureKeys = new ArrayList<>();
            ModelMaterial[] mats = new ModelMaterial[nativeBackend.getMaterialCount(model)];
            for (int i = 0; i < mats.length; ++i) {
                mats[i] = new ModelMaterial();
                mats[i].name = nativeBackend.getMaterialName(model, i);
                String texFilename = nativeBackend.getMaterialTexturePath(model, i);
                mats[i].texturePath = texFilename != null ? texFilename : "";
                if (texFilename != null && !texFilename.isEmpty()) {
                    TextureRepository.Texture managerTexture = TextureRepository.GetTexture(texFilename);
                    if (managerTexture != null) {
                        mats[i].tex = managerTexture.tex;
                        mats[i].hasAlpha = managerTexture.hasAlpha;
                        TextureRepository.addRef(texFilename);
                        textureKeys.add(texFilename);
                    }
                }
                mats[i].updateOutlinePolicy();
            }

            lightMapMaterial = new ModelMaterial();
            String lightMapPath = modelDir + "/lightMap.png";
            TextureRepository.Texture lightMapTexture = TextureRepository.GetTexture(lightMapPath);
            if (lightMapTexture != null) {
                lightMapMaterial.tex = lightMapTexture.tex;
                lightMapMaterial.hasAlpha = lightMapTexture.hasAlpha;
                TextureRepository.addRef(lightMapPath);
                textureKeys.add(lightMapPath);
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
            boneMatrixSsbo = SkinningComputeShader.createBoneMatrixBuffer();

            modelViewMatBuff = MemoryUtil.memAllocFloat(16);
            projMatBuff = MemoryUtil.memAllocFloat(16);

            nativeBackend.initGpuMorphData(model);
            int morphCount = nativeBackend.getVertexMorphCount(model);
            if (morphCount > 0) {
                morphWeightsBuffer = MemoryUtil.memAllocFloat(morphCount);
                morphBuffers = SkinningComputeShader.createMorphBuffers(morphCount);
            }

            nativeBackend.initGpuUvMorphData(model);
            int uvMorphCount = nativeBackend.getUvMorphCount(model);
            if (uvMorphCount > 0) {
                uvMorphWeightsBuf = MemoryUtil.memAllocFloat(uvMorphCount);
                uvMorphBuffers = SkinningComputeShader.createUvMorphBuffers(uvMorphCount);
                skinnedUvBuf = SkinningComputeShader.createSkinnedUvBuffer(vertexCount);
            } else {
                skinnedUvBuf = SkinningComputeShader.createSkinnedUvBuffer(vertexCount);
            }

            int materialMorphCount = nativeBackend.getMaterialMorphResultCount(model);
            if (materialMorphCount > 0) {
                int floatCount = materialMorphCount * 56;
                matMorphResultsByteBuf = MemoryUtil.memAlloc(floatCount * 4);
                matMorphResultsByteBuf.order(ByteOrder.LITTLE_ENDIAN);
            }

            GpuSkinningModelInstance result = new GpuSkinningModelInstance();
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
            result.boneMatrixSSBO = boneMatrixSsbo;
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
            result.textureKeys = textureKeys;
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

            result.uvMorphCount = uvMorphCount;
            result.skinnedUvBuffer = skinnedUvBuf;
            if (uvMorphCount > 0) {
                result.uvMorphWeightsBuffer = uvMorphWeightsBuf;
                result.uvMorphWeightsByteBuffer = ByteBuffer.allocateDirect(uvMorphCount * 4);
                result.uvMorphWeightsByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                result.uvMorphOffsetsSSBO = uvMorphBuffers[0];
                result.uvMorphWeightsSSBO = uvMorphBuffers[1];
            }

            result.materialMorphResultCount = materialMorphCount;
            result.materialMorphResultsByteBuffer = matMorphResultsByteBuf;
            result.subMeshCount = nativeBackend.getSubMeshCount(model);
            subMeshDataBufLocal = MemoryUtil.memAlloc(result.subMeshCount * 20);
            subMeshDataBufLocal.order(ByteOrder.LITTLE_ENDIAN);
            result.subMeshDataBuf = subMeshDataBufLocal;
            result.initialized = true;

            nativeBackend.setAutoBlinkEnabled(model, true);
            GL46C.glBindVertexArray(0);
            return result;
        } catch (Exception e) {
            logger.error("GPU skinning model creation failed, cleaning resources: {}", e.getMessage());

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
            if (boneMatrixSsbo > 0) GL46C.glDeleteBuffers(boneMatrixSsbo);
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

    private static boolean ensureComputeShaderInitialized() {
        if (computeShader != null) {
            return true;
        }

        computeShader = new SkinningComputeShader();
        if (!computeShader.init()) {
            logger.error("Skinning compute shader init failed, falling back to CPU skinning");
            computeShader = null;
            return false;
        }
        return true;
    }

    @Override
    protected boolean isReady() {
        return initialized;
    }

    @Override
    protected void onUpdate(float deltaTime) {
        backendPort().updateAnimationOnly(model, deltaTime);
    }

    @Override
    protected void doRenderModel(Entity entityIn,
                                 float entityYaw,
                                 float entityPitch,
                                 Vector3f entityTrans,
                                 PoseStack deliverStack,
                                 int packedLight) {
        GpuSkinningModelRenderer.render(this, entityIn, entityYaw, entityPitch, entityTrans, deliverStack);
    }

    void updateLocation(int program) {
        if (program == cachedShaderProgram) {
            return;
        }
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

    NativeRenderBackendPort nativeBackendPort() {
        return BaseModelInstance.backendPort();
    }

    long nativeModelHandle() {
        return model;
    }

    long nativeUpdateRevisionValue() {
        return getNativeUpdateRevision();
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
        return GpuSkinningModelLifecycle.getVramUsage(this);
    }

    @Override
    public long getRamUsage() {
        return GpuSkinningModelLifecycle.getRamUsage(this);
    }

    @Override
    public void dispose() {
        GpuSkinningModelLifecycle.dispose(this);
    }
}
