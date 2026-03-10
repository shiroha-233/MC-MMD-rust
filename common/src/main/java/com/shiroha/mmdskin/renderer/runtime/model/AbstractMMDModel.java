package com.shiroha.mmdskin.renderer.runtime.model;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.renderer.api.IMMDModel;
import com.shiroha.mmdskin.renderer.api.RenderContext;
import com.shiroha.mmdskin.renderer.runtime.bridge.ModelRuntimeBridgeHolder;
import com.shiroha.mmdskin.renderer.runtime.model.helper.LivingEntityModelStateHelper;
import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
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

        update();
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

        onUpdate(deltaTime);
    }

    protected void fetchMaterialMorphResults() {
        if (materialMorphResultCount <= 0 || materialMorphResultsByteBuffer == null) return;
        materialMorphResultsByteBuffer.clear();
        getNf().CopyMaterialMorphResultsToBuffer(model, materialMorphResultsByteBuffer);
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
}
