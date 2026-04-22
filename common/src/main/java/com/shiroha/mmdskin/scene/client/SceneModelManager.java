package com.shiroha.mmdskin.scene.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.asset.catalog.ModelInfo;
import com.shiroha.mmdskin.bridge.runtime.NativeRuntimeBridgeHolder;
import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.model.runtime.ModelInstance;
import com.shiroha.mmdskin.model.runtime.ModelRequestKey;
import com.shiroha.mmdskin.render.bootstrap.ClientRenderRuntime;
import com.shiroha.mmdskin.render.scene.RenderScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;

/** 文件职责：管理场景模型的放置状态，并复用统一模型仓储完成加载与渲染。 */
public final class SceneModelManager {
    private static final Logger logger = LogManager.getLogger();
    private static final SceneModelManager INSTANCE = new SceneModelManager();
    private static final float MODEL_SCALE = 0.09f;

    private final SceneModelCatalog catalog;

    private volatile ManagedModel sceneModel;
    private volatile String sceneModelName;
    private volatile ModelRequestKey sceneRequestKey;

    private double placeX;
    private double placeY;
    private double placeZ;
    private float placeYaw;
    private boolean active;

    private SceneModelManager() {
        this.catalog = SceneModelCatalog.getInstance();
    }

    public static SceneModelManager getInstance() {
        return INSTANCE;
    }

    public boolean isActive() {
        return active && isValidSceneModel(sceneModel);
    }

    public boolean isLoading() {
        ModelRequestKey requestKey = sceneRequestKey;
        return active
                && requestKey != null
                && ClientRenderRuntime.get().modelRepository().isPending(requestKey);
    }

    public String getSceneModelName() {
        return sceneModelName;
    }

    public void placeScene(String folderName) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || folderName == null || folderName.isBlank()) {
            return;
        }

        ModelInfo modelInfo = catalog.findByFolderName(folderName).orElse(null);
        if (modelInfo == null) {
            logger.warn("Scene model not found: {}", folderName);
            removeScene();
            return;
        }

        removeScene();

        placeX = minecraft.player.getX();
        placeY = minecraft.player.getY();
        placeZ = minecraft.player.getZ();
        placeYaw = minecraft.player.getYRot();
        sceneModelName = folderName;
        sceneRequestKey = ModelRequestKey.scene(folderName, folderName);
        active = true;

        sceneModel = ClientRenderRuntime.get().modelRepository().acquire(sceneRequestKey);
    }

    public void removeScene() {
        sceneModel = null;
        sceneModelName = null;
        sceneRequestKey = null;
        active = false;
    }

    public void renderScene(PoseStack poseStack,
                            float tickDelta,
                            int packedLight,
                            double entityRenderX,
                            double entityRenderY,
                            double entityRenderZ) {
        if (!active) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        ManagedModel managedModel = resolveSceneModel();
        if (managedModel == null || managedModel.modelInstance() == null) {
            return;
        }

        ModelInstance modelInstance = managedModel.modelInstance();
        long modelHandle = modelInstance.getModelHandle();
        if (modelHandle == 0L) {
            sceneModel = null;
            return;
        }

        float posX = (float) (placeX * MODEL_SCALE);
        float posY = (float) (placeY * MODEL_SCALE);
        float posZ = (float) (placeZ * MODEL_SCALE);
        float yawRadians = placeYaw * ((float) Math.PI / 180F);
        NativeRuntimeBridgeHolder.get().setModelPositionAndYaw(modelHandle, posX, posY, posZ, yawRadians);

        float offsetX = (float) (placeX - entityRenderX);
        float offsetY = (float) (placeY - entityRenderY);
        float offsetZ = (float) (placeZ - entityRenderZ);

        poseStack.pushPose();
        poseStack.translate(offsetX, offsetY, offsetZ);

        RenderSystem.setShader(GameRenderer::getRendertypeEntityTranslucentShader);
        modelInstance.render(
                minecraft.player,
                placeYaw,
                0.0f,
                new Vector3f(0.0f, 0.0f, 0.0f),
                tickDelta,
                poseStack,
                packedLight,
                RenderScene.WORLD
        );

        poseStack.popPose();
        NativeRuntimeBridgeHolder.get().setModelPositionAndYaw(modelHandle, posX, posY, posZ, yawRadians);
    }

    private ManagedModel resolveSceneModel() {
        ManagedModel cachedModel = sceneModel;
        if (isValidSceneModel(cachedModel)) {
            return cachedModel;
        }

        ModelRequestKey requestKey = sceneRequestKey;
        if (!active || requestKey == null) {
            return null;
        }

        ManagedModel resolvedModel = ClientRenderRuntime.get().modelRepository().acquire(requestKey);
        if (resolvedModel != null) {
            sceneModel = resolvedModel;
            return resolvedModel;
        }

        sceneModel = null;
        return null;
    }

    private static boolean isValidSceneModel(ManagedModel model) {
        return model != null
                && model.modelInstance() != null
                && model.modelInstance().getModelHandle() != 0L;
    }
}
