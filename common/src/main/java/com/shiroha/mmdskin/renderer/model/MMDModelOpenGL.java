package com.shiroha.mmdskin.renderer.model;

import com.shiroha.mmdskin.MmdSkinClient;
import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.renderer.camera.MMDCameraController;
import com.shiroha.mmdskin.renderer.core.EyeTrackingHelper;
import com.shiroha.mmdskin.renderer.core.IMMDModel;
import com.shiroha.mmdskin.renderer.core.IrisCompat;
import com.shiroha.mmdskin.renderer.core.RenderContext;
import com.shiroha.mmdskin.renderer.resource.MMDTextureManager;
import com.shiroha.mmdskin.renderer.shader.ShaderProvider;
import com.shiroha.mmdskin.renderer.shader.ToonShaderCpu;
import com.shiroha.mmdskin.renderer.shader.ToonConfig;
import com.shiroha.mmdskin.NativeFunc;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
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

/**
 * MMD模型OpenGL渲染实现
 * 负责MMD模型的OpenGL渲染逻辑
 */
public class MMDModelOpenGL implements IMMDModel {
    static final Logger logger = LogManager.getLogger();
    static NativeFunc nf;
    static boolean isShaderInited = false;
    static int MMDShaderProgram;
    public static boolean isMMDShaderEnabled = false;
    private static ToonShaderCpu toonShaderCpu;
    private static final ToonConfig toonConfig = ToonConfig.getInstance();
    int shaderProgram;

    int positionLocation;
    int normalLocation;
    int uv0Location, uv1Location, uv2Location;
    int colorLocation;
    int projMatLocation;
    int modelViewLocation;
    int sampler0Location, sampler1Location, sampler2Location;
    int light0Location, light1Location;

    int K_positionLocation;
    int K_normalLocation;
    int K_uv0Location, K_uv2Location;
    int K_projMatLocation;
    int K_modelViewLocation;
    int K_sampler0Location, K_sampler2Location;
    int KAIMyLocationV;
    int KAIMyLocationF;

    int I_positionLocation;
    int I_normalLocation;
    int I_uv0Location, I_uv2Location;
    int I_colorLocation;

    long model;
    String modelDir;
    private String cachedModelName;
    int vertexCount;
    ByteBuffer posBuffer, colorBuffer, norBuffer, uv0Buffer, uv1Buffer, uv2Buffer;
    int vertexArrayObject;
    int indexBufferObject;
    int vertexBufferObject;
    int colorBufferObject;
    int normalBufferObject;
    int texcoordBufferObject;
    int uv1BufferObject;
    int uv2BufferObject;
    int indexElementSize;
    int indexType;
    Material[] mats;
    Material lightMapMaterial;
    final Vector3f light0Direction = new Vector3f();
    final Vector3f light1Direction = new Vector3f();
    private final Quaternionf tempQuat = new Quaternionf();
    
    // 时间追踪（用于计算 deltaTime）
    private long lastUpdateTime = -1; // -1 表示未初始化
    private static final float MAX_DELTA_TIME = 0.25f; // 最大 250ms（4FPS），防止暂停后跳跃
    
    private FloatBuffer modelViewMatBuff;          // 预分配的矩阵缓冲区
    private FloatBuffer projMatBuff;
    private FloatBuffer light0Buff;                  // 预分配的光照缓冲区
    private FloatBuffer light1Buff;
    
    // 材质 Morph 结果
    private FloatBuffer materialMorphResultsBuffer;
    private ByteBuffer materialMorphResultsByteBuffer;
    private int materialMorphResultCount = 0;

    MMDModelOpenGL() {
        // 不在这里初始化时间，等第一次 Update 时初始化
    }

    public static void InitShader() {
        //Init Shader
        ShaderProvider.Init();
        if (ShaderProvider.isReady()) {
            MMDShaderProgram = ShaderProvider.getProgram();
        } else {
            logger.warn("MMD Shader 初始化失败，已自动禁用自定义着色器");
            MMDShaderProgram = 0;
            isMMDShaderEnabled = false;
        }
        isShaderInited = true;
    }

    public static MMDModelOpenGL Create(String modelFilename, String modelDir, boolean isPMD, long layerCount) {
        if (!isShaderInited && isMMDShaderEnabled)
            InitShader();
        if (nf == null) nf = NativeFunc.GetInst();
        long model;
        if (isPMD)
            model = nf.LoadModelPMD(modelFilename, modelDir, layerCount);
        else
            model = nf.LoadModelPMX(modelFilename, modelDir, layerCount);
        if (model == 0) {
            logger.info(String.format("Cannot open model: '%s'.", modelFilename));
            return null;
        }
        BufferUploader.reset();
        //Model exists,now we prepare data for OpenGL
        int vertexArrayObject = GL46C.glGenVertexArrays();
        int indexBufferObject = GL46C.glGenBuffers();
        int positionBufferObject = GL46C.glGenBuffers();
        int colorBufferObject = GL46C.glGenBuffers();
        int normalBufferObject = GL46C.glGenBuffers();
        int uv0BufferObject = GL46C.glGenBuffers();
        int uv1BufferObject = GL46C.glGenBuffers();
        int uv2BufferObject = GL46C.glGenBuffers();

        int vertexCount = (int) nf.GetVertexCount(model);
        ByteBuffer posBuffer = ByteBuffer.allocateDirect(vertexCount * 12); //float * 3
        ByteBuffer colorBuffer = ByteBuffer.allocateDirect(vertexCount * 16); //float * 4
        ByteBuffer norBuffer = ByteBuffer.allocateDirect(vertexCount * 12); //float * 3
        ByteBuffer uv0Buffer = ByteBuffer.allocateDirect(vertexCount * 8); //float * 2
        ByteBuffer uv1Buffer = ByteBuffer.allocateDirect(vertexCount * 8); //int * 2
        ByteBuffer uv2Buffer = ByteBuffer.allocateDirect(vertexCount * 8); //int * 2
        colorBuffer.order(ByteOrder.LITTLE_ENDIAN);
        uv1Buffer.order(ByteOrder.LITTLE_ENDIAN);
        uv2Buffer.order(ByteOrder.LITTLE_ENDIAN);

        GL46C.glBindVertexArray(vertexArrayObject);
        //Init indexBufferObject
        int indexElementSize = (int) nf.GetIndexElementSize(model);
        int indexCount = (int) nf.GetIndexCount(model);
        int indexSize = indexCount * indexElementSize;
        long indexData = nf.GetIndices(model);
        ByteBuffer indexBuffer = ByteBuffer.allocateDirect(indexSize);
        for (int i = 0; i < indexSize; ++i)
            indexBuffer.put(nf.ReadByte(indexData, i));
        indexBuffer.position(0);
        GL46C.glBindBuffer(GL46C.GL_ELEMENT_ARRAY_BUFFER, indexBufferObject);
        GL46C.glBufferData(GL46C.GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL46C.GL_STATIC_DRAW);

        int indexType = switch (indexElementSize) {
            case 1 -> GL46C.GL_UNSIGNED_BYTE;
            case 2 -> GL46C.GL_UNSIGNED_SHORT;
            case 4 -> GL46C.GL_UNSIGNED_INT;
            default -> 0;
        };

        //Material
        MMDModelOpenGL.Material[] mats = new MMDModelOpenGL.Material[(int) nf.GetMaterialCount(model)];
        for (int i = 0; i < mats.length; ++i) {
            mats[i] = new MMDModelOpenGL.Material();
            String texFilename = nf.GetMaterialTex(model, i);
            if (!texFilename.isEmpty()) {
                MMDTextureManager.Texture mgrTex = MMDTextureManager.GetTexture(texFilename);
                if (mgrTex != null) {
                    mats[i].tex = mgrTex.tex;
                    mats[i].hasAlpha = mgrTex.hasAlpha;
                }
            }
        }

        //lightMap
        MMDModelOpenGL.Material lightMapMaterial = new MMDModelOpenGL.Material();
        MMDTextureManager.Texture mgrTex = MMDTextureManager.GetTexture(modelDir + "/lightMap.png");
        if (mgrTex != null) {
            lightMapMaterial.tex = mgrTex.tex;
            lightMapMaterial.hasAlpha = mgrTex.hasAlpha;
        }else{
            lightMapMaterial.tex = GL46C.glGenTextures();
            lightMapMaterial.ownsTexture = true;
            GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, lightMapMaterial.tex);
            ByteBuffer texBuffer = ByteBuffer.allocateDirect(16*16*4);
            texBuffer.order(ByteOrder.LITTLE_ENDIAN);
            for(int i=0;i<16*16;i++){
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

        for(int i=0; i<vertexCount; i++){
            colorBuffer.putFloat(1.0f);
            colorBuffer.putFloat(1.0f);
            colorBuffer.putFloat(1.0f);
            colorBuffer.putFloat(1.0f);
        }
        colorBuffer.flip();

        for(int i=0; i<vertexCount; i++){
            uv1Buffer.putInt(15);
            uv1Buffer.putInt(15);
        }
        uv1Buffer.flip();

        MMDModelOpenGL result = new MMDModelOpenGL();
        result.model = model;
        result.modelDir = modelDir;
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
        
        // 预分配矩阵缓冲区（避免每帧分配）
        result.modelViewMatBuff = MemoryUtil.memAllocFloat(16);
        result.projMatBuff = MemoryUtil.memAllocFloat(16);
        result.light0Buff = MemoryUtil.memAllocFloat(3);
        result.light1Buff = MemoryUtil.memAllocFloat(3);
        
        // 初始化材质 Morph 结果缓冲区
        int matMorphCount = nf.GetMaterialMorphResultCount(model);
        if (matMorphCount > 0) {
            int floatCount = matMorphCount * 28;
            result.materialMorphResultCount = matMorphCount;
            result.materialMorphResultsBuffer = MemoryUtil.memAllocFloat(floatCount);
            result.materialMorphResultsByteBuffer = MemoryUtil.memAlloc(floatCount * 4);
            result.materialMorphResultsByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        }
        
        // 启用自动眨眼
        nf.SetAutoBlinkEnabled(model, true);
        
        return result;
    }

    @Override
    public void dispose() {
        nf.DeleteModel(model);
        
        // 释放预分配的矩阵缓冲区
        if (modelViewMatBuff != null) {
            MemoryUtil.memFree(modelViewMatBuff);
            modelViewMatBuff = null;
        }
        if (projMatBuff != null) {
            MemoryUtil.memFree(projMatBuff);
            projMatBuff = null;
        }
        if (light0Buff != null) {
            MemoryUtil.memFree(light0Buff);
            light0Buff = null;
        }
        if (light1Buff != null) {
            MemoryUtil.memFree(light1Buff);
            light1Buff = null;
        }
        if (materialMorphResultsBuffer != null) {
            MemoryUtil.memFree(materialMorphResultsBuffer);
            materialMorphResultsBuffer = null;
        }
        if (materialMorphResultsByteBuffer != null) {
            MemoryUtil.memFree(materialMorphResultsByteBuffer);
            materialMorphResultsByteBuffer = null;
        }
        
        // 释放自建的 lightMap 纹理（来自 MMDTextureManager 的不在此删除）
        if (lightMapMaterial != null && lightMapMaterial.ownsTexture && lightMapMaterial.tex > 0) {
            GL46C.glDeleteTextures(lightMapMaterial.tex);
        }
        
        // 删除 OpenGL 资源
        GL46C.glDeleteVertexArrays(vertexArrayObject);
        GL46C.glDeleteBuffers(indexBufferObject);
        GL46C.glDeleteBuffers(vertexBufferObject);
        GL46C.glDeleteBuffers(colorBufferObject);
        GL46C.glDeleteBuffers(normalBufferObject);
        GL46C.glDeleteBuffers(texcoordBufferObject);
        GL46C.glDeleteBuffers(uv1BufferObject);
        GL46C.glDeleteBuffers(uv2BufferObject);
    }
    
    @Override
    public void render(Entity entityIn, float entityYaw, float entityPitch, Vector3f entityTrans, float tickDelta, PoseStack mat, int packedLight, RenderContext context) {
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
            
            float pitchRad = headAngleX * ((float)Math.PI / 180F);
            float yawRad = context.isInventoryScene() ? -headAngleY * ((float)Math.PI / 180F) : headAngleY * ((float)Math.PI / 180F);
            nf.SetHeadAngle(model, pitchRad, yawRad, 0.0f, context.isWorldScene());
        }
        
        // 使用公共工具类更新眼球追踪（传递模型名称，使用每模型独立配置）
        if (!stagePlaying) {
            EyeTrackingHelper.updateEyeTracking(nf, model, entityIn, entityYaw, tickDelta, getModelName());
        }
        
        // 传递实体位置和朝向给物理系统（用于人物移动时的惯性效果）
        final float MODEL_SCALE = 0.09f;
        float posX = (float)(Mth.lerp(tickDelta, entityIn.xo, entityIn.getX()) * MODEL_SCALE);
        float posY = (float)(Mth.lerp(tickDelta, entityIn.yo, entityIn.getY()) * MODEL_SCALE);
        float posZ = (float)(Mth.lerp(tickDelta, entityIn.zo, entityIn.getZ()) * MODEL_SCALE);
        float bodyYaw = Mth.lerp(tickDelta, entityIn.yBodyRotO, entityIn.yBodyRot) * ((float) Math.PI / 180F);
        nf.SetModelPositionAndYaw(model, posX, posY, posZ, bodyYaw);
        
        Update();
        RenderModel(entityIn, entityYaw, entityPitch, entityTrans, mat);
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
     */
    private float getMaterialMorphAlpha(int materialIndex) {
        if (materialMorphResultsBuffer == null || materialIndex >= materialMorphResultCount) return 1.0f;
        int offset = materialIndex * 28 + 3; // diffuse.w
        if (offset < materialMorphResultsBuffer.capacity()) {
            return materialMorphResultsBuffer.get(offset);
        }
        return 1.0f;
    }
    
    void Update() {
        // 计算真实的 deltaTime（秒）
        long currentTime = System.currentTimeMillis();
        
        // 第一次调用，初始化时间
        if (lastUpdateTime < 0) {
            lastUpdateTime = currentTime;
            return; // 第一帧不更新物理，避免异常大的 deltaTime
        }
        
        float deltaTime = (currentTime - lastUpdateTime) / 1000.0f;
        lastUpdateTime = currentTime;
        
        // 限制 deltaTime 上限，防止暂停后物理爆炸
        // 注意：不设下限，避免高帧率下动画加速
        if (deltaTime > MAX_DELTA_TIME) {
            deltaTime = MAX_DELTA_TIME;
        }
        
        nf.UpdateModel(model, deltaTime);
    }

    void RenderModel(Entity entityIn, float entityYaw, float entityPitch, Vector3f entityTrans, PoseStack deliverStack) {
        Minecraft MCinstance = Minecraft.getInstance();
        
        // 采样玩家位置的环境光照
        MCinstance.level.updateSkyBrightness();
        int eyeHeight = (int)(entityIn.getEyeY() - entityIn.getBlockY());
        int blockLight = entityIn.level().getBrightness(LightLayer.BLOCK, entityIn.blockPosition().above(eyeHeight));
        int skyLight = entityIn.level().getBrightness(LightLayer.SKY, entityIn.blockPosition().above(eyeHeight));
        float skyDarken = MCinstance.level.getSkyDarken();
        
        // 计算综合光照强度 (0.0 ~ 1.0)
        // 方块光照直接使用，天空光照需要考虑天空亮度衰减
        float blockLightFactor = blockLight / 15.0f;
        float skyLightFactor = (skyLight / 15.0f) * ((15.0f - skyDarken) / 15.0f);
        // 取两者中较亮的作为最终光照，模拟 Minecraft 的光照混合
        float lightIntensity = Math.max(blockLightFactor, skyLightFactor);
        
        // 设置最低亮度阈值，防止完全黑暗（0.1 = 10% 最低亮度）
        float minBrightness = 0.1f;
        lightIntensity = minBrightness + lightIntensity * (1.0f - minBrightness);
        
        light0Direction.set(1.0f, 0.75f, 0.0f).normalize();
        light1Direction.set(-1.0f, 0.75f, 0.0f).normalize();
        float yawRad = entityYaw * ((float)Math.PI / 180F);
        light0Direction.rotate(tempQuat.identity().rotateY(yawRad));
        light1Direction.rotate(tempQuat.identity().rotateY(yawRad));

        deliverStack.mulPose(tempQuat.identity().rotateY(-yawRad));
        deliverStack.mulPose(tempQuat.identity().rotateX(entityPitch*((float)Math.PI / 180F)));
        deliverStack.translate(entityTrans.x, entityTrans.y, entityTrans.z);
        float baseScale = 0.09f * com.shiroha.mmdskin.config.ModelConfigManager.getConfig(getModelName()).modelScale;
        deliverStack.scale(baseScale, baseScale, baseScale);
        
        // 获取材质 Morph 结果
        fetchMaterialMorphResults();
        
        // 检查是否启用 Toon 渲染
        boolean useToon = ConfigManager.isToonRenderingEnabled();
        if (useToon) {
            // 初始化 Toon 着色器（懒加载）
            if (toonShaderCpu == null) {
                toonShaderCpu = new ToonShaderCpu();
                if (!toonShaderCpu.init()) {
                    logger.warn("ToonShaderCpu 初始化失败，回退到普通着色");
                    useToon = false;
                }
            }
        }
        
        if (useToon && toonShaderCpu != null && toonShaderCpu.isInitialized()) {
            // Toon 渲染模式
            renderToon(MCinstance, lightIntensity, deliverStack);
            return;
        }
        
        // 普通渲染模式
        if(MmdSkinClient.usingMMDShader == 0){
            ShaderInstance mcShader = RenderSystem.getShader();
            if (mcShader == null) {
                logger.debug("RenderSystem.getShader() 返回 null，跳过本帧渲染");
                return;
            }
            shaderProgram = mcShader.getId();
            setUniforms(mcShader, deliverStack);
            mcShader.apply();
        }
        if(MmdSkinClient.usingMMDShader == 1){
            shaderProgram = MMDShaderProgram;
            GlStateManager._glUseProgram(shaderProgram);
        }
        
        updateLocation(shaderProgram);

        BufferUploader.reset();
        GL46C.glBindVertexArray(vertexArrayObject);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.blendEquation(GL46C.GL_FUNC_ADD);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        // === 上传顶点数据到 VBO（每个 VBO 只上传一次）===
        int posAndNorSize = vertexCount * 12; // float * 3
        long posData = nf.GetPoss(model);
        nf.CopyDataToByteBuffer(posBuffer, posData, posAndNorSize);
        GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, vertexBufferObject);
        GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, posBuffer, GL46C.GL_DYNAMIC_DRAW);

        long normalData = nf.GetNormals(model);
        nf.CopyDataToByteBuffer(norBuffer, normalData, posAndNorSize);
        GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, normalBufferObject);
        GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, norBuffer, GL46C.GL_DYNAMIC_DRAW);

        int uv0Size = vertexCount * 8; // float * 2
        long uv0Data = nf.GetUVs(model);
        nf.CopyDataToByteBuffer(uv0Buffer, uv0Data, uv0Size);
        GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, texcoordBufferObject);
        GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, uv0Buffer, GL46C.GL_DYNAMIC_DRAW);

        uv1Buffer.position(0);
        GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, uv1BufferObject);
        GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, uv1Buffer, GL46C.GL_STATIC_DRAW);

        // === UV2 和 Color：所有顶点值相同，使用常量顶点属性（避免逐顶点循环和缓冲区上传）===
        boolean irisActive = IrisCompat.isIrisShaderActive();
        int blockBrightness = 16 * blockLight;
        // Iris 兼容：UV2 不应包含 skyDarken，Iris 的光照管线会自行处理昼夜变化
        int skyBrightness = irisActive ? (16 * skyLight) : Math.round((15.0f - skyDarken) * (skyLight / 15.0f) * 16);
        if (uv2Location != -1) GL46C.glVertexAttribI4i(uv2Location, blockBrightness, skyBrightness, 0, 0);
        if (K_uv2Location != -1) GL46C.glVertexAttribI4i(K_uv2Location, blockBrightness, skyBrightness, 0, 0);
        if (I_uv2Location != -1) GL46C.glVertexAttribI4i(I_uv2Location, blockBrightness, skyBrightness, 0, 0);
        // Iris 兼容：Color 应为白色，让 Iris 的 G-buffer 管线完全控制光照；
        // 否则 lightIntensity 预乘后会与 Iris 自身光照叠加导致夜间过暗
        float colorFactor = irisActive ? 1.0f : lightIntensity;
        if (colorLocation != -1) GL46C.glVertexAttrib4f(colorLocation, colorFactor, colorFactor, colorFactor, 1.0f);
        if (I_colorLocation != -1) GL46C.glVertexAttrib4f(I_colorLocation, colorFactor, colorFactor, colorFactor, 1.0f);

        // === 绑定顶点属性（数据已在 VBO 中，只需设置指针）===
        if (positionLocation != -1) {
            GL46C.glEnableVertexAttribArray(positionLocation);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, vertexBufferObject);
            GL46C.glVertexAttribPointer(positionLocation, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (normalLocation != -1) {
            GL46C.glEnableVertexAttribArray(normalLocation);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, normalBufferObject);
            GL46C.glVertexAttribPointer(normalLocation, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (uv0Location != -1) {
            GL46C.glEnableVertexAttribArray(uv0Location);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, texcoordBufferObject);
            GL46C.glVertexAttribPointer(uv0Location, 2, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (uv1Location != -1) {
            GL46C.glEnableVertexAttribArray(uv1Location);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, uv1BufferObject);
            GL46C.glVertexAttribIPointer(uv1Location, 2, GL46C.GL_INT, 0, 0);
        }

        GL46C.glBindBuffer(GL46C.GL_ELEMENT_ARRAY_BUFFER, indexBufferObject);

        // 使用预分配的矩阵缓冲区（避免每帧分配）
        modelViewMatBuff.clear();
        projMatBuff.clear();
        deliverStack.last().pose().get(modelViewMatBuff);
        RenderSystem.getProjectionMatrix().get(projMatBuff);

        //upload Uniforms(MMDShader)
        if(MmdSkinClient.usingMMDShader == 1){
            RenderSystem.glUniformMatrix4(modelViewLocation, false, modelViewMatBuff);
            RenderSystem.glUniformMatrix4(projMatLocation, false, projMatBuff);

            if(light0Location != -1){
                light0Buff.clear();
                light0Buff.put(light0Direction.x);
                light0Buff.put(light0Direction.y);
                light0Buff.put(light0Direction.z);
                light0Buff.flip();
                RenderSystem.glUniform3(light0Location, light0Buff);
            }
            if(light1Location != -1){
                light1Buff.clear();
                light1Buff.put(light1Direction.x);
                light1Buff.put(light1Direction.y);
                light1Buff.put(light1Direction.z);
                light1Buff.flip();
                RenderSystem.glUniform3(light1Location, light1Buff);
            }
            if(sampler0Location != -1){
                GL46C.glUniform1i(sampler0Location, 0);
            }
            if(sampler1Location != -1){
                RenderSystem.activeTexture(GL46C.GL_TEXTURE1);
                RenderSystem.bindTexture(lightMapMaterial.tex);
                GL46C.glUniform1i(sampler1Location, 1);
            }
            if(sampler2Location != -1){
                RenderSystem.activeTexture(GL46C.GL_TEXTURE2);
                RenderSystem.bindTexture(lightMapMaterial.tex);
                GL46C.glUniform1i(sampler2Location, 2);
            }
        }

        // K_* 属性（自定义着色器属性）— 复用已上传的 VBO，无需重复 glBufferData
        if (K_positionLocation != -1) {
            GL46C.glEnableVertexAttribArray(K_positionLocation);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, vertexBufferObject);
            GL46C.glVertexAttribPointer(K_positionLocation, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (K_normalLocation != -1) {
            GL46C.glEnableVertexAttribArray(K_normalLocation);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, normalBufferObject);
            GL46C.glVertexAttribPointer(K_normalLocation, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (K_uv0Location != -1) {
            GL46C.glEnableVertexAttribArray(K_uv0Location);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, texcoordBufferObject);
            GL46C.glVertexAttribPointer(K_uv0Location, 2, GL46C.GL_FLOAT, false, 0, 0);
        }
        if(K_projMatLocation != -1){
            projMatBuff.position(0);
            RenderSystem.glUniformMatrix4(K_projMatLocation, false, projMatBuff);
        }
        if(K_modelViewLocation != -1){
            modelViewMatBuff.position(0);
            RenderSystem.glUniformMatrix4(K_modelViewLocation, false, modelViewMatBuff);
        }
        if(K_sampler0Location != -1){
            GL46C.glUniform1i(K_sampler0Location, 0);
        }
        if(K_sampler2Location != -1){
            RenderSystem.activeTexture(GL46C.GL_TEXTURE2);
            RenderSystem.bindTexture(lightMapMaterial.tex);
            GL46C.glUniform1i(K_sampler2Location, 2);
        }
        if(KAIMyLocationV != -1)
            GL46C.glUniform1i(KAIMyLocationV, 1);
        
        if(KAIMyLocationF != -1)
            GL46C.glUniform1i(KAIMyLocationF, 1);

        // Iris 属性 — 复用已上传的 VBO，无需重复 glBufferData
        if (I_positionLocation != -1) {
            GL46C.glEnableVertexAttribArray(I_positionLocation);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, vertexBufferObject);
            GL46C.glVertexAttribPointer(I_positionLocation, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (I_normalLocation != -1) {
            GL46C.glEnableVertexAttribArray(I_normalLocation);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, normalBufferObject);
            GL46C.glVertexAttribPointer(I_normalLocation, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (I_uv0Location != -1) {
            GL46C.glEnableVertexAttribArray(I_uv0Location);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, texcoordBufferObject);
            GL46C.glVertexAttribPointer(I_uv0Location, 2, GL46C.GL_FLOAT, false, 0, 0);
        }

        //Draw
        RenderSystem.activeTexture(GL46C.GL_TEXTURE0);
        long subMeshCount = nf.GetSubMeshCount(model);
        for (long i = 0; i < subMeshCount; ++i) {
            int materialID = nf.GetSubMeshMaterialID(model, i);
            
            // 检查材质可见性（用于脱外套等功能）
            if (!nf.IsMaterialVisible(model, materialID))
                continue;
            
            float alpha = nf.GetMaterialAlpha(model, materialID);
            float morphAlpha = getMaterialMorphAlpha(materialID);
            if (alpha * morphAlpha < 0.001f)
                continue;

            if (nf.GetMaterialBothFace(model, materialID)) {
                RenderSystem.disableCull();
            } else {
                RenderSystem.enableCull();
            }
            int texId;
            if (mats[materialID].tex == 0)
                texId = MCinstance.getTextureManager().getTexture(TextureManager.INTENTIONAL_MISSING_TEXTURE).getId();
            else
                texId = mats[materialID].tex;
            RenderSystem.setShaderTexture(0, texId);
            GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, texId);
            long startPos = (long) nf.GetSubMeshBeginIndex(model, i) * indexElementSize;
            int count = nf.GetSubMeshVertexCount(model, i);

            GL46C.glDrawElements(GL46C.GL_TRIANGLES, count, indexType, startPos);
        }

        if(KAIMyLocationV != -1)
            GL46C.glUniform1i(KAIMyLocationV, 0);
        if(KAIMyLocationF != -1)
            GL46C.glUniform1i(KAIMyLocationF, 0);

        // === 关键：恢复 OpenGL 状态，防止与 Iris 冲突 ===
        // 禁用所有启用的顶点属性数组
        if (positionLocation != -1) GL46C.glDisableVertexAttribArray(positionLocation);
        if (normalLocation != -1) GL46C.glDisableVertexAttribArray(normalLocation);
        if (uv0Location != -1) GL46C.glDisableVertexAttribArray(uv0Location);
        if (uv1Location != -1) GL46C.glDisableVertexAttribArray(uv1Location);
        if (uv2Location != -1) GL46C.glDisableVertexAttribArray(uv2Location);
        if (colorLocation != -1) GL46C.glDisableVertexAttribArray(colorLocation);
        if (K_positionLocation != -1) GL46C.glDisableVertexAttribArray(K_positionLocation);
        if (K_normalLocation != -1) GL46C.glDisableVertexAttribArray(K_normalLocation);
        if (K_uv0Location != -1) GL46C.glDisableVertexAttribArray(K_uv0Location);
        if (K_uv2Location != -1) GL46C.glDisableVertexAttribArray(K_uv2Location);
        if (I_positionLocation != -1) GL46C.glDisableVertexAttribArray(I_positionLocation);
        if (I_normalLocation != -1) GL46C.glDisableVertexAttribArray(I_normalLocation);
        if (I_uv0Location != -1) GL46C.glDisableVertexAttribArray(I_uv0Location);
        if (I_uv2Location != -1) GL46C.glDisableVertexAttribArray(I_uv2Location);
        if (I_colorLocation != -1) GL46C.glDisableVertexAttribArray(I_colorLocation);
        
        // 解绑缓冲区
        GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, 0);
        GL46C.glBindBuffer(GL46C.GL_ELEMENT_ARRAY_BUFFER, 0);
        
        // 解绑 VAO（重要：让 Minecraft/Iris 使用自己的 VAO）
        GL46C.glBindVertexArray(0);
        
        // 确保纹理单元恢复到 TEXTURE0
        RenderSystem.activeTexture(GL46C.GL_TEXTURE0);

        ShaderInstance currentShader = RenderSystem.getShader();
        if (currentShader != null) {
            currentShader.clear();
        }
        BufferUploader.reset();
    }

    /**
     * Toon 渲染模式（CPU 蒙皮版本）
     * 两遍渲染：1. 描边（背面扩张）2. 主体（卡通着色）
     * 
     * Iris 兼容：
     *   Iris 激活时，先通过 ExtendedShader.apply() 绑定 G-buffer FBO + MRT draw buffers，
     *   再切换到 Toon 着色器程序。Toon 片段着色器已声明 layout(location=0..3) 多输出，
     *   确保 Iris 的 draw buffers 全部被写入合理数据，避免透明。
     */
    private void renderToon(Minecraft MCinstance, float lightIntensity, PoseStack deliverStack) {
        BufferUploader.reset();
        GL46C.glBindVertexArray(vertexArrayObject);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.blendEquation(GL46C.GL_FUNC_ADD);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        
        // Iris 兼容：绑定 Iris G-buffer FBO（如果 Iris 光影激活）
        boolean irisActive = IrisCompat.isIrisShaderActive();
        if (irisActive) {
            ShaderInstance irisShader = RenderSystem.getShader();
            if (irisShader != null) {
                setUniforms(irisShader, deliverStack);
                irisShader.apply();  // 绑定 Iris G-buffer FBO + MRT draw buffers
            }
        }
        
        // 获取蒙皮后的顶点数据（由 Rust 引擎计算）
        int posAndNorSize = vertexCount * 12;
        long posData = nf.GetPoss(model);
        nf.CopyDataToByteBuffer(posBuffer, posData, posAndNorSize);
        long normalData = nf.GetNormals(model);
        nf.CopyDataToByteBuffer(norBuffer, normalData, posAndNorSize);
        int uv0Size = vertexCount * 8;
        long uv0Data = nf.GetUVs(model);
        nf.CopyDataToByteBuffer(uv0Buffer, uv0Data, uv0Size);
        
        // 设置矩阵
        modelViewMatBuff.clear();
        projMatBuff.clear();
        deliverStack.last().pose().get(modelViewMatBuff);
        RenderSystem.getProjectionMatrix().get(projMatBuff);
        
        GL46C.glBindBuffer(GL46C.GL_ELEMENT_ARRAY_BUFFER, indexBufferObject);
        
        // ===== 第一遍：描边 =====
        if (toonConfig.isOutlineEnabled()) {
            toonShaderCpu.useOutline();
            
            int posLoc = toonShaderCpu.getOutlinePositionLocation();
            int norLoc = toonShaderCpu.getOutlineNormalLocation();
            
            // 设置顶点属性
            if (posLoc != -1) {
                GL46C.glEnableVertexAttribArray(posLoc);
                GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, vertexBufferObject);
                GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, posBuffer, GL46C.GL_DYNAMIC_DRAW);
                GL46C.glVertexAttribPointer(posLoc, 3, GL46C.GL_FLOAT, false, 0, 0);
            }
            if (norLoc != -1) {
                GL46C.glEnableVertexAttribArray(norLoc);
                GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, normalBufferObject);
                GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, norBuffer, GL46C.GL_DYNAMIC_DRAW);
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
            
            // 正面剔除，只绘制背面（扩张后的背面形成描边）
            GL46C.glCullFace(GL46C.GL_FRONT);
            RenderSystem.enableCull();
            
            // 绘制所有子网格
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
        
        int posLoc = toonShaderCpu.getPositionLocation();
        int norLoc = toonShaderCpu.getNormalLocation();
        int uvLoc = toonShaderCpu.getUv0Location();
        
        // 设置顶点属性
        if (posLoc != -1) {
            GL46C.glEnableVertexAttribArray(posLoc);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, vertexBufferObject);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, posBuffer, GL46C.GL_DYNAMIC_DRAW);
            GL46C.glVertexAttribPointer(posLoc, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (norLoc != -1) {
            GL46C.glEnableVertexAttribArray(norLoc);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, normalBufferObject);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, norBuffer, GL46C.GL_DYNAMIC_DRAW);
            GL46C.glVertexAttribPointer(norLoc, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (uvLoc != -1) {
            GL46C.glEnableVertexAttribArray(uvLoc);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, texcoordBufferObject);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, uv0Buffer, GL46C.GL_DYNAMIC_DRAW);
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
        
        // 绘制所有子网格
        RenderSystem.activeTexture(GL46C.GL_TEXTURE0);
        long subMeshCount = nf.GetSubMeshCount(model);
        for (long i = 0; i < subMeshCount; ++i) {
            int materialID = nf.GetSubMeshMaterialID(model, i);
            if (!nf.IsMaterialVisible(model, materialID)) continue;
            
            float alpha = nf.GetMaterialAlpha(model, materialID);
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
        
        // 清理顶点属性
        if (posLoc != -1) GL46C.glDisableVertexAttribArray(posLoc);
        if (norLoc != -1) GL46C.glDisableVertexAttribArray(norLoc);
        if (uvLoc != -1) GL46C.glDisableVertexAttribArray(uvLoc);
        
        // 解绑缓冲区和 VAO
        GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, 0);
        GL46C.glBindBuffer(GL46C.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL46C.glBindVertexArray(0);
        
        // 恢复默认着色器
        GL46C.glUseProgram(0);
        RenderSystem.activeTexture(GL46C.GL_TEXTURE0);
        BufferUploader.reset();
    }

    static class Material {
        int tex;
        boolean hasAlpha;
        boolean ownsTexture;

        Material() {
            tex = 0;
            hasAlpha = false;
            ownsTexture = false;
        }
    }

    void updateLocation(int shaderProgram){
        positionLocation = GlStateManager._glGetAttribLocation(shaderProgram, "Position");
        normalLocation = GlStateManager._glGetAttribLocation(shaderProgram, "Normal");
        uv0Location = GlStateManager._glGetAttribLocation(shaderProgram, "UV0");
        uv1Location = GlStateManager._glGetAttribLocation(shaderProgram, "UV1");
        uv2Location = GlStateManager._glGetAttribLocation(shaderProgram, "UV2");
        colorLocation = GlStateManager._glGetAttribLocation(shaderProgram, "Color");
        projMatLocation = GlStateManager._glGetUniformLocation(shaderProgram, "ProjMat");
        modelViewLocation = GlStateManager._glGetUniformLocation(shaderProgram, "ModelViewMat");
        sampler0Location = GlStateManager._glGetUniformLocation(shaderProgram, "Sampler0");
        sampler1Location = GlStateManager._glGetUniformLocation(shaderProgram, "Sampler1");
        sampler2Location = GlStateManager._glGetUniformLocation(shaderProgram, "Sampler2");
        light0Location = GlStateManager._glGetUniformLocation(shaderProgram, "Light0_Direction");
        light1Location = GlStateManager._glGetUniformLocation(shaderProgram, "Light1_Direction");

        K_positionLocation = GlStateManager._glGetAttribLocation(shaderProgram, "K_Position");
        K_normalLocation = GlStateManager._glGetAttribLocation(shaderProgram, "K_Normal");
        K_uv0Location = GlStateManager._glGetAttribLocation(shaderProgram, "K_UV0");
        K_uv2Location = GlStateManager._glGetAttribLocation(shaderProgram, "K_UV2");
        K_projMatLocation = GlStateManager._glGetUniformLocation(shaderProgram, "K_ProjMat");
        K_modelViewLocation = GlStateManager._glGetUniformLocation(shaderProgram, "K_ModelViewMat");
        K_sampler0Location = GlStateManager._glGetUniformLocation(shaderProgram, "K_Sampler0");
        K_sampler2Location = GlStateManager._glGetUniformLocation(shaderProgram, "K_Sampler2");
        KAIMyLocationV = GlStateManager._glGetUniformLocation(shaderProgram, "MMDShaderV");
        KAIMyLocationF = GlStateManager._glGetUniformLocation(shaderProgram, "MMDShaderF");

        I_positionLocation = GlStateManager._glGetAttribLocation(shaderProgram, "iris_Position");
        I_normalLocation = GlStateManager._glGetAttribLocation(shaderProgram, "iris_Normal");
        I_uv0Location = GlStateManager._glGetAttribLocation(shaderProgram, "iris_UV0");
        I_uv2Location = GlStateManager._glGetAttribLocation(shaderProgram, "iris_UV2");
        I_colorLocation = GlStateManager._glGetAttribLocation(shaderProgram, "iris_Color");
    }

    public void setUniforms(ShaderInstance shader, PoseStack deliverStack){
        if(shader.MODEL_VIEW_MATRIX != null)
            shader.MODEL_VIEW_MATRIX.set(deliverStack.last().pose());

        if(shader.PROJECTION_MATRIX != null)
            shader.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());

        if(shader.INVERSE_VIEW_ROTATION_MATRIX != null)
            shader.INVERSE_VIEW_ROTATION_MATRIX.set(RenderSystem.getInverseViewRotationMatrix());

        if(shader.COLOR_MODULATOR != null)
            shader.COLOR_MODULATOR.set(RenderSystem.getShaderColor());

        if(shader.LIGHT0_DIRECTION != null)
            shader.LIGHT0_DIRECTION.set(light0Direction);

        if(shader.LIGHT1_DIRECTION != null)
            shader.LIGHT1_DIRECTION.set(light1Direction);

        if(shader.FOG_START != null)
            shader.FOG_START.set(RenderSystem.getShaderFogStart());

        if(shader.FOG_END != null)
            shader.FOG_END.set(RenderSystem.getShaderFogEnd());

        if(shader.FOG_COLOR != null)
            shader.FOG_COLOR.set(RenderSystem.getShaderFogColor());

        if(shader.FOG_SHAPE != null)
            shader.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());

        if (shader.TEXTURE_MATRIX != null) 
            shader.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());

        if (shader.GAME_TIME != null) 
            shader.GAME_TIME.set(RenderSystem.getShaderGameTime());

        if (shader.SCREEN_SIZE != null) {
            Window window = Minecraft.getInstance().getWindow();
            shader.SCREEN_SIZE.set((float)window.getScreenWidth(), (float)window.getScreenHeight());
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
}
