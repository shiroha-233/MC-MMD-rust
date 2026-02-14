package com.shiroha.mmdskin.renderer.model;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.renderer.camera.MMDCameraController;
import com.shiroha.mmdskin.renderer.core.EyeTrackingHelper;
import com.shiroha.mmdskin.renderer.core.IMMDModel;
import com.shiroha.mmdskin.renderer.core.RenderContext;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;

/**
 * MMD 模型抽象基类（SRP + LoD）
 *
 * 统一三个渲染器的公共逻辑：
 * - 实体状态处理（头部角度、眼球追踪、物理位置）
 * - deltaTime 计算
 * - 模型名称缓存
 * - 材质 Morph 结果处理
 * - Shader Uniform 设置
 */
public abstract class AbstractMMDModel implements IMMDModel {
    protected static final Logger logger = LogManager.getLogger();
    protected static volatile NativeFunc nf;

    protected static final float MAX_DELTA_TIME = 0.25f;
    protected static final float MODEL_SCALE = 0.09f;

    protected long model;
    protected String modelDir;
    private String cachedModelName;

    // 时间追踪
    protected long lastUpdateTime = -1;

    // 预分配临时对象
    protected final Quaternionf tempQuat = new Quaternionf();

    // 材质 Morph
    protected FloatBuffer materialMorphResultsBuffer;
    protected ByteBuffer materialMorphResultsByteBuffer;
    protected int materialMorphResultCount = 0;

    // 纹理引用键（dispose 时用于批量释放引用计数）
    protected List<String> textureKeys;

    // ===== NativeFunc 访问 =====

    protected static NativeFunc getNf() {
        if (nf == null) nf = NativeFunc.GetInst();
        return nf;
    }

    // ===== IMMDModel 接口实现 =====

    @Override
    public void render(Entity entityIn, float entityYaw, float entityPitch,
                       Vector3f entityTrans, float tickDelta, PoseStack mat,
                       int packedLight, RenderContext context) {
        if (model == 0 || !isReady()) return;

        if (entityIn instanceof LivingEntity living) {
            handleLivingEntity(living, entityYaw, entityPitch, entityTrans,
                    tickDelta, mat, packedLight, context);
            return;
        }
        update();
        doRenderModel(entityIn, entityYaw, entityPitch, entityTrans, mat, packedLight);
    }

    @Override
    public void changeAnim(long anim, long layer) {
        if (model != 0) getNf().ChangeModelAnim(model, anim, layer);
    }

    @Override
    public void transitionAnim(long anim, long layer, float transitionTime) {
        if (model != 0) getNf().TransitionLayerTo(model, layer, anim, transitionTime);
    }

    @Override
    public void resetPhysics() {
        if (model != 0) getNf().ResetModelPhysics(model);
    }

    @Override
    public long getModelHandle() { return model; }

    @Override
    public String getModelDir() { return modelDir; }

    @Override
    public String getModelName() {
        if (cachedModelName == null) {
            cachedModelName = IMMDModel.super.getModelName();
        }
        return cachedModelName;
    }

    // ===== 公共逻辑（模板方法） =====

    /**
     * 处理 LivingEntity 的公共逻辑：头部角度、眼球追踪、物理位置
     */
    private void handleLivingEntity(LivingEntity entityIn, float entityYaw, float entityPitch,
                                     Vector3f entityTrans, float tickDelta, PoseStack mat,
                                     int packedLight, RenderContext context) {
        boolean stagePlaying = MMDCameraController.getInstance().isStagePlayingModel(model);

        // 头部角度（舞台播放时归零，由 VMD 动画控制）
        if (stagePlaying) {
            getNf().SetHeadAngle(model, 0.0f, 0.0f, 0.0f, context.isWorldScene());
        } else {
            float headAngleX = Mth.clamp(entityIn.getXRot(), -50.0f, 50.0f);
            float headAngleY = (entityYaw - Mth.lerp(tickDelta, entityIn.yHeadRotO, entityIn.yHeadRot)) % 360.0f;
            if (headAngleY < -180.0f) headAngleY += 360.0f;
            else if (headAngleY > 180.0f) headAngleY -= 360.0f;
            headAngleY = Mth.clamp(headAngleY, -80.0f, 80.0f);

            float pitchRad = headAngleX * ((float) Math.PI / 180F);
            float yawRad = context.isInventoryScene()
                    ? -headAngleY * ((float) Math.PI / 180F)
                    : headAngleY * ((float) Math.PI / 180F);
            getNf().SetHeadAngle(model, pitchRad, yawRad, 0.0f, context.isWorldScene());
        }

        // 眼球追踪
        if (!stagePlaying) {
            EyeTrackingHelper.updateEyeTracking(getNf(), model, entityIn, entityYaw, tickDelta, getModelName());
        }

        // 传递实体位置和朝向给物理系统（用于人物移动时的惯性效果）
        float posX = (float)(Mth.lerp(tickDelta, entityIn.xo, entityIn.getX()) * MODEL_SCALE);
        float posY = (float)(Mth.lerp(tickDelta, entityIn.yo, entityIn.getY()) * MODEL_SCALE);
        float posZ = (float)(Mth.lerp(tickDelta, entityIn.zo, entityIn.getZ()) * MODEL_SCALE);
        float bodyYaw = Mth.lerp(tickDelta, entityIn.yBodyRotO, entityIn.yBodyRot) * ((float) Math.PI / 180F);
        getNf().SetModelPositionAndYaw(model, posX, posY, posZ, bodyYaw);

        update();
        doRenderModel(entityIn, entityYaw, entityPitch, entityTrans, mat, packedLight);
    }

    /**
     * deltaTime 计算 + 调用子类更新（模板方法）
     */
    protected void update() {
        long currentTime = System.currentTimeMillis();
        if (lastUpdateTime < 0) {
            lastUpdateTime = currentTime;
            return;
        }

        float deltaTime = (currentTime - lastUpdateTime) / 1000.0f;
        lastUpdateTime = currentTime;

        if (deltaTime <= 0.0f) return;
        if (deltaTime > MAX_DELTA_TIME) deltaTime = MAX_DELTA_TIME;

        onUpdate(deltaTime);
    }

    /**
     * 获取材质 Morph 结果
     */
    protected void fetchMaterialMorphResults() {
        if (materialMorphResultCount <= 0 || materialMorphResultsBuffer == null) return;
        materialMorphResultsByteBuffer.clear();
        getNf().CopyMaterialMorphResultsToBuffer(model, materialMorphResultsByteBuffer);
        materialMorphResultsBuffer.clear();
        materialMorphResultsByteBuffer.position(0);
        materialMorphResultsBuffer.put(materialMorphResultsByteBuffer.asFloatBuffer());
        materialMorphResultsBuffer.flip();
    }

    /**
     * 计算材质经 Morph 变形后的有效 alpha
     * 布局：每材质 56 float = mul(28) + add(28)，diffuse.w 在各组偏移 3
     */
    protected float getEffectiveMaterialAlpha(int materialIndex, float baseAlpha) {
        if (materialMorphResultsBuffer == null || materialIndex >= materialMorphResultCount) return baseAlpha;
        int mulOffset = materialIndex * 56 + 3;
        int addOffset = materialIndex * 56 + 28 + 3;
        float mulAlpha = (mulOffset < materialMorphResultsBuffer.capacity()) ? materialMorphResultsBuffer.get(mulOffset) : 1.0f;
        float addAlpha = (addOffset < materialMorphResultsBuffer.capacity()) ? materialMorphResultsBuffer.get(addOffset) : 0.0f;
        return baseAlpha * mulAlpha + addAlpha;
    }

    /**
     * 获取模型缩放比例（统一访问配置）
     */
    protected float getModelScale() {
        return MODEL_SCALE * com.shiroha.mmdskin.config.ModelConfigManager.getConfig(getModelName()).modelScale;
    }

    // ===== 资源释放辅助方法 =====

    /** 释放模型原生句柄 */
    protected void disposeModelHandle() {
        if (model != 0) {
            getNf().DeleteModel(model);
            model = 0;
        }
    }

    /** 释放该模型引用的所有纹理（减少引用计数） */
    protected void releaseTextures() {
        if (textureKeys != null) {
            com.shiroha.mmdskin.renderer.resource.MMDTextureManager.releaseAll(textureKeys);
            textureKeys = null;
        }
    }

    /** 释放材质 Morph 缓冲区 */
    protected void disposeMaterialMorphBuffers() {
        if (materialMorphResultsBuffer != null) {
            MemoryUtil.memFree(materialMorphResultsBuffer);
            materialMorphResultsBuffer = null;
        }
        if (materialMorphResultsByteBuffer != null) {
            MemoryUtil.memFree(materialMorphResultsByteBuffer);
            materialMorphResultsByteBuffer = null;
        }
    }

    // ===== 共享工具方法 =====

    /**
     * 设置 Minecraft ShaderInstance 的标准 Uniform
     * OpenGL 和 GpuSkinning 渲染器共用
     */
    protected static void setupShaderUniforms(ShaderInstance shader, PoseStack deliverStack,
                                               Vector3f light0Dir, Vector3f light1Dir, int lightMapTex) {
        if (shader.MODEL_VIEW_MATRIX != null)
            shader.MODEL_VIEW_MATRIX.set(deliverStack.last().pose());
        if (shader.PROJECTION_MATRIX != null)
            shader.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
        if (shader.INVERSE_VIEW_ROTATION_MATRIX != null)
            shader.INVERSE_VIEW_ROTATION_MATRIX.set(RenderSystem.getInverseViewRotationMatrix());
        if (shader.COLOR_MODULATOR != null)
            shader.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        if (shader.LIGHT0_DIRECTION != null)
            shader.LIGHT0_DIRECTION.set(light0Dir);
        if (shader.LIGHT1_DIRECTION != null)
            shader.LIGHT1_DIRECTION.set(light1Dir);
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

        shader.setSampler("Sampler1", lightMapTex);
        shader.setSampler("Sampler2", lightMapTex);
        // Iris 兼容
        RenderSystem.setShaderTexture(1, lightMapTex);
        RenderSystem.setShaderTexture(2, lightMapTex);
    }

    // ===== 子类实现 =====

    /** 执行实际渲染逻辑 */
    protected abstract void doRenderModel(Entity entityIn, float entityYaw, float entityPitch,
                                           Vector3f entityTrans, PoseStack mat, int packedLight);

    /** 执行模型更新（UpdateModel 或 UpdateAnimationOnly） */
    protected abstract void onUpdate(float deltaTime);

    /**
     * 子类可重写此方法，表示模型是否已完全初始化并可安全渲染。
     * 默认返回 true（OpenGL 无额外初始化阶段）。
     */
    protected boolean isReady() {
        return true;
    }
}
