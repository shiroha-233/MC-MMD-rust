package com.shiroha.skinlayers3d.renderer.model;

import com.shiroha.skinlayers3d.NativeFunc;
import com.shiroha.skinlayers3d.config.ConfigManager;
import com.shiroha.skinlayers3d.renderer.compat.IrisCompat;
import com.shiroha.skinlayers3d.renderer.core.EyeTrackingHelper;
import com.shiroha.skinlayers3d.renderer.core.IMMDModel;
import com.shiroha.skinlayers3d.renderer.core.RenderContext;
import com.shiroha.skinlayers3d.renderer.resource.MMDTextureManager;
import com.shiroha.skinlayers3d.renderer.shader.GpuSkinningShader;
import com.shiroha.skinlayers3d.renderer.shader.ToonShader;
import com.shiroha.skinlayers3d.renderer.shader.ToonConfig;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
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
 * 将蒙皮计算从 CPU 移到 GPU，大幅提升大面数模型性能
 */
public class MMDModelGpuSkinning implements IMMDModel {
    private static final Logger logger = LogManager.getLogger();
    private static NativeFunc nf;
    private static GpuSkinningShader gpuShader;
    private static ToonShader toonShader;
    private static final ToonConfig toonConfig = ToonConfig.getInstance();
    
    // 模型数据
    private long model;
    private String modelDir;
    private int vertexCount;
    private int boneCount;
    
    // OpenGL 资源
    private int vertexArrayObject;
    private int indexBufferObject;
    private int positionBufferObject;
    private int normalBufferObject;
    private int uv0BufferObject;
    private int boneIndicesBufferObject;
    private int boneWeightsBufferObject;
    
    // 缓冲区（allocateDirect 分配，由 GC 回收）
    @SuppressWarnings("unused") // 保留引用防止 GC 过早回收
    private ByteBuffer posBuffer;
    @SuppressWarnings("unused") // 保留引用防止 GC 过早回收
    private ByteBuffer norBuffer;
    @SuppressWarnings("unused") // 保留引用防止 GC 过早回收
    private ByteBuffer uv0Buffer;
    private FloatBuffer boneMatricesBuffer;
    private FloatBuffer modelViewMatBuff;
    private FloatBuffer projMatBuff;
    
    // 预分配的骨骼矩阵复制缓冲区（避免每帧 allocateDirect）
    private ByteBuffer boneMatricesByteBuffer;
    
    // Morph 数据
    private int vertexMorphCount = 0;
    private boolean morphDataUploaded = false;
    private FloatBuffer morphWeightsBuffer;
    
    private int indexElementSize;
    private int indexType;
    private Material[] mats;
    
    // 时间追踪
    private long lastUpdateTime = -1;
    private static final float MAX_DELTA_TIME = 0.05f;
    private static final float MIN_DELTA_TIME = 0.001f;
    
    private boolean initialized = false;
    
    private MMDModelGpuSkinning() {}
    
    /**
     * 创建 GPU 蒙皮模型
     */
    public static MMDModelGpuSkinning Create(String modelFilename, String modelDir, boolean isPMD, long layerCount) {
        if (nf == null) nf = NativeFunc.GetInst();
        
        // 初始化 GPU 蒙皮着色器
        if (gpuShader == null) {
            gpuShader = new GpuSkinningShader();
            if (!gpuShader.init()) {
                logger.error("GPU 蒙皮着色器初始化失败，回退到 CPU 蒙皮");
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
        
        // 初始化 GPU 蒙皮数据
        nf.InitGpuSkinningData(model);
        
        BufferUploader.reset();
        
        int vertexCount = (int) nf.GetVertexCount(model);
        int boneCount = nf.GetBoneCount(model);
        
        // 检查骨骼数量是否超过 GPU 蒙皮支持的最大值
        if (boneCount > GpuSkinningShader.MAX_BONES) {
            logger.warn("模型骨骼数量 ({}) 超过 GPU 蒙皮最大支持 ({})，部分骨骼可能无法正确渲染", 
                boneCount, GpuSkinningShader.MAX_BONES);
        }
        logger.info("GPU 蒙皮模型加载: {} 顶点, {} 骨骼", vertexCount, boneCount);
        
        // 创建 VAO 和 VBO
        int vao = GL46C.glGenVertexArrays();
        int indexVbo = GL46C.glGenBuffers();
        int posVbo = GL46C.glGenBuffers();
        int norVbo = GL46C.glGenBuffers();
        int uv0Vbo = GL46C.glGenBuffers();
        int boneIdxVbo = GL46C.glGenBuffers();
        int boneWgtVbo = GL46C.glGenBuffers();
        
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
        
        // 原始顶点位置（静态，用于 GPU 蒙皮输入）- 使用线程安全复制
        ByteBuffer posBuffer = ByteBuffer.allocateDirect(vertexCount * 12);
        posBuffer.order(ByteOrder.LITTLE_ENDIAN);
        int copiedPos = nf.CopyOriginalPositionsToBuffer(model, posBuffer, vertexCount);
        if (copiedPos == 0) {
            logger.warn("原始顶点位置数据复制失败");
        }
        GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, posVbo);
        GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, posBuffer, GL46C.GL_STATIC_DRAW);
        
        // 原始法线（静态）- 使用线程安全复制
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
        
        // 骨骼索引（静态，ivec4）- 使用线程安全复制
        ByteBuffer boneIndicesByteBuffer = ByteBuffer.allocateDirect(vertexCount * 16);
        boneIndicesByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        int copiedIdx = nf.CopyBoneIndicesToBuffer(model, boneIndicesByteBuffer, vertexCount);
        if (copiedIdx == 0) {
            logger.warn("骨骼索引数据复制失败");
        }
        IntBuffer boneIndicesBuffer = boneIndicesByteBuffer.asIntBuffer();
        GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, boneIdxVbo);
        GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, boneIndicesBuffer, GL46C.GL_STATIC_DRAW);
        
        // 骨骼权重（静态，vec4）- 使用线程安全复制
        ByteBuffer boneWeightsByteBuffer = ByteBuffer.allocateDirect(vertexCount * 16);
        boneWeightsByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        int copiedWgt = nf.CopyBoneWeightsToBuffer(model, boneWeightsByteBuffer, vertexCount);
        if (copiedWgt == 0) {
            logger.warn("骨骼权重数据复制失败");
        }
        FloatBuffer boneWeightsBuffer = boneWeightsByteBuffer.asFloatBuffer();
        GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, boneWgtVbo);
        GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, boneWeightsBuffer, GL46C.GL_STATIC_DRAW);
        
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
        
        // 骨骼矩阵缓冲区和预分配的复制缓冲区
        FloatBuffer boneMatricesBuffer = MemoryUtil.memAllocFloat(boneCount * 16);
        ByteBuffer boneMatricesByteBuffer = ByteBuffer.allocateDirect(boneCount * 64);
        boneMatricesByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        
        // 构建结果
        MMDModelGpuSkinning result = new MMDModelGpuSkinning();
        result.model = model;
        result.modelDir = modelDir;
        result.vertexCount = vertexCount;
        result.boneCount = boneCount;
        result.vertexArrayObject = vao;
        result.indexBufferObject = indexVbo;
        result.positionBufferObject = posVbo;
        result.normalBufferObject = norVbo;
        result.uv0BufferObject = uv0Vbo;
        result.boneIndicesBufferObject = boneIdxVbo;
        result.boneWeightsBufferObject = boneWgtVbo;
        result.posBuffer = posBuffer;
        result.norBuffer = norBuffer;
        result.uv0Buffer = uv0Buffer;
        result.boneMatricesBuffer = boneMatricesBuffer;
        result.boneMatricesByteBuffer = boneMatricesByteBuffer;
        result.indexElementSize = indexElementSize;
        result.indexType = indexType;
        result.mats = mats;
        result.modelViewMatBuff = MemoryUtil.memAllocFloat(16);
        result.projMatBuff = MemoryUtil.memAllocFloat(16);
        result.initialized = true;
        
        // 初始化 Morph 数据
        nf.InitGpuMorphData(model);
        int morphCount = (int) nf.GetVertexMorphCount(model);
        result.vertexMorphCount = morphCount;
        if (morphCount > 0) {
            result.morphWeightsBuffer = MemoryUtil.memAllocFloat(morphCount);
            logger.info("GPU Morph 初始化: {} 个顶点 Morph", morphCount);
        }
        
        // 启用自动眨眼
        nf.SetAutoBlinkEnabled(model, true);
        
        logger.info("GPU 蒸皮模型创建成功: {} 顶点, {} 骨骼", vertexCount, boneCount);
        return result;
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
        // 使用公共工具类计算头部角度
        float headAngleX = Mth.clamp(entityIn.getXRot(), -50.0f, 50.0f);
        float headAngleY = (entityYaw - Mth.lerp(tickDelta, entityIn.yHeadRotO, entityIn.yHeadRot)) % 360.0f;
        if (headAngleY < -180.0f) headAngleY += 360.0f;
        else if (headAngleY > 180.0f) headAngleY -= 360.0f;
        headAngleY = Mth.clamp(headAngleY, -80.0f, 80.0f);
        
        float pitchRad = headAngleX * ((float) Math.PI / 180F);
        float yawRad = context.isInventoryScene() ? -headAngleY * ((float) Math.PI / 180F) : headAngleY * ((float) Math.PI / 180F);
        nf.SetHeadAngle(model, pitchRad, yawRad, 0.0f, context.isWorldScene());
        
        // 使用公共工具类更新眼球追踪
        EyeTrackingHelper.updateEyeTracking(nf, model, entityIn, entityYaw, tickDelta);
        
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
        deltaTime = Mth.clamp(deltaTime, MIN_DELTA_TIME, MAX_DELTA_TIME);
        
        // 只更新动画，不执行 CPU 蒙皮
        nf.UpdateAnimationOnly(model, deltaTime);
    }
    
    private void RenderModel(Entity entityIn, float entityYaw, float entityPitch, Vector3f entityTrans, PoseStack deliverStack) {
        Minecraft MCinstance = Minecraft.getInstance();
        
        // Iris 兼容：开始 GPU 蒙皮渲染
        // 如果 Iris 激活，会绑定 Iris 的 G-buffer framebuffer 并设置正确的渲染阶段
        IrisCompat.beginGpuSkinningWithIris();
        
        try {
            renderModelInternal(entityIn, entityYaw, entityPitch, entityTrans, deliverStack, MCinstance);
        } finally {
            // Iris 兼容：结束 GPU 蒙皮渲染
            // 恢复 framebuffer、着色器程序、SSBO 绑定等状态
            IrisCompat.endGpuSkinningWithIris();
        }
    }
    
    private void renderModelInternal(Entity entityIn, float entityYaw, float entityPitch, Vector3f entityTrans, PoseStack deliverStack, Minecraft MCinstance) {
        // 光照计算
        MCinstance.level.updateSkyBrightness();
        int blockLight = entityIn.level().getBrightness(LightLayer.BLOCK, entityIn.blockPosition());
        int skyLight = entityIn.level().getBrightness(LightLayer.SKY, entityIn.blockPosition());
        float skyDarken = MCinstance.level.getSkyDarken();
        
        float blockLightFactor = blockLight / 15.0f;
        float skyLightFactor = (skyLight / 15.0f) * ((15.0f - skyDarken) / 15.0f);
        float lightIntensity = Math.max(blockLightFactor, skyLightFactor);
        lightIntensity = 0.1f + lightIntensity * 0.9f;
        
        // 变换
        deliverStack.mulPose(new Quaternionf().rotateY(-entityYaw * ((float) Math.PI / 180F)));
        deliverStack.mulPose(new Quaternionf().rotateX(entityPitch * ((float) Math.PI / 180F)));
        deliverStack.translate(entityTrans.x, entityTrans.y, entityTrans.z);
        deliverStack.scale(0.09f, 0.09f, 0.09f);
        
        // 检查是否启用 Toon 渲染（ToonConfig 直接代理 ConfigManager，无需手动同步）
        boolean useToon = ConfigManager.isToonRenderingEnabled();
        if (useToon) {
            // 初始化 Toon 着色器（懒加载）
            if (toonShader == null) {
                toonShader = new ToonShader();
                if (!toonShader.init()) {
                    logger.warn("Toon 着色器初始化失败，回退到普通着色");
                    useToon = false;
                }
            }
        }
        
        // 上传骨骼矩阵到 GPU
        uploadBoneMatrices();
        
        BufferUploader.reset();
        GL46C.glBindVertexArray(vertexArrayObject);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.blendEquation(GL46C.GL_FUNC_ADD);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        
        // 设置矩阵（两种模式共用）
        modelViewMatBuff.clear();
        projMatBuff.clear();
        deliverStack.last().pose().get(modelViewMatBuff);
        RenderSystem.getProjectionMatrix().get(projMatBuff);
        
        GL46C.glBindBuffer(GL46C.GL_ELEMENT_ARRAY_BUFFER, indexBufferObject);
        
        if (useToon && toonShader != null && toonShader.isInitialized()) {
            // ==================== Toon 渲染模式 ====================
            renderToon(MCinstance, lightIntensity);
        } else {
            // ==================== 普通渲染模式 ====================
            renderNormal(MCinstance, lightIntensity);
        }
        
        // === 清理顶点属性 ===
        cleanupVertexAttributes();
        
        // 注意：主要的状态恢复由 IrisCompat.endGpuSkinningRendering() 处理
        // 这里只做基本的解绑，避免重复操作
    }
    
    /**
     * 清理所有启用的顶点属性数组
     */
    private void cleanupVertexAttributes() {
        // 普通着色器属性
        if (gpuShader != null) {
            int posLoc = gpuShader.getPositionLocation();
            int norLoc = gpuShader.getNormalLocation();
            int uvLoc = gpuShader.getUv0Location();
            int boneIdxLoc = gpuShader.getBoneIndicesLocation();
            int boneWgtLoc = gpuShader.getBoneWeightsLocation();
            
            if (posLoc != -1) GL46C.glDisableVertexAttribArray(posLoc);
            if (norLoc != -1) GL46C.glDisableVertexAttribArray(norLoc);
            if (uvLoc != -1) GL46C.glDisableVertexAttribArray(uvLoc);
            if (boneIdxLoc != -1) GL46C.glDisableVertexAttribArray(boneIdxLoc);
            if (boneWgtLoc != -1) GL46C.glDisableVertexAttribArray(boneWgtLoc);
        }
        
        // Toon 着色器属性
        if (toonShader != null && toonShader.isInitialized()) {
            // 主着色器
            int posLoc = toonShader.getPositionLocation();
            int norLoc = toonShader.getNormalLocation();
            int uvLoc = toonShader.getUv0Location();
            int boneIdxLoc = toonShader.getBoneIndicesLocation();
            int boneWgtLoc = toonShader.getBoneWeightsLocation();
            
            if (posLoc != -1) GL46C.glDisableVertexAttribArray(posLoc);
            if (norLoc != -1) GL46C.glDisableVertexAttribArray(norLoc);
            if (uvLoc != -1) GL46C.glDisableVertexAttribArray(uvLoc);
            if (boneIdxLoc != -1) GL46C.glDisableVertexAttribArray(boneIdxLoc);
            if (boneWgtLoc != -1) GL46C.glDisableVertexAttribArray(boneWgtLoc);
            
            // 描边着色器
            int outPosLoc = toonShader.getOutlinePositionLocation();
            int outNorLoc = toonShader.getOutlineNormalLocation();
            int outBoneIdxLoc = toonShader.getOutlineBoneIndicesLocation();
            int outBoneWgtLoc = toonShader.getOutlineBoneWeightsLocation();
            
            if (outPosLoc != -1) GL46C.glDisableVertexAttribArray(outPosLoc);
            if (outNorLoc != -1) GL46C.glDisableVertexAttribArray(outNorLoc);
            if (outBoneIdxLoc != -1) GL46C.glDisableVertexAttribArray(outBoneIdxLoc);
            if (outBoneWgtLoc != -1) GL46C.glDisableVertexAttribArray(outBoneWgtLoc);
        }
    }
    
    /**
     * 普通渲染模式（GPU 蒙皮）
     */
    private void renderNormal(Minecraft MCinstance, float lightIntensity) {
        gpuShader.use();
        
        int posLoc = gpuShader.getPositionLocation();
        int norLoc = gpuShader.getNormalLocation();
        int uvLoc = gpuShader.getUv0Location();
        int boneIdxLoc = gpuShader.getBoneIndicesLocation();
        int boneWgtLoc = gpuShader.getBoneWeightsLocation();
        
        setupVertexAttributes(posLoc, norLoc, uvLoc, boneIdxLoc, boneWgtLoc);
        
        gpuShader.setModelViewMatrix(modelViewMatBuff);
        gpuShader.setProjectionMatrix(projMatBuff);
        gpuShader.setSampler0(0);
        gpuShader.setLightIntensity(lightIntensity);
        gpuShader.uploadBoneMatrices(boneMatricesBuffer, boneCount);
        
        // Morph 支持
        if (vertexMorphCount > 0) {
            uploadMorphData();
            gpuShader.bindMorphSSBOs();
            gpuShader.setMorphParams(vertexMorphCount, vertexCount);
        } else {
            gpuShader.setMorphParams(0, 0);
        }
        
        drawAllSubMeshes(MCinstance);
    }
    
    /**
     * Toon 渲染模式（3渲2/卡通着色）
     * 两遍渲染：1. 描边（背面扩张）2. 主体（卡通着色）
     */
    private void renderToon(Minecraft MCinstance, float lightIntensity) {
        // ===== 第一遍：描边 =====
        if (toonConfig.isOutlineEnabled()) {
            toonShader.useOutline();
            toonShader.uploadBoneMatrices(boneMatricesBuffer, boneCount);
            
            int posLoc = toonShader.getOutlinePositionLocation();
            int norLoc = toonShader.getOutlineNormalLocation();
            int boneIdxLoc = toonShader.getOutlineBoneIndicesLocation();
            int boneWgtLoc = toonShader.getOutlineBoneWeightsLocation();
            
            // 设置顶点属性（描边不需要 UV）
            if (posLoc != -1) {
                GL46C.glEnableVertexAttribArray(posLoc);
                GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, positionBufferObject);
                GL46C.glVertexAttribPointer(posLoc, 3, GL46C.GL_FLOAT, false, 0, 0);
            }
            if (norLoc != -1) {
                GL46C.glEnableVertexAttribArray(norLoc);
                GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, normalBufferObject);
                GL46C.glVertexAttribPointer(norLoc, 3, GL46C.GL_FLOAT, false, 0, 0);
            }
            if (boneIdxLoc != -1) {
                GL46C.glEnableVertexAttribArray(boneIdxLoc);
                GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, boneIndicesBufferObject);
                GL46C.glVertexAttribIPointer(boneIdxLoc, 4, GL46C.GL_INT, 0, 0);
            }
            if (boneWgtLoc != -1) {
                GL46C.glEnableVertexAttribArray(boneWgtLoc);
                GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, boneWeightsBufferObject);
                GL46C.glVertexAttribPointer(boneWgtLoc, 4, GL46C.GL_FLOAT, false, 0, 0);
            }
            
            toonShader.setOutlineProjectionMatrix(projMatBuff);
            toonShader.setOutlineModelViewMatrix(modelViewMatBuff);
            toonShader.setOutlineWidth(toonConfig.getOutlineWidth());
            toonShader.setOutlineColor(
                toonConfig.getOutlineColorR(),
                toonConfig.getOutlineColorG(),
                toonConfig.getOutlineColorB()
            );
            
            // 正面剔除，只绘制背面（扩张后的背面形成描边）
            GL46C.glCullFace(GL46C.GL_FRONT);
            RenderSystem.enableCull();
            
            // 绘制所有子网格
            long subMeshCount = nf.GetSubMeshCount(model);
            for (long i = 0; i < subMeshCount; ++i) {
                int materialID = nf.GetSubMeshMaterialID(model, i);
                if (!nf.IsMaterialVisible(model, materialID)) continue;
                if (nf.GetMaterialAlpha(model, materialID) == 0.0f) continue;
                
                long startPos = (long) nf.GetSubMeshBeginIndex(model, i) * indexElementSize;
                int count = nf.GetSubMeshVertexCount(model, i);
                GL46C.glDrawElements(GL46C.GL_TRIANGLES, count, indexType, startPos);
            }
            
            // 恢复背面剔除
            GL46C.glCullFace(GL46C.GL_BACK);
        }
        
        // ===== 第二遍：主体（Toon 着色） =====
        toonShader.useMain();
        toonShader.uploadBoneMatrices(boneMatricesBuffer, boneCount);
        
        // Morph 支持
        // 注意：SSBO 绑定点是全局的，gpuShader 的 SSBO 绑定在 toonShader 中也有效
        if (vertexMorphCount > 0) {
            uploadMorphData();
            gpuShader.bindMorphSSBOs(); // 绑定点 1 和 2，ToonShader 共用
            toonShader.setMorphParams(vertexMorphCount, vertexCount);
        } else {
            toonShader.setMorphParams(0, 0);
        }
        
        int posLoc = toonShader.getPositionLocation();
        int norLoc = toonShader.getNormalLocation();
        int uvLoc = toonShader.getUv0Location();
        int boneIdxLoc = toonShader.getBoneIndicesLocation();
        int boneWgtLoc = toonShader.getBoneWeightsLocation();
        
        setupVertexAttributes(posLoc, norLoc, uvLoc, boneIdxLoc, boneWgtLoc);
        
        toonShader.setProjectionMatrix(projMatBuff);
        toonShader.setModelViewMatrix(modelViewMatBuff);
        toonShader.setSampler0(0);
        toonShader.setLightIntensity(lightIntensity);
        toonShader.setToonLevels(toonConfig.getToonLevels());
        toonShader.setRimLight(toonConfig.getRimPower(), toonConfig.getRimIntensity());
        toonShader.setShadowColor(
            toonConfig.getShadowColorR(),
            toonConfig.getShadowColorG(),
            toonConfig.getShadowColorB()
        );
        toonShader.setSpecular(toonConfig.getSpecularPower(), toonConfig.getSpecularIntensity());
        
        drawAllSubMeshes(MCinstance);
    }
    
    /**
     * 设置顶点属性
     */
    private void setupVertexAttributes(int posLoc, int norLoc, int uvLoc, int boneIdxLoc, int boneWgtLoc) {
        if (posLoc != -1) {
            GL46C.glEnableVertexAttribArray(posLoc);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, positionBufferObject);
            GL46C.glVertexAttribPointer(posLoc, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (norLoc != -1) {
            GL46C.glEnableVertexAttribArray(norLoc);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, normalBufferObject);
            GL46C.glVertexAttribPointer(norLoc, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (uvLoc != -1) {
            GL46C.glEnableVertexAttribArray(uvLoc);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, uv0BufferObject);
            GL46C.glVertexAttribPointer(uvLoc, 2, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (boneIdxLoc != -1) {
            GL46C.glEnableVertexAttribArray(boneIdxLoc);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, boneIndicesBufferObject);
            GL46C.glVertexAttribIPointer(boneIdxLoc, 4, GL46C.GL_INT, 0, 0);
        }
        if (boneWgtLoc != -1) {
            GL46C.glEnableVertexAttribArray(boneWgtLoc);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, boneWeightsBufferObject);
            GL46C.glVertexAttribPointer(boneWgtLoc, 4, GL46C.GL_FLOAT, false, 0, 0);
        }
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
            if (alpha == 0.0f) continue;
            
            if (nf.GetMaterialBothFace(model, materialID)) {
                RenderSystem.disableCull();
            } else {
                RenderSystem.enableCull();
            }
            
            if (mats[materialID].tex == 0) {
                MCinstance.getEntityRenderDispatcher().textureManager.bindForSetup(TextureManager.INTENTIONAL_MISSING_TEXTURE);
            } else {
                GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, mats[materialID].tex);
            }
            
            long startPos = (long) nf.GetSubMeshBeginIndex(model, i) * indexElementSize;
            int count = nf.GetSubMeshVertexCount(model, i);
            
            RenderSystem.assertOnRenderThread();
            GL46C.glDrawElements(GL46C.GL_TRIANGLES, count, indexType, startPos);
        }
    }
    
    private void uploadBoneMatrices() {
        // 使用预分配的缓冲区（优化：避免每帧 allocateDirect）
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
        
        gpuShader.uploadBoneMatrices(boneMatricesBuffer, copiedBones);
    }
    
    /**
     * 上传 Morph 数据到 GPU
     */
    private void uploadMorphData() {
        if (vertexMorphCount <= 0) return;
        
        // 首次上传偏移数据（静态）
        if (!morphDataUploaded) {
            long offsetsSize = nf.GetGpuMorphOffsetsSize(model);
            if (offsetsSize > 0) {
                ByteBuffer offsetsBuffer = ByteBuffer.allocateDirect((int) offsetsSize);
                offsetsBuffer.order(ByteOrder.LITTLE_ENDIAN);
                nf.CopyGpuMorphOffsetsToBuffer(model, offsetsBuffer);
                gpuShader.uploadMorphOffsets(offsetsBuffer, vertexMorphCount, vertexCount);
                morphDataUploaded = true;
            }
        }
        
        // 每帧更新权重
        if (morphWeightsBuffer != null) {
            ByteBuffer weightsByteBuffer = ByteBuffer.allocateDirect(vertexMorphCount * 4);
            weightsByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            nf.CopyGpuMorphWeightsToBuffer(model, weightsByteBuffer);
            morphWeightsBuffer.clear();
            morphWeightsBuffer.put(weightsByteBuffer.asFloatBuffer());
            morphWeightsBuffer.flip();
            gpuShader.updateMorphWeights(morphWeightsBuffer);
        }
    }
    
    @Override
    public void ChangeAnim(long anim, long layer) {
        nf.ChangeModelAnim(model, anim, layer);
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
    public void dispose() {
        nf.DeleteModel(model);
        
        // 释放 OpenGL 资源
        GL46C.glDeleteVertexArrays(vertexArrayObject);
        GL46C.glDeleteBuffers(indexBufferObject);
        GL46C.glDeleteBuffers(positionBufferObject);
        GL46C.glDeleteBuffers(normalBufferObject);
        GL46C.glDeleteBuffers(uv0BufferObject);
        GL46C.glDeleteBuffers(boneIndicesBufferObject);
        GL46C.glDeleteBuffers(boneWeightsBufferObject);
        
        // 释放 MemoryUtil 分配的缓冲区
        if (boneMatricesBuffer != null) MemoryUtil.memFree(boneMatricesBuffer);
        if (modelViewMatBuff != null) MemoryUtil.memFree(modelViewMatBuff);
        if (projMatBuff != null) MemoryUtil.memFree(projMatBuff);
        
        // 注意：posBuffer, norBuffer, uv0Buffer, boneMatricesByteBuffer 是通过
        // ByteBuffer.allocateDirect() 分配的，会由 GC 自动回收
        // boneIndicesBuffer, boneWeightsBuffer 是 ByteBuffer 的视图，不需要单独释放
    }
    
    /** @deprecated 使用 {@link #dispose()} 替代 */
    @Deprecated
    public static void Delete(MMDModelGpuSkinning model) {
        if (model != null) model.dispose();
    }
    
    private static class Material {
        int tex = 0;
        @SuppressWarnings("unused") // 预留用于透明度渲染
        boolean hasAlpha = false;
    }
}
