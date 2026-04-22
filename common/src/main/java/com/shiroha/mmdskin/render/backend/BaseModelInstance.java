package com.shiroha.mmdskin.render.backend;

import com.shiroha.mmdskin.bridge.runtime.NativeRenderBackendPort;
import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.model.runtime.ModelInstance;
import com.shiroha.mmdskin.render.scene.RenderScene;
import com.shiroha.mmdskin.bridge.runtime.NativeRuntimeBridgeHolder;
import com.shiroha.mmdskin.render.pipeline.LivingEntityModelStateHelper;
import com.shiroha.mmdskin.render.pipeline.RenderPerformanceProfiler;
import com.shiroha.mmdskin.render.policy.WorldRenderPolicy;
import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import com.shiroha.mmdskin.texture.runtime.TextureRepository;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * MMD 模型抽象基类。
 */
public abstract class BaseModelInstance implements ModelInstance {
    protected static final Logger logger = LogManager.getLogger();

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
    protected long nativeUpdateRevision = 0L;
    private boolean physicsStateInitialized = false;
    private boolean physicsEnabled = true;

    public void setVrActive(boolean active) { this.vrActive = active; }

    public boolean isVrActive() { return vrActive; }

    protected static NativeRenderBackendPort backendPort() {
        return NativeRuntimeBridgeHolder.get();
    }

    @Override
    public void render(Entity entityIn, float entityYaw, float entityPitch,
                       Vector3f entityTrans, float tickDelta, PoseStack mat,
                       int packedLight, RenderScene context) {
        if (model == 0 || !isReady()) return;

        WorldRenderPolicy.Decision worldDecision = nonWorldDecision();
        if (context != null && context.isWorldScene()) {
            worldDecision = WorldRenderPolicy.get().resolve(model, entityIn);
            if (!worldDecision.shouldRender()) {
                return;
            }
        } else {
            applyPhysicsState(ConfigManager.isPhysicsEnabled());
        }

        if (entityIn instanceof LivingEntity living) {
            handleLivingEntity(living, entityYaw, entityPitch, entityTrans,
                    tickDelta, mat, packedLight, context, worldDecision);
            return;
        }

        applyPhysicsState(worldDecision.physicsEnabled());
        if (worldDecision.shouldUpdate()) {
            update();
        }
        doRenderModel(entityIn, entityYaw, entityPitch, entityTrans, mat, packedLight);
    }

    @Override
    public void changeAnim(long anim, long layer) {
        if (model != 0) backendPort().changeModelAnimation(model, anim, layer);
    }

    @Override
    public void transitionAnim(long anim, long layer, float transitionTime) {
        if (model != 0) backendPort().transitionLayerTo(model, layer, anim, transitionTime);
    }

    @Override
    public void setLayerLoop(long layer, boolean loop) {
        if (model != 0) backendPort().setLayerLoop(model, layer, loop);
    }

    @Override
    public void resetPhysics() {
        if (model != 0) backendPort().resetModelPhysics(model);
    }

    @Override
    public long getModelHandle() { return model; }

    @Override
    public String getModelDir() { return modelDir; }

    @Override
    public boolean setLayerBoneMask(int layer, String rootBoneName) {
        return NativeRuntimeBridgeHolder.get().setLayerBoneMask(model, layer, rootBoneName);
    }

    @Override
    public boolean setLayerBoneExclude(int layer, String rootBoneName) {
        return NativeRuntimeBridgeHolder.get().setLayerBoneExclude(model, layer, rootBoneName);
    }

    @Override
    public String getModelName() {
        if (cachedModelName == null) {
            cachedModelName = ModelInstance.super.getModelName();
        }
        return cachedModelName;
    }

    protected boolean checkVrm() {
        if (!isVrmChecked && model != 0) {
            isVrmChecked = true;
            try {
                isVrmModel = backendPort().isVrmModel(model);
            } catch (Exception e) {
                isVrmModel = false;
            }
        }
        return isVrmModel;
    }

    @Override
    public long getRamUsage() {
        try {
            return NativeRuntimeBridgeHolder.get().getModelMemoryUsage(model);
        } catch (Exception e) {
            return 0;
        }
    }

    private void handleLivingEntity(LivingEntity entityIn, float entityYaw, float entityPitch,
                                     Vector3f entityTrans, float tickDelta, PoseStack mat,
                                     int packedLight, RenderScene context, WorldRenderPolicy.Decision worldDecision) {
        boolean stagePlaying = MMDCameraController.getInstance().isStagePlayingModel(model);

        applyPhysicsState(worldDecision.physicsEnabled());

        if (worldDecision.shouldUpdate()) {
            long syncTimer = RenderPerformanceProfiler.get().startTimer();
            LivingEntityModelStateHelper.syncModelState(
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

    protected boolean update() {
        long currentTime = System.currentTimeMillis();
        if (lastUpdateTime < 0) {
            lastUpdateTime = currentTime;
            return false;
        }

        float deltaTime = (currentTime - lastUpdateTime) / 1000.0f;
        lastUpdateTime = currentTime;

        if (deltaTime <= 0.0f) return false;
        if (deltaTime > MAX_DELTA_TIME) deltaTime = MAX_DELTA_TIME;

        long updateTimer = RenderPerformanceProfiler.get().startTimer();
        onUpdate(deltaTime);
        RenderPerformanceProfiler.get().endTimer(RenderPerformanceProfiler.SECTION_NATIVE_MODEL_UPDATE, updateTimer);
        nativeUpdateRevision++;
        return true;
    }

    protected long getNativeUpdateRevision() {
        return nativeUpdateRevision;
    }

    protected void fetchMaterialMorphResults() {
        if (materialMorphResultCount <= 0 || materialMorphResultsByteBuffer == null) return;
        materialMorphResultsByteBuffer.clear();
        backendPort().copyMaterialMorphResultsToBuffer(model, materialMorphResultsByteBuffer);
        materialMorphResultsByteBuffer.rewind();
    }

    protected float getEffectiveMaterialAlpha(int materialIndex, float baseAlpha) {
        if (materialMorphResultsByteBuffer == null || materialIndex >= materialMorphResultCount) return baseAlpha;
        int mulOffset = materialIndex * 56 + 3;
        int addOffset = materialIndex * 56 + 28 + 3;
        int capacity = materialMorphResultsByteBuffer.capacity() / 4;
        float mulAlpha = (mulOffset < capacity) ? materialMorphResultsByteBuffer.getFloat(mulOffset * 4) : 1.0f;
        float addAlpha = (addOffset < capacity) ? materialMorphResultsByteBuffer.getFloat(addOffset * 4) : 0.0f;
        return baseAlpha * mulAlpha + addAlpha;
    }

    protected float getModelScale() {
        return MODEL_SCALE * com.shiroha.mmdskin.config.ModelConfigManager.getConfig(getModelName()).modelScale;
    }

    protected void disposeModelHandle() {
        if (model != 0) {
            NativeRuntimeBridgeHolder.get().deleteModel(model);
            model = 0;
        }
    }

    protected void releaseTextures() {
        if (textureKeys != null) {
            TextureRepository.releaseAll(textureKeys);
            textureKeys = null;
        }
    }

    protected void disposeMaterialMorphBuffers() {
        if (materialMorphResultsByteBuffer != null) {
            MemoryUtil.memFree(materialMorphResultsByteBuffer);
            materialMorphResultsByteBuffer = null;
        }
    }

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

        RenderSystem.setShaderTexture(1, lightMapTex);
        RenderSystem.setShaderTexture(2, lightMapTex);
    }

    protected abstract void doRenderModel(Entity entityIn, float entityYaw, float entityPitch,
                                           Vector3f entityTrans, PoseStack mat, int packedLight);

    protected abstract void onUpdate(float deltaTime);

    protected boolean isReady() {
        return true;
    }

    private void applyPhysicsState(boolean enabled) {
        if (model == 0) {
            return;
        }

        if (!physicsStateInitialized || physicsEnabled != enabled) {
            backendPort().setPhysicsEnabled(model, enabled);
            physicsEnabled = enabled;
            physicsStateInitialized = true;
        }
    }

    private WorldRenderPolicy.Decision nonWorldDecision() {
        return new WorldRenderPolicy.Decision(true, true, ConfigManager.isPhysicsEnabled());
    }
}
