package com.shiroha.mmdskin.renderer.model;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.renderer.camera.MMDCameraController;
import com.shiroha.mmdskin.renderer.core.EyeTrackingHelper;
import com.shiroha.mmdskin.renderer.core.IMMDModel;
import com.shiroha.mmdskin.renderer.core.IrisCompat;
import com.shiroha.mmdskin.renderer.core.RenderContext;
import com.shiroha.mmdskin.renderer.resource.MMDTextureManager;
import com.shiroha.mmdskin.renderer.shader.SkinningComputeShader;
import com.shiroha.mmdskin.renderer.shader.ToonShaderCpu;
import com.shiroha.mmdskin.renderer.shader.ToonConfig;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LightLayer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL46C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * GPU 蒙皮 MMD 模型渲染器
 * 
 * 使用 Compute Shader 在 GPU 上预计算蒙皮，然后通过 Minecraft 标准 ShaderInstance 管线渲染。
 * 这样 Iris 可以正确拦截渲染着色器，解决光影下模型透明的问题。
 * 
 * 流程：
 * 1. Compute Shader 读取原始顶点 + 骨骼矩阵 → 输出蒙皮后的顶点/法线
 * 2. 使用 Minecraft 标准管线（RenderSystem.getShader()）进行渲染
 * 3. Iris 拦截 ShaderInstance 替换为 G-buffer 着色器 → 光影正常工作
 */
public class MMDModelGpuSkinning implements IMMDModel {
    private static final Logger logger = LogManager.getLogger();
    private static NativeFunc nf;
    private static SkinningComputeShader computeShader;
    private static ToonShaderCpu toonShaderCpu;
    private static final ToonConfig toonConfig = ToonConfig.getInstance();
    
    // 模型数据
    private long model;
    private String modelDir;
    private String cachedModelName;
    private int vertexCount;
    
    // OpenGL 资源 - VAO
    private int vertexArrayObject;
    private int indexBufferObject;
    
    // 原始数据 VBO（静态，作为 Compute Shader 的 SSBO 输入）
    private int positionBufferObject;
    private int normalBufferObject;
    private int uv0BufferObject;
    private int boneIndicesBufferObject;
    private int boneWeightsBufferObject;
    
    // Minecraft 标准顶点属性 VBO
    private int colorBufferObject;
    private int uv1BufferObject;
    private int uv2BufferObject;
    
    // Compute Shader 输出缓冲区（每实例独立，同时作为 SSBO 和 VBO）
    private int skinnedPositionsBuffer;
    private int skinnedNormalsBuffer;
    
    // 骨骼矩阵 SSBO（每实例独立，避免多模型数据冲突）
    private int boneMatrixSSBO = 0;
    
    // 缓冲区（allocateDirect 分配，由 GC 回收）
    @SuppressWarnings("unused")
    private ByteBuffer posBuffer;
    @SuppressWarnings("unused")
    private ByteBuffer norBuffer;
    @SuppressWarnings("unused")
    private ByteBuffer uv0Buffer;
    @SuppressWarnings("unused")
    private ByteBuffer colorBuffer;
    @SuppressWarnings("unused")
    private ByteBuffer uv1Buffer;
    @SuppressWarnings("unused")
    private ByteBuffer uv2Buffer;
    private FloatBuffer boneMatricesBuffer;
    private FloatBuffer modelViewMatBuff;
    private FloatBuffer projMatBuff;
    
    // 预分配的骨骼矩阵复制缓冲区（避免每帧 allocateDirect）
    private ByteBuffer boneMatricesByteBuffer;
    
    // 顶点 Morph 数据
    private int vertexMorphCount = 0;
    private boolean morphDataUploaded = false;
    private FloatBuffer morphWeightsBuffer;
    private ByteBuffer morphWeightsByteBuffer;
    private int morphOffsetsSSBO = 0;
    private int morphWeightsSSBO = 0;
    
    // UV Morph 数据
    private int uvMorphCount = 0;
    private boolean uvMorphDataUploaded = false;
    private FloatBuffer uvMorphWeightsBuffer;
    private ByteBuffer uvMorphWeightsByteBuffer;
    private int uvMorphOffsetsSSBO = 0;
    private int uvMorphWeightsSSBO = 0;
    private int skinnedUvBuffer = 0;
    
    // 材质 Morph 结果（每材质 28 个 float）
    private FloatBuffer materialMorphResultsBuffer;
    private ByteBuffer materialMorphResultsByteBuffer;
    private int materialMorphResultCount = 0;
    
    private int indexElementSize;
    private int indexType;
    private Material[] mats;
    private Material lightMapMaterial;
    
    // 光照方向（预分配复用）
    private final Vector3f light0Direction = new Vector3f();
    private final Vector3f light1Direction = new Vector3f();
    private final Quaternionf tempQuat = new Quaternionf();
    
    // 着色器属性位置（每帧根据当前着色器更新）
    private int shaderProgram;
    private int positionLocation, normalLocation;
    private int uv0Location, uv1Location, uv2Location;
    private int colorLocation;
    // Iris 重命名的属性
    private int I_positionLocation, I_normalLocation;
    private int I_uv0Location, I_uv2Location, I_colorLocation;
    
    // 临时存储当前 PoseStack，供 renderNormal 使用
    private PoseStack currentDeliverStack;
    
    // 时间追踪
    private long lastUpdateTime = -1;
    private static final float MAX_DELTA_TIME = 0.25f; // 最大 250ms（4FPS），防止暂停后跳跃
    
    private boolean initialized = false;
    
    private MMDModelGpuSkinning() {}
    
    /**
     * 创建 GPU 蒙皮模型（Compute Shader 方案）
     */
    public static MMDModelGpuSkinning Create(String modelFilename, String modelDir, boolean isPMD, long layerCount) {
        if (nf == null) nf = NativeFunc.GetInst();
        
        // 初始化 Compute Shader（懒加载，全局共享）
        if (computeShader == null) {
            computeShader = new SkinningComputeShader();
            if (!computeShader.init()) {
                logger.error("蒙皮 Compute Shader 初始化失败，回退到 CPU 蒙皮");
                return null;
            }
        }
        
        // 加载模型
        long model;
        if (isPMD) {
            model = nf.LoadModelPMD(modelFilename, modelDir, layerCount);
        } else {
            model = nf.LoadModelPMX(modelFilename, modelDir, layerCount);
        }
        
        if (model == 0) {
            logger.info("无法打开模型: '{}'", modelFilename);
            return null;
        }
        
        // 资源追踪变量（用于异常时清理）
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
        FloatBuffer matMorphResultsBuf = null;
        ByteBuffer matMorphResultsByteBuf = null;
        Material lightMapMaterial = null;
        
        try {
            // 初始化 GPU 蒙皮数据
            nf.InitGpuSkinningData(model);
            
            BufferUploader.reset();
            
            int vertexCount = (int) nf.GetVertexCount(model);
            int boneCount = nf.GetBoneCount(model);
            
            if (boneCount > SkinningComputeShader.MAX_BONES) {
                logger.warn("模型骨骼数量 ({}) 超过最大支持 ({})，部分骨骼可能无法正确渲染", 
                    boneCount, SkinningComputeShader.MAX_BONES);
            }
            logger.info("GPU 蒙皮模型加载（Compute Shader）: {} 顶点, {} 骨骼", vertexCount, boneCount);
            
            // 创建 VAO 和 VBO
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
            
            // 索引缓冲区
            int indexElementSize = (int) nf.GetIndexElementSize(model);
            int indexCount = (int) nf.GetIndexCount(model);
            int indexSize = indexCount * indexElementSize;
            long indexData = nf.GetIndices(model);
            ByteBuffer indexBuffer = ByteBuffer.allocateDirect(indexSize);
            for (int i = 0; i < indexSize; ++i) {
                indexBuffer.put(nf.ReadByte(indexData, i));
            }
            indexBuffer.position(0);
            GL46C.glBindBuffer(GL46C.GL_ELEMENT_ARRAY_BUFFER, indexVbo);
            GL46C.glBufferData(GL46C.GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL46C.GL_STATIC_DRAW);
            
            int indexType = switch (indexElementSize) {
                case 1 -> GL46C.GL_UNSIGNED_BYTE;
                case 2 -> GL46C.GL_UNSIGNED_SHORT;
                case 4 -> GL46C.GL_UNSIGNED_INT;
                default -> 0;
            };
            
            // 原始顶点位置（静态，用于 Compute Shader 输入）
            ByteBuffer posBuffer = ByteBuffer.allocateDirect(vertexCount * 12);
            posBuffer.order(ByteOrder.LITTLE_ENDIAN);
            int copiedPos = nf.CopyOriginalPositionsToBuffer(model, posBuffer, vertexCount);
            if (copiedPos == 0) {
                logger.warn("原始顶点位置数据复制失败");
            }
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, posVbo);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, posBuffer, GL46C.GL_STATIC_DRAW);
            
            // 原始法线（静态）
            ByteBuffer norBuffer = ByteBuffer.allocateDirect(vertexCount * 12);
            norBuffer.order(ByteOrder.LITTLE_ENDIAN);
            int copiedNor = nf.CopyOriginalNormalsToBuffer(model, norBuffer, vertexCount);
            if (copiedNor == 0) {
                logger.warn("原始法线数据复制失败");
            }
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, norVbo);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, norBuffer, GL46C.GL_STATIC_DRAW);
            
            // UV（静态）
            ByteBuffer uv0Buffer = ByteBuffer.allocateDirect(vertexCount * 8);
            uv0Buffer.order(ByteOrder.LITTLE_ENDIAN);
            long uvData = nf.GetUVs(model);
            nf.CopyDataToByteBuffer(uv0Buffer, uvData, vertexCount * 8);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, uv0Vbo);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, uv0Buffer, GL46C.GL_STATIC_DRAW);
            
            // 骨骼索引（静态，ivec4）
            ByteBuffer boneIndicesByteBuffer = ByteBuffer.allocateDirect(vertexCount * 16);
            boneIndicesByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            int copiedIdx = nf.CopyBoneIndicesToBuffer(model, boneIndicesByteBuffer, vertexCount);
            if (copiedIdx == 0) {
                logger.warn("骨骼索引数据复制失败");
            }
            IntBuffer boneIndicesBuffer = boneIndicesByteBuffer.asIntBuffer();
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, boneIdxVbo);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, boneIndicesBuffer, GL46C.GL_STATIC_DRAW);
            
            // 骨骼权重（静态，vec4）
            ByteBuffer boneWeightsByteBuffer = ByteBuffer.allocateDirect(vertexCount * 16);
            boneWeightsByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            int copiedWgt = nf.CopyBoneWeightsToBuffer(model, boneWeightsByteBuffer, vertexCount);
            if (copiedWgt == 0) {
                logger.warn("骨骼权重数据复制失败");
            }
            FloatBuffer boneWeightsFloatBuffer = boneWeightsByteBuffer.asFloatBuffer();
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, boneWgtVbo);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, boneWeightsFloatBuffer, GL46C.GL_STATIC_DRAW);
            
            // 顶点颜色缓冲区（Minecraft 标准属性：白色 + 全不透明）
            ByteBuffer colorBuffer = ByteBuffer.allocateDirect(vertexCount * 16);
            colorBuffer.order(ByteOrder.LITTLE_ENDIAN);
            
            // UV1 缓冲区（overlay）— 静态数据，创建时即上传到 GPU
            ByteBuffer uv1Buffer = ByteBuffer.allocateDirect(vertexCount * 8);
            uv1Buffer.order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < vertexCount; i++) {
                uv1Buffer.putInt(15);
                uv1Buffer.putInt(15);
            }
            uv1Buffer.flip();
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, uv1Vbo);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, uv1Buffer, GL46C.GL_STATIC_DRAW);
            
            // UV2 缓冲区（lightmap）
            ByteBuffer uv2Buffer = ByteBuffer.allocateDirect(vertexCount * 8);
            uv2Buffer.order(ByteOrder.LITTLE_ENDIAN);
            
            // Color 缓冲区——初始分配 GPU 存储（避免 Iris 模式下缓冲区为空）
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, colorVbo);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, vertexCount * 16, GL46C.GL_DYNAMIC_DRAW);
            
            // 材质
            Material[] mats = new Material[(int) nf.GetMaterialCount(model)];
            for (int i = 0; i < mats.length; ++i) {
                mats[i] = new Material();
                String texFilename = nf.GetMaterialTex(model, i);
                if (!texFilename.isEmpty()) {
                    MMDTextureManager.Texture mgrTex = MMDTextureManager.GetTexture(texFilename);
                    if (mgrTex != null) {
                        mats[i].tex = mgrTex.tex;
                        mats[i].hasAlpha = mgrTex.hasAlpha;
                    }
                }
            }
            
            // lightMap 材质
            lightMapMaterial = new Material();
            MMDTextureManager.Texture mgrTex = MMDTextureManager.GetTexture(modelDir + "/lightMap.png");
            if (mgrTex != null) {
                lightMapMaterial.tex = mgrTex.tex;
                lightMapMaterial.hasAlpha = mgrTex.hasAlpha;
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
            
            // 骨骼矩阵缓冲区（统一使用 MemoryUtil，在 dispose() 中显式释放）
            boneMatricesBuffer = MemoryUtil.memAllocFloat(boneCount * 16);
            boneMatricesByteBuffer = MemoryUtil.memAlloc(boneCount * 64);
            boneMatricesByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            
            // 创建 Compute Shader 输出缓冲区（每实例独立，双重用途：SSBO + VBO）
            outputBuffers = SkinningComputeShader.createOutputBuffers(vertexCount);
            
            // 创建骨骼矩阵 SSBO（每实例独立）
            boneMatrixSSBO = SkinningComputeShader.createBoneMatrixBuffer();
            
            // 预分配矩阵缓冲区
            modelViewMatBuff = MemoryUtil.memAllocFloat(16);
            projMatBuff = MemoryUtil.memAllocFloat(16);
            
            // 初始化顶点 Morph 数据
            nf.InitGpuMorphData(model);
            int morphCount = (int) nf.GetVertexMorphCount(model);
            if (morphCount > 0) {
                morphWeightsBuffer = MemoryUtil.memAllocFloat(morphCount);
                morphBuffers = SkinningComputeShader.createMorphBuffers(morphCount);
                logger.info("GPU Morph 初始化: {} 个顶点 Morph", morphCount);
            }
            
            // 初始化 UV Morph 数据
            nf.InitGpuUvMorphData(model);
            int uvMorphCnt = nf.GetUvMorphCount(model);
            if (uvMorphCnt > 0) {
                uvMorphWeightsBuf = MemoryUtil.memAllocFloat(uvMorphCnt);
                uvMorphBuffers = SkinningComputeShader.createUvMorphBuffers(uvMorphCnt);
                skinnedUvBuf = SkinningComputeShader.createSkinnedUvBuffer(vertexCount);
                logger.info("GPU UV Morph 初始化: {} 个 UV Morph", uvMorphCnt);
            } else {
                // 即使没有 UV Morph，也创建蒙皮 UV 输出缓冲区用于 Compute Shader 写入
                skinnedUvBuf = SkinningComputeShader.createSkinnedUvBuffer(vertexCount);
            }
            
            // 初始化材质 Morph 结果缓冲区
            int matMorphCount = nf.GetMaterialMorphResultCount(model);
            if (matMorphCount > 0) {
                int floatCount = matMorphCount * 28;
                matMorphResultsBuf = MemoryUtil.memAllocFloat(floatCount);
                matMorphResultsByteBuf = MemoryUtil.memAlloc(floatCount * 4);
                matMorphResultsByteBuf.order(ByteOrder.LITTLE_ENDIAN);
            }
            
            // 构建结果
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
            // UV Morph
            result.uvMorphCount = uvMorphCnt;
            result.skinnedUvBuffer = skinnedUvBuf;
            if (uvMorphCnt > 0) {
                result.uvMorphWeightsBuffer = uvMorphWeightsBuf;
                result.uvMorphWeightsByteBuffer = ByteBuffer.allocateDirect(uvMorphCnt * 4);
                result.uvMorphWeightsByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                result.uvMorphOffsetsSSBO = uvMorphBuffers[0];
                result.uvMorphWeightsSSBO = uvMorphBuffers[1];
            }
            // 材质 Morph
            result.materialMorphResultCount = matMorphCount;
            result.materialMorphResultsBuffer = matMorphResultsBuf;
            result.materialMorphResultsByteBuffer = matMorphResultsByteBuf;
            result.initialized = true;
            
            // 启用自动眨眼
            nf.SetAutoBlinkEnabled(model, true);
            
            GL46C.glBindVertexArray(0);
            logger.info("GPU 蒙皮模型创建成功（Compute Shader）: {} 顶点, {} 骨骼", vertexCount, boneCount);
            return result;
            
        } catch (Exception e) {
            // 异常时清理所有已分配的资源
            logger.error("GPU 蒙皮模型创建失败，清理资源: {}", e.getMessage());
            
            // 清理原生模型
            nf.DeleteModel(model);
            
            // 清理 GL 资源
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
            
            // 清理 MemoryUtil 分配的缓冲区
            if (boneMatricesBuffer != null) MemoryUtil.memFree(boneMatricesBuffer);
            if (boneMatricesByteBuffer != null) MemoryUtil.memFree(boneMatricesByteBuffer);
            if (modelViewMatBuff != null) MemoryUtil.memFree(modelViewMatBuff);
            if (projMatBuff != null) MemoryUtil.memFree(projMatBuff);
            if (morphWeightsBuffer != null) MemoryUtil.memFree(morphWeightsBuffer);
            if (uvMorphWeightsBuf != null) MemoryUtil.memFree(uvMorphWeightsBuf);
            if (matMorphResultsBuf != null) MemoryUtil.memFree(matMorphResultsBuf);
            if (matMorphResultsByteBuf != null) MemoryUtil.memFree(matMorphResultsByteBuf);
            
            return null;
        }
    }
    
    @Override
    public void render(Entity entityIn, float entityYaw, float entityPitch, Vector3f entityTrans, float tickDelta, PoseStack mat, int packedLight, RenderContext context) {
        if (!initialized) return;
        
        if (entityIn instanceof LivingEntity && tickDelta != 1.0f) {
            renderLivingEntity((LivingEntity) entityIn, entityYaw, entityPitch, entityTrans, tickDelta, mat, packedLight, context);
            return;
        }
        Update();
        RenderModel(entityIn, entityYaw, entityPitch, entityTrans, mat);
    }
    
    private void renderLivingEntity(LivingEntity entityIn, float entityYaw, float entityPitch, Vector3f entityTrans, float tickDelta, PoseStack mat, int packedLight, RenderContext context) {
        // 头部角度处理（舞台播放时归零，由 VMD 动画控制）
        boolean stagePlaying = MMDCameraController.getInstance().isStagePlayingModel(model);
        if (stagePlaying) {
            nf.SetHeadAngle(model, 0.0f, 0.0f, 0.0f, context.isWorldScene());
        } else {
            float headAngleX = Mth.clamp(entityIn.getXRot(), -50.0f, 50.0f);
            float headAngleY = (entityYaw - Mth.lerp(tickDelta, entityIn.yHeadRotO, entityIn.yHeadRot)) % 360.0f;
            if (headAngleY < -180.0f) headAngleY += 360.0f;
            else if (headAngleY > 180.0f) headAngleY -= 360.0f;
            headAngleY = Mth.clamp(headAngleY, -80.0f, 80.0f);
            
            float pitchRad = headAngleX * ((float) Math.PI / 180F);
            float yawRad = context.isInventoryScene() ? -headAngleY * ((float) Math.PI / 180F) : headAngleY * ((float) Math.PI / 180F);
            nf.SetHeadAngle(model, pitchRad, yawRad, 0.0f, context.isWorldScene());
        }
        
        // 使用公共工具类更新眼球追踪（传递模型名称，使用每模型独立配置）
        if (!stagePlaying) {
            EyeTrackingHelper.updateEyeTracking(nf, model, entityIn, entityYaw, tickDelta, getModelName());
        }
        
        // 传递实体位置和朝向给物理系统（用于人物移动时的惯性效果）
        // 位置用于计算速度差，朝向用于将世界速度转换到模型局部空间
        // 注意：模型渲染时缩放了 0.09 倍，所以位置也需要同步缩放
        final float MODEL_SCALE = 0.09f;
        float posX = (float)(Mth.lerp(tickDelta, entityIn.xo, entityIn.getX()) * MODEL_SCALE);
        float posY = (float)(Mth.lerp(tickDelta, entityIn.yo, entityIn.getY()) * MODEL_SCALE);
        float posZ = (float)(Mth.lerp(tickDelta, entityIn.zo, entityIn.getZ()) * MODEL_SCALE);
        // 使用实体的身体朝向（不是头部朝向）
        float bodyYaw = Mth.lerp(tickDelta, entityIn.yBodyRotO, entityIn.yBodyRot) * ((float) Math.PI / 180F);
        nf.SetModelPositionAndYaw(model, posX, posY, posZ, bodyYaw);
        
        Update();
        RenderModel(entityIn, entityYaw, entityPitch, entityTrans, mat);
    }
    
    private void Update() {
        long currentTime = System.currentTimeMillis();
        if (lastUpdateTime < 0) {
            lastUpdateTime = currentTime;
            return;
        }
        
        float deltaTime = (currentTime - lastUpdateTime) / 1000.0f;
        lastUpdateTime = currentTime;
        
        // 跳过零或负增量帧，避免高帧率下动画加速
        if (deltaTime <= 0.0f) {
            return;
        }
        // 限制 deltaTime 上限，防止暂停后物理爆炸
        if (deltaTime > MAX_DELTA_TIME) {
            deltaTime = MAX_DELTA_TIME;
        }
        
        nf.UpdateAnimationOnly(model, deltaTime);
    }
    
    private void RenderModel(Entity entityIn, float entityYaw, float entityPitch, Vector3f entityTrans, PoseStack deliverStack) {
        Minecraft MCinstance = Minecraft.getInstance();
        renderModelInternal(entityIn, entityYaw, entityPitch, entityTrans, deliverStack, MCinstance);
    }
    
    private void renderModelInternal(Entity entityIn, float entityYaw, float entityPitch, Vector3f entityTrans, PoseStack deliverStack, Minecraft MCinstance) {
        // 光照计算
        MCinstance.level.updateSkyBrightness();
        int eyeHeight = (int)(entityIn.getEyeY() - entityIn.getBlockY());
        int blockLight = entityIn.level().getBrightness(LightLayer.BLOCK, entityIn.blockPosition().above(eyeHeight));
        int skyLight = entityIn.level().getBrightness(LightLayer.SKY, entityIn.blockPosition().above(eyeHeight));
        float skyDarken = MCinstance.level.getSkyDarken();
        
        float blockLightFactor = blockLight / 15.0f;
        float skyLightFactor = (skyLight / 15.0f) * ((15.0f - skyDarken) / 15.0f);
        float lightIntensity = Math.max(blockLightFactor, skyLightFactor);
        lightIntensity = 0.1f + lightIntensity * 0.9f;
        
        light0Direction.set(1.0f, 0.75f, 0.0f).normalize();
        light1Direction.set(-1.0f, 0.75f, 0.0f).normalize();
        float yawRad = entityYaw * ((float) Math.PI / 180F);
        light0Direction.rotate(tempQuat.identity().rotateY(yawRad));
        light1Direction.rotate(tempQuat.identity().rotateY(yawRad));
        
        // 变换
        deliverStack.mulPose(tempQuat.identity().rotateY(-yawRad));
        deliverStack.mulPose(tempQuat.identity().rotateX(entityPitch * ((float) Math.PI / 180F)));
        deliverStack.translate(entityTrans.x, entityTrans.y, entityTrans.z);
        float baseScale = 0.09f * com.shiroha.mmdskin.config.ModelConfigManager.getConfig(getModelName()).modelScale;
        deliverStack.scale(baseScale, baseScale, baseScale);
        
        uploadBoneMatrices();
        if (vertexMorphCount > 0) {
            uploadMorphData();
        }
        if (uvMorphCount > 0) {
            uploadUvMorphData();
        }
        if (materialMorphResultCount > 0) {
            fetchMaterialMorphResults();
        }
        
        // Compute Shader 蒙皮（含 UV Morph）
        computeShader.dispatch(
            positionBufferObject, normalBufferObject,
            boneIndicesBufferObject, boneWeightsBufferObject,
            skinnedPositionsBuffer, skinnedNormalsBuffer,
            boneMatrixSSBO,
            morphOffsetsSSBO, morphWeightsSSBO,
            vertexCount, vertexMorphCount,
            uv0BufferObject,
            uvMorphOffsetsSSBO, uvMorphWeightsSSBO,
            skinnedUvBuffer, uvMorphCount
        );
        
        boolean useToon = ConfigManager.isToonRenderingEnabled();
        if (useToon) {
            if (toonShaderCpu == null) {
                toonShaderCpu = new ToonShaderCpu();
                if (!toonShaderCpu.init()) {
                    logger.warn("ToonShaderCpu 初始化失败，回退到普通着色");
                    useToon = false;
                }
            }
        }
        
        BufferUploader.reset();
        GL46C.glBindVertexArray(vertexArrayObject);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.blendEquation(GL46C.GL_FUNC_ADD);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        
        modelViewMatBuff.clear();
        projMatBuff.clear();
        deliverStack.last().pose().get(modelViewMatBuff);
        RenderSystem.getProjectionMatrix().get(projMatBuff);
        
        GL46C.glBindBuffer(GL46C.GL_ELEMENT_ARRAY_BUFFER, indexBufferObject);
        
        currentDeliverStack = deliverStack;
        if (useToon && toonShaderCpu != null && toonShaderCpu.isInitialized()) {
            renderToon(MCinstance, lightIntensity, blockLight, skyLight, skyDarken);
        } else {
            renderNormal(MCinstance, lightIntensity, blockLight, skyLight, skyDarken);
        }
        
        // === 清理 ===
        cleanupVertexAttributes();
        
        GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, 0);
        GL46C.glBindBuffer(GL46C.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL46C.glBindVertexArray(0);
        RenderSystem.activeTexture(GL46C.GL_TEXTURE0);
        
        ShaderInstance currentShader = RenderSystem.getShader();
        if (currentShader != null) {
            currentShader.clear();
        }
        BufferUploader.reset();
    }
    
    /**
     * 清理所有启用的顶点属性数组
     */
    private void cleanupVertexAttributes() {
        if (positionLocation != -1) GL46C.glDisableVertexAttribArray(positionLocation);
        if (normalLocation != -1) GL46C.glDisableVertexAttribArray(normalLocation);
        if (uv0Location != -1) GL46C.glDisableVertexAttribArray(uv0Location);
        if (uv1Location != -1) GL46C.glDisableVertexAttribArray(uv1Location);
        if (uv2Location != -1) GL46C.glDisableVertexAttribArray(uv2Location);
        if (colorLocation != -1) GL46C.glDisableVertexAttribArray(colorLocation);
        if (I_positionLocation != -1) GL46C.glDisableVertexAttribArray(I_positionLocation);
        if (I_normalLocation != -1) GL46C.glDisableVertexAttribArray(I_normalLocation);
        if (I_uv0Location != -1) GL46C.glDisableVertexAttribArray(I_uv0Location);
        if (I_uv2Location != -1) GL46C.glDisableVertexAttribArray(I_uv2Location);
        if (I_colorLocation != -1) GL46C.glDisableVertexAttribArray(I_colorLocation);
    }
    
    /**
     * 普通渲染模式（通过 Minecraft 标准 ShaderInstance 管线）
     * 
     * 使用 RenderSystem.getShader() 获取当前着色器，
     * 当 Iris 激活时返回的是 Iris 的 G-buffer 着色器，从而正确写入 MRT。
     */
    private void renderNormal(Minecraft MCinstance, float lightIntensity, int blockLight, int skyLight, float skyDarken) {
        // 获取 Minecraft 当前着色器（Iris 激活时会被替换为 G-buffer 着色器）
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) {
            logger.error("[GPU蒙皮] RenderSystem.getShader() 返回 null，跳过渲染");
            return;
        }
        shaderProgram = shader.getId();
        setUniforms(shader, currentDeliverStack);
        shader.apply();
        
        GL46C.glUseProgram(shaderProgram);
        updateLocation(shaderProgram);
        
        // === UV2 和 Color：所有顶点值相同，使用常量顶点属性（避免逐顶点循环和缓冲区上传）===
        boolean irisActive = IrisCompat.isIrisShaderActive();
        int blockBrightness = 16 * blockLight;
        // Iris 兼容：UV2 不应包含 skyDarken，Iris 的光照管线会自行处理昼夜变化
        int skyBrightness = irisActive ? (16 * skyLight) : Math.round((15.0f - skyDarken) * (skyLight / 15.0f) * 16);
        if (uv2Location != -1) GL46C.glVertexAttribI4i(uv2Location, blockBrightness, skyBrightness, 0, 0);
        if (I_uv2Location != -1) GL46C.glVertexAttribI4i(I_uv2Location, blockBrightness, skyBrightness, 0, 0);
        // Iris 兼容：Color 应为白色，让 Iris 的 G-buffer 管线完全控制光照；
        // 否则 lightIntensity 预乘后会与 Iris 自身光照叠加导致夜间过暗
        float colorFactor = irisActive ? 1.0f : lightIntensity;
        if (colorLocation != -1) GL46C.glVertexAttrib4f(colorLocation, colorFactor, colorFactor, colorFactor, 1.0f);
        if (I_colorLocation != -1) GL46C.glVertexAttrib4f(I_colorLocation, colorFactor, colorFactor, colorFactor, 1.0f);
        
        // 绑定顶点属性（标准名称）
        if (positionLocation != -1) {
            GL46C.glEnableVertexAttribArray(positionLocation);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, skinnedPositionsBuffer);
            GL46C.glVertexAttribPointer(positionLocation, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (normalLocation != -1) {
            GL46C.glEnableVertexAttribArray(normalLocation);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, skinnedNormalsBuffer);
            GL46C.glVertexAttribPointer(normalLocation, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        // UV0: 使用 Compute Shader 输出的蒙皮后 UV（含 UV Morph）
        int activeUvBuffer = (skinnedUvBuffer > 0) ? skinnedUvBuffer : uv0BufferObject;
        if (uv0Location != -1) {
            GL46C.glEnableVertexAttribArray(uv0Location);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, activeUvBuffer);
            GL46C.glVertexAttribPointer(uv0Location, 2, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (uv1Location != -1) {
            GL46C.glEnableVertexAttribArray(uv1Location);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, uv1BufferObject);
            GL46C.glVertexAttribIPointer(uv1Location, 2, GL46C.GL_INT, 0, 0);
        }
        
        // 绑定 Iris 重命名属性
        if (I_positionLocation != -1) {
            GL46C.glEnableVertexAttribArray(I_positionLocation);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, skinnedPositionsBuffer);
            GL46C.glVertexAttribPointer(I_positionLocation, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (I_normalLocation != -1) {
            GL46C.glEnableVertexAttribArray(I_normalLocation);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, skinnedNormalsBuffer);
            GL46C.glVertexAttribPointer(I_normalLocation, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (I_uv0Location != -1) {
            GL46C.glEnableVertexAttribArray(I_uv0Location);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, activeUvBuffer);
            GL46C.glVertexAttribPointer(I_uv0Location, 2, GL46C.GL_FLOAT, false, 0, 0);
        }
        
        drawAllSubMeshes(MCinstance);
    }
    
    /**
     * Toon 渲染模式（使用 ToonShaderCpu，蒙皮后的顶点数据来自 Compute Shader）
     * 
     * Iris 兼容：
     *   Iris 激活时，先通过 ExtendedShader.apply() 绑定 G-buffer FBO + MRT draw buffers，
     *   再切换到 Toon 着色器程序。Toon 片段着色器已声明 layout(location=0..3) 多输出，
     *   确保 Iris 的 draw buffers 全部被写入合理数据，避免透明。
     */
    private void renderToon(Minecraft MCinstance, float lightIntensity, int blockLight, int skyLight, float skyDarken) {
        
        // Iris 兼容：绑定 Iris G-buffer FBO（如果 Iris 光影激活）
        boolean irisActive = IrisCompat.isIrisShaderActive();
        if (irisActive) {
            ShaderInstance irisShader = RenderSystem.getShader();
            if (irisShader != null) {
                setUniforms(irisShader, currentDeliverStack);
                irisShader.apply();  // 绑定 Iris G-buffer FBO + MRT draw buffers
            }
        }
        
        // ===== 第一遍：描边 =====
        if (toonConfig.isOutlineEnabled()) {
            toonShaderCpu.useOutline();
            
            int posLoc = toonShaderCpu.getOutlinePositionLocation();
            int norLoc = toonShaderCpu.getOutlineNormalLocation();
            
            if (posLoc != -1) {
                GL46C.glEnableVertexAttribArray(posLoc);
                GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, skinnedPositionsBuffer);
                GL46C.glVertexAttribPointer(posLoc, 3, GL46C.GL_FLOAT, false, 0, 0);
            }
            if (norLoc != -1) {
                GL46C.glEnableVertexAttribArray(norLoc);
                GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, skinnedNormalsBuffer);
                GL46C.glVertexAttribPointer(norLoc, 3, GL46C.GL_FLOAT, false, 0, 0);
            }
            
            toonShaderCpu.setOutlineProjectionMatrix(projMatBuff);
            toonShaderCpu.setOutlineModelViewMatrix(modelViewMatBuff);
            toonShaderCpu.setOutlineWidth(toonConfig.getOutlineWidth());
            toonShaderCpu.setOutlineColor(
                toonConfig.getOutlineColorR(),
                toonConfig.getOutlineColorG(),
                toonConfig.getOutlineColorB()
            );
            
            GL46C.glCullFace(GL46C.GL_FRONT);
            RenderSystem.enableCull();
            
            // 绘制描边
            long subMeshCount = nf.GetSubMeshCount(model);
            for (long i = 0; i < subMeshCount; ++i) {
                int materialID = nf.GetSubMeshMaterialID(model, i);
                if (!nf.IsMaterialVisible(model, materialID)) continue;
                if (nf.GetMaterialAlpha(model, materialID) * getMaterialMorphAlpha(materialID) < 0.001f) continue;
                
                long startPos = (long) nf.GetSubMeshBeginIndex(model, i) * indexElementSize;
                int count = nf.GetSubMeshVertexCount(model, i);
                GL46C.glDrawElements(GL46C.GL_TRIANGLES, count, indexType, startPos);
            }
            
            // 恢复背面剔除
            GL46C.glCullFace(GL46C.GL_BACK);
            
            // 禁用描边着色器的顶点属性
            if (posLoc != -1) GL46C.glDisableVertexAttribArray(posLoc);
            if (norLoc != -1) GL46C.glDisableVertexAttribArray(norLoc);
        }
        
        // ===== 第二遍：主体（Toon 着色） =====
        toonShaderCpu.useMain();
        
        int toonPosLoc = toonShaderCpu.getPositionLocation();
        int toonNorLoc = toonShaderCpu.getNormalLocation();
        int uvLoc = toonShaderCpu.getUv0Location();
        
        if (toonPosLoc != -1) {
            GL46C.glEnableVertexAttribArray(toonPosLoc);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, skinnedPositionsBuffer);
            GL46C.glVertexAttribPointer(toonPosLoc, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (toonNorLoc != -1) {
            GL46C.glEnableVertexAttribArray(toonNorLoc);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, skinnedNormalsBuffer);
            GL46C.glVertexAttribPointer(toonNorLoc, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (uvLoc != -1) {
            GL46C.glEnableVertexAttribArray(uvLoc);
            int toonUvBuffer = (skinnedUvBuffer > 0) ? skinnedUvBuffer : uv0BufferObject;
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, toonUvBuffer);
            GL46C.glVertexAttribPointer(uvLoc, 2, GL46C.GL_FLOAT, false, 0, 0);
        }
        
        toonShaderCpu.setProjectionMatrix(projMatBuff);
        toonShaderCpu.setModelViewMatrix(modelViewMatBuff);
        toonShaderCpu.setSampler0(0);
        toonShaderCpu.setLightIntensity(lightIntensity);
        toonShaderCpu.setToonLevels(toonConfig.getToonLevels());
        toonShaderCpu.setRimLight(toonConfig.getRimPower(), toonConfig.getRimIntensity());
        toonShaderCpu.setShadowColor(
            toonConfig.getShadowColorR(),
            toonConfig.getShadowColorG(),
            toonConfig.getShadowColorB()
        );
        toonShaderCpu.setSpecular(toonConfig.getSpecularPower(), toonConfig.getSpecularIntensity());
        
        drawAllSubMeshes(MCinstance);
        
        if (toonPosLoc != -1) GL46C.glDisableVertexAttribArray(toonPosLoc);
        if (toonNorLoc != -1) GL46C.glDisableVertexAttribArray(toonNorLoc);
        if (uvLoc != -1) GL46C.glDisableVertexAttribArray(uvLoc);
        
        GL46C.glUseProgram(0);
    }
    
    /**
     * 绘制所有子网格
     */
    private void drawAllSubMeshes(Minecraft MCinstance) {
        RenderSystem.activeTexture(GL46C.GL_TEXTURE0);
        long subMeshCount = nf.GetSubMeshCount(model);
        
        for (long i = 0; i < subMeshCount; ++i) {
            int materialID = nf.GetSubMeshMaterialID(model, i);
            if (!nf.IsMaterialVisible(model, materialID)) continue;
            
            float alpha = nf.GetMaterialAlpha(model, materialID);
            // 应用材质 Morph 对 alpha 的调制
            float morphAlpha = getMaterialMorphAlpha(materialID);
            if (alpha * morphAlpha < 0.001f) continue;
            
            if (nf.GetMaterialBothFace(model, materialID)) {
                RenderSystem.disableCull();
            } else {
                RenderSystem.enableCull();
            }
            
            int texId;
            if (mats[materialID].tex == 0) {
                texId = MCinstance.getTextureManager().getTexture(TextureManager.INTENTIONAL_MISSING_TEXTURE).getId();
            } else {
                texId = mats[materialID].tex;
            }
            RenderSystem.setShaderTexture(0, texId);
            GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, texId);
            
            long startPos = (long) nf.GetSubMeshBeginIndex(model, i) * indexElementSize;
            int count = nf.GetSubMeshVertexCount(model, i);
            
            GL46C.glDrawElements(GL46C.GL_TRIANGLES, count, indexType, startPos);
        }
    }
    
    /**
     * 上传骨骼矩阵到 Compute Shader 的 SSBO
     */
    private void uploadBoneMatrices() {
        boneMatricesByteBuffer.clear();
        
        int copiedBones = nf.CopySkinningMatricesToBuffer(model, boneMatricesByteBuffer);
        if (copiedBones == 0) return;
        
        boneMatricesBuffer.clear();
        boneMatricesByteBuffer.position(0);
        FloatBuffer floatView = boneMatricesByteBuffer.asFloatBuffer();
        for (int i = 0; i < copiedBones * 16; i++) {
            boneMatricesBuffer.put(floatView.get());
        }
        boneMatricesBuffer.flip();
        
        computeShader.uploadBoneMatrices(boneMatrixSSBO, boneMatricesBuffer, copiedBones);
    }
    
    /**
     * 上传 Morph 数据到 Compute Shader 的 SSBO
     */
    private void uploadMorphData() {
        if (vertexMorphCount <= 0) return;
        
        // 首次上传偏移数据（静态）
        if (!morphDataUploaded) {
            long offsetsSize = nf.GetGpuMorphOffsetsSize(model);
            if (offsetsSize > 0) {
                // 边界检查：避免 long 截断为负数导致 memAlloc 异常
                if (offsetsSize > Integer.MAX_VALUE) {
                    logger.error("Morph 数据过大 ({} bytes)，超过 2GB 限制，跳过 GPU Morph", offsetsSize);
                    vertexMorphCount = 0; // 禁用 Morph 以避免后续错误
                } else {
                    // 使用 MemoryUtil.memAlloc 分配原生内存，避免 Java 直接内存池 OOM
                    ByteBuffer offsetsBuffer = MemoryUtil.memAlloc((int) offsetsSize);
                    offsetsBuffer.order(ByteOrder.LITTLE_ENDIAN);
                    try {
                        nf.CopyGpuMorphOffsetsToBuffer(model, offsetsBuffer);
                        computeShader.uploadMorphOffsets(morphOffsetsSSBO, offsetsBuffer);
                        morphDataUploaded = true;
                    } finally {
                        MemoryUtil.memFree(offsetsBuffer);
                    }
                }
            }
        }
        
        // 每帧更新权重（复用预分配缓冲区）
        if (morphWeightsBuffer != null && morphWeightsByteBuffer != null) {
            morphWeightsByteBuffer.clear();
            nf.CopyGpuMorphWeightsToBuffer(model, morphWeightsByteBuffer);
            morphWeightsBuffer.clear();
            morphWeightsByteBuffer.position(0);
            morphWeightsBuffer.put(morphWeightsByteBuffer.asFloatBuffer());
            morphWeightsBuffer.flip();
            computeShader.updateMorphWeights(morphWeightsSSBO, morphWeightsBuffer);
        }
    }
    
    /**
     * 上传 UV Morph 数据到 Compute Shader 的 SSBO
     */
    private void uploadUvMorphData() {
        if (uvMorphCount <= 0) return;
        
        // 首次上传偏移数据（静态）
        if (!uvMorphDataUploaded) {
            long offsetsSize = nf.GetGpuUvMorphOffsetsSize(model);
            if (offsetsSize > 0 && offsetsSize <= Integer.MAX_VALUE) {
                ByteBuffer offsetsBuffer = MemoryUtil.memAlloc((int) offsetsSize);
                offsetsBuffer.order(ByteOrder.LITTLE_ENDIAN);
                try {
                    nf.CopyGpuUvMorphOffsetsToBuffer(model, offsetsBuffer);
                    computeShader.uploadUvMorphOffsets(uvMorphOffsetsSSBO, offsetsBuffer);
                    uvMorphDataUploaded = true;
                } finally {
                    MemoryUtil.memFree(offsetsBuffer);
                }
            }
        }
        
        // 每帧更新权重
        if (uvMorphWeightsBuffer != null && uvMorphWeightsByteBuffer != null) {
            uvMorphWeightsByteBuffer.clear();
            nf.CopyGpuUvMorphWeightsToBuffer(model, uvMorphWeightsByteBuffer);
            uvMorphWeightsBuffer.clear();
            uvMorphWeightsByteBuffer.position(0);
            uvMorphWeightsBuffer.put(uvMorphWeightsByteBuffer.asFloatBuffer());
            uvMorphWeightsBuffer.flip();
            computeShader.updateUvMorphWeights(uvMorphWeightsSSBO, uvMorphWeightsBuffer);
        }
    }
    
    /**
     * 从 Rust 端获取材质 Morph 结果
     */
    private void fetchMaterialMorphResults() {
        if (materialMorphResultCount <= 0 || materialMorphResultsBuffer == null) return;
        
        materialMorphResultsByteBuffer.clear();
        nf.CopyMaterialMorphResultsToBuffer(model, materialMorphResultsByteBuffer);
        materialMorphResultsBuffer.clear();
        materialMorphResultsByteBuffer.position(0);
        materialMorphResultsBuffer.put(materialMorphResultsByteBuffer.asFloatBuffer());
        materialMorphResultsBuffer.flip();
    }
    
    /**
     * 获取指定材质的 Morph diffuse alpha 乘数
     * 用于渲染时调制材质透明度
     */
    private float getMaterialMorphAlpha(int materialIndex) {
        if (materialMorphResultsBuffer == null || materialIndex >= materialMorphResultCount) return 1.0f;
        // diffuse.w 位于每材质 28 float 块的第 3 个位置（index 3）
        int offset = materialIndex * 28 + 3;
        if (offset < materialMorphResultsBuffer.capacity()) {
            return materialMorphResultsBuffer.get(offset);
        }
        return 1.0f;
    }
    
    /**
     * 更新着色器属性位置（基于当前绑定的着色器程序）
     * 支持 Minecraft 标准属性和 Iris 重命名属性
     */
    private void updateLocation(int program) {
        positionLocation = GlStateManager._glGetAttribLocation(program, "Position");
        normalLocation = GlStateManager._glGetAttribLocation(program, "Normal");
        uv0Location = GlStateManager._glGetAttribLocation(program, "UV0");
        uv1Location = GlStateManager._glGetAttribLocation(program, "UV1");
        uv2Location = GlStateManager._glGetAttribLocation(program, "UV2");
        colorLocation = GlStateManager._glGetAttribLocation(program, "Color");
        
        // Iris 重命名属性（Iris 会将标准属性名加上 "iris_" 前缀）
        I_positionLocation = GlStateManager._glGetAttribLocation(program, "iris_Position");
        I_normalLocation = GlStateManager._glGetAttribLocation(program, "iris_Normal");
        I_uv0Location = GlStateManager._glGetAttribLocation(program, "iris_UV0");
        I_uv2Location = GlStateManager._glGetAttribLocation(program, "iris_UV2");
        I_colorLocation = GlStateManager._glGetAttribLocation(program, "iris_Color");
    }
    
    /**
     * 设置 Minecraft ShaderInstance 的标准 Uniform（模型视图矩阵、投影矩阵、光照等）
     */
    private void setUniforms(ShaderInstance shader, PoseStack deliverStack) {
        if (shader.MODEL_VIEW_MATRIX != null)
            shader.MODEL_VIEW_MATRIX.set(deliverStack.last().pose());
        
        if (shader.PROJECTION_MATRIX != null)
            shader.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
        
        if (shader.INVERSE_VIEW_ROTATION_MATRIX != null)
            shader.INVERSE_VIEW_ROTATION_MATRIX.set(RenderSystem.getInverseViewRotationMatrix());
        
        if (shader.COLOR_MODULATOR != null)
            shader.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        
        if (shader.LIGHT0_DIRECTION != null)
            shader.LIGHT0_DIRECTION.set(light0Direction);
        
        if (shader.LIGHT1_DIRECTION != null)
            shader.LIGHT1_DIRECTION.set(light1Direction);
        
        if (shader.FOG_START != null)
            shader.FOG_START.set(RenderSystem.getShaderFogStart());
        
        if (shader.FOG_END != null)
            shader.FOG_END.set(RenderSystem.getShaderFogEnd());
        
        if (shader.FOG_COLOR != null)
            shader.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        
        if (shader.FOG_SHAPE != null)
            shader.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
        
        if (shader.TEXTURE_MATRIX != null)
            shader.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
        
        if (shader.GAME_TIME != null)
            shader.GAME_TIME.set(RenderSystem.getShaderGameTime());
        
        if (shader.SCREEN_SIZE != null) {
            Window window = Minecraft.getInstance().getWindow();
            shader.SCREEN_SIZE.set((float) window.getScreenWidth(), (float) window.getScreenHeight());
        }
        if (shader.LINE_WIDTH != null)
            shader.LINE_WIDTH.set(RenderSystem.getShaderLineWidth());
        
        shader.setSampler("Sampler1", lightMapMaterial.tex);
        shader.setSampler("Sampler2", lightMapMaterial.tex);
        
        // Iris 兼容：ExtendedShader.apply() 从 RenderSystem.getShaderTexture() 读取纹理，
        // 而非 shader.setSampler() 设置的值，需要同步设置
        RenderSystem.setShaderTexture(1, lightMapMaterial.tex);
        RenderSystem.setShaderTexture(2, lightMapMaterial.tex);
    }
    
    @Override
    public void ChangeAnim(long anim, long layer) {
        nf.ChangeModelAnim(model, anim, layer);
    }
    
    @Override
    public void TransitionAnim(long anim, long layer, float transitionTime) {
        nf.TransitionLayerTo(model, layer, anim, transitionTime);
    }
    
    @Override
    public void ResetPhysics() {
        nf.ResetModelPhysics(model);
    }
    
    @Override
    public long GetModelLong() {
        return model;
    }
    
    @Override
    public String GetModelDir() {
        return modelDir;
    }
    
    @Override
    public String getModelName() {
        if (cachedModelName == null) {
            cachedModelName = IMMDModel.super.getModelName();
        }
        return cachedModelName;
    }
    
    @Override
    public void dispose() {
        // 防护：避免 double-free 和 use-after-free
        if (!initialized) return;
        initialized = false;
        
        if (model != 0) {
            nf.DeleteModel(model);
            model = 0;
        }
        
        // 释放 OpenGL 资源
        GL46C.glDeleteVertexArrays(vertexArrayObject);
        GL46C.glDeleteBuffers(indexBufferObject);
        GL46C.glDeleteBuffers(positionBufferObject);
        GL46C.glDeleteBuffers(normalBufferObject);
        GL46C.glDeleteBuffers(uv0BufferObject);
        GL46C.glDeleteBuffers(boneIndicesBufferObject);
        GL46C.glDeleteBuffers(boneWeightsBufferObject);
        GL46C.glDeleteBuffers(colorBufferObject);
        GL46C.glDeleteBuffers(uv1BufferObject);
        GL46C.glDeleteBuffers(uv2BufferObject);
        GL46C.glDeleteBuffers(skinnedPositionsBuffer);
        GL46C.glDeleteBuffers(skinnedNormalsBuffer);
        
        // 释放每实例 SSBO
        if (boneMatrixSSBO > 0) GL46C.glDeleteBuffers(boneMatrixSSBO);
        if (morphOffsetsSSBO > 0) GL46C.glDeleteBuffers(morphOffsetsSSBO);
        if (morphWeightsSSBO > 0) GL46C.glDeleteBuffers(morphWeightsSSBO);
        if (uvMorphOffsetsSSBO > 0) GL46C.glDeleteBuffers(uvMorphOffsetsSSBO);
        if (uvMorphWeightsSSBO > 0) GL46C.glDeleteBuffers(uvMorphWeightsSSBO);
        if (skinnedUvBuffer > 0) GL46C.glDeleteBuffers(skinnedUvBuffer);
        boneMatrixSSBO = 0;
        morphOffsetsSSBO = 0;
        morphWeightsSSBO = 0;
        uvMorphOffsetsSSBO = 0;
        uvMorphWeightsSSBO = 0;
        skinnedUvBuffer = 0;
        
        // 释放自建的 lightMap 纹理（来自 MMDTextureManager 的不在此删除）
        if (lightMapMaterial != null && lightMapMaterial.ownsTexture && lightMapMaterial.tex > 0) {
            GL46C.glDeleteTextures(lightMapMaterial.tex);
            lightMapMaterial.tex = 0;
        }
        
        // 释放 MemoryUtil 分配的缓冲区
        if (boneMatricesBuffer != null) {
            MemoryUtil.memFree(boneMatricesBuffer);
            boneMatricesBuffer = null;
        }
        if (boneMatricesByteBuffer != null) {
            MemoryUtil.memFree(boneMatricesByteBuffer);
            boneMatricesByteBuffer = null;
        }
        if (morphWeightsBuffer != null) {
            MemoryUtil.memFree(morphWeightsBuffer);
            morphWeightsBuffer = null;
        }
        if (uvMorphWeightsBuffer != null) {
            MemoryUtil.memFree(uvMorphWeightsBuffer);
            uvMorphWeightsBuffer = null;
        }
        if (materialMorphResultsBuffer != null) {
            MemoryUtil.memFree(materialMorphResultsBuffer);
            materialMorphResultsBuffer = null;
        }
        if (materialMorphResultsByteBuffer != null) {
            MemoryUtil.memFree(materialMorphResultsByteBuffer);
            materialMorphResultsByteBuffer = null;
        }
        if (modelViewMatBuff != null) {
            MemoryUtil.memFree(modelViewMatBuff);
            modelViewMatBuff = null;
        }
        if (projMatBuff != null) {
            MemoryUtil.memFree(projMatBuff);
            projMatBuff = null;
        }
    }
    
    /** @deprecated 使用 {@link #dispose()} 替代 */
    @Deprecated
    public static void Delete(MMDModelGpuSkinning model) {
        if (model != null) model.dispose();
    }
    
    private static class Material {
        int tex = 0;
        @SuppressWarnings("unused")
        boolean hasAlpha = false;
        boolean ownsTexture = false;
    }
}
