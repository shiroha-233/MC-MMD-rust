package com.shiroha.mmdskin.renderer.runtime.model;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.renderer.api.IMMDModel;
import com.shiroha.mmdskin.renderer.api.RenderContext;
import com.shiroha.mmdskin.renderer.runtime.bridge.ModelRuntimeBridgeHolder;
import com.shiroha.mmdskin.renderer.integration.player.PlayerPerformanceGate;
import com.shiroha.mmdskin.renderer.performance.RenderPerformanceProfiler;
import com.shiroha.mmdskin.renderer.runtime.model.helper.LivingEntityModelStateHelper;
import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import com.shiroha.mmdskin.renderer.compat.RenderSystemCompat;
import com.shiroha.mmdskin.renderer.compat.ShaderInstanceStub;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MMD 模型抽象基类。
 */
public abstract class AbstractMMDModel implements IMMDModel {
    protected static final Logger logger = LogManager.getLogger();
    protected static volatile NativeFunc nf;

    protected static final float MAX_DELTA_TIME = 0.25f;
    protected static final float MODEL_SCALE = 0.09f;
    protected long model;
    protected String modelDir;
    private String cachedModelName;

    private boolean isVrmModel;
    private boolean isVrmChecked;

    protected long lastUpdateTime = -1;

    protected final Quaternionf tempQuat = new Quaternionf();

    protected ByteBuffer materialMorphResultsByteBuffer;
    protected int materialMorphResultCount = 0;

    protected List<String> textureKeys;

    private volatile boolean vrActive;
    protected final AtomicLong nativeUpdateRevision = new AtomicLong(0L);

    public void setVrActive(boolean active) { this.vrActive = active; }

    public boolean isVrActive() { return vrActive; }

    protected static NativeFunc getNf() {
        if (nf == null) nf = NativeFunc.GetInst();
        return nf;
    }

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
        boolean shouldUpdate = shouldUpdateForContext(entityIn, context);
        applyPhysicsState(resolvePhysicsEnabled(entityIn, context, false, shouldUpdate));
        if (shouldUpdate) {
            update();
        }
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
    public void setLayerLoop(long layer, boolean loop) {
        if (model != 0) getNf().SetLayerLoop(model, layer, loop);
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
    public boolean setLayerBoneMask(int layer, String rootBoneName) {
        return ModelRuntimeBridgeHolder.get().setLayerBoneMask(model, layer, rootBoneName);
    }

    @Override
    public boolean setLayerBoneExclude(int layer, String rootBoneName) {
        return ModelRuntimeBridgeHolder.get().setLayerBoneExclude(model, layer, rootBoneName);
    }

    @Override
    public String getModelName() {
        if (cachedModelName == null) {
            cachedModelName = IMMDModel.super.getModelName();
        }
        return cachedModelName;
    }

    protected boolean checkVrm() {
        if (!isVrmChecked && model != 0) {
            isVrmChecked = true;
            try {
                isVrmModel = getNf().IsVrmModel(model);
            } catch (Exception e) {
                isVrmModel = false;
            }
        }
        return isVrmModel;
    }

    @Override
    public long getRamUsage() {
        try {
            return ModelRuntimeBridgeHolder.get().getModelMemoryUsage(model);
        } catch (Exception e) {
            return 0;
        }
    }

    private void handleLivingEntity(LivingEntity entityIn, float entityYaw, float entityPitch,
                                     Vector3f entityTrans, float tickDelta, PoseStack mat,
                                     int packedLight, RenderContext context) {
        boolean stagePlaying = MMDCameraController.getInstance().isStagePlayingModel(model);
        boolean localPlayer = isLocalPlayer(entityIn);
        boolean shouldUpdate = shouldUpdateForContext(entityIn, context);
        applyPhysicsState(resolvePhysicsEnabled(entityIn, context, localPlayer, shouldUpdate));
        if (shouldUpdate) {
            long syncTimer = RenderPerformanceProfiler.get().startTimer();
            LivingEntityModelStateHelper.syncModelState(
                    getNf(),
                    model,
                    entityIn,
                    entityYaw,
                    tickDelta,
                    context,
                    getModelName(),
                    stagePlaying,
                    vrActive);
            RenderPerformanceProfiler.get().endTimer(RenderPerformanceProfiler.SECTION_LIVING_STATE_SYNC, syncTimer);
            update();
        }
        doRenderModel(entityIn, entityYaw, entityPitch, entityTrans, mat, packedLight);
    }

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

        long updateTimer = RenderPerformanceProfiler.get().startTimer();
        try {
            onUpdate(deltaTime);
            nativeUpdateRevision.incrementAndGet();
        } finally {
            RenderPerformanceProfiler.get().endTimer(RenderPerformanceProfiler.SECTION_NATIVE_MODEL_UPDATE, updateTimer);
        }
    }

    protected long getNativeUpdateRevision() {
        return nativeUpdateRevision.get();
    }

    protected void fetchMaterialMorphResults() {
        if (materialMorphResultCount <= 0 || materialMorphResultsByteBuffer == null) return;
        materialMorphResultsByteBuffer.clear();
        getNf().CopyMaterialMorphResultsToBuffer(model, materialMorphResultsByteBuffer);
        materialMorphResultsByteBuffer.rewind();
    }

    private static final int MATERIAL_MORPH_STRIDE_FLOATS = 56;
    private static final int MATERIAL_MORPH_MUL_ALPHA_OFFSET = 3;
    private static final int MATERIAL_MORPH_ADD_ALPHA_OFFSET = 28 + 3;

    protected float getEffectiveMaterialAlpha(int materialIndex, float baseAlpha) {
        if (materialMorphResultsByteBuffer == null || materialIndex < 0 || materialIndex >= materialMorphResultCount)
            return baseAlpha;
        int mulOffset = materialIndex * MATERIAL_MORPH_STRIDE_FLOATS + MATERIAL_MORPH_MUL_ALPHA_OFFSET;
        int addOffset = materialIndex * MATERIAL_MORPH_STRIDE_FLOATS + MATERIAL_MORPH_ADD_ALPHA_OFFSET;
        int capacity = materialMorphResultsByteBuffer.capacity() / 4;
        float mulAlpha = (mulOffset < capacity) ? materialMorphResultsByteBuffer.getFloat(mulOffset * 4) : 1.0f;
        float addAlpha = (addOffset < capacity) ? materialMorphResultsByteBuffer.getFloat(addOffset * 4) : 0.0f;
        return baseAlpha * mulAlpha + addAlpha;
    }

    protected float getModelScale() {
        return MODEL_SCALE * com.shiroha.mmdskin.config.ModelConfigManager.getLiveConfig(getModelName()).modelScale;
    }

    protected void disposeModelHandle() {
        if (model != 0) {
            ModelRuntimeBridgeHolder.get().deleteModel(model);
            model = 0;
        }
    }

    protected void releaseTextures() {
        if (textureKeys != null) {
            com.shiroha.mmdskin.renderer.runtime.texture.MMDTextureManager.releaseAll(textureKeys);
            textureKeys = null;
        }
    }

    protected void disposeMaterialMorphBuffers() {
        if (materialMorphResultsByteBuffer != null) {
            MemoryUtil.memFree(materialMorphResultsByteBuffer);
            materialMorphResultsByteBuffer = null;
        }
    }

    protected static void setupShaderUniforms(ShaderInstanceStub shader, PoseStack deliverStack,
                                                Vector3f light0Dir, Vector3f light1Dir, int lightMapTex) {
        // TODO_1.21.11: 渲染管线重写 - ShaderInstance/RenderSystem 状态方法已删除
        if (shader.MODEL_VIEW_MATRIX != null)
            shader.MODEL_VIEW_MATRIX.set(computeModelViewMatrix(deliverStack));
        if (shader.PROJECTION_MATRIX != null)
            shader.PROJECTION_MATRIX.set(RenderSystemCompat.getProjectionMatrix());
        // TODO_1.21.11: COLOR_MODULATOR / LIGHT*_DIRECTION / FOG_* / GAME_TIME / SCREEN_SIZE / LINE_WIDTH 字段已变为 Object 占位
        // 原有 shader.COLOR_MODULATOR.set(...) 等调用均移除
        if (shader.TEXTURE_MATRIX != null)
            shader.TEXTURE_MATRIX.set(new Matrix4f());

        shader.setSampler("Sampler1", lightMapTex);
        shader.setSampler("Sampler2", lightMapTex);

        // TODO_1.21.11: RenderSystem.setShaderTexture 已删除
    }

    public static Matrix4f computeModelViewMatrix(PoseStack deliverStack) {
        return new Matrix4f(RenderSystemCompat.getModelViewMatrix()).mul(deliverStack.last().pose());
    }

    protected abstract void doRenderModel(Entity entityIn, float entityYaw, float entityPitch,
                                            Vector3f entityTrans, PoseStack mat, int packedLight);

    protected abstract void onUpdate(float deltaTime);

    protected boolean isReady() {
        return true;
    }

    private boolean shouldUpdateForContext(Entity entity, RenderContext context) {
        if (context == null || !context.isWorldScene()) {
            return true;
        }
        return PlayerPerformanceGate.shouldUpdateAnimation(entity, isLocalPlayer(entity), model);
    }

    private boolean resolvePhysicsEnabled(Entity entity, RenderContext context, boolean localPlayer, boolean shouldUpdate) {
        if (context == null || !context.isWorldScene()) {
            return ConfigManager.isPhysicsEnabled();
        }
        return shouldUpdate && PlayerPerformanceGate.shouldEnablePhysics(entity, localPlayer);
    }

    private void applyPhysicsState(boolean enabled) {
        try {
            getNf().SetPhysicsEnabled(model, enabled);
        } catch (Exception e) {
            logger.debug("Failed to update physics state for model {}", model, e);
        }
    }

    private boolean isLocalPlayer(Entity entity) {
        if (!(entity instanceof net.minecraft.world.entity.player.Player player)) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.getUUID().equals(player.getUUID());
    }
}
