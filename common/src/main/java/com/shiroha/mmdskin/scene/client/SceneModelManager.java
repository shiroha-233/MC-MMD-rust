package com.shiroha.mmdskin.scene.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.asset.catalog.ModelInfo;
import com.shiroha.mmdskin.renderer.runtime.animation.MMDAnimManager;
import com.shiroha.mmdskin.renderer.api.IMMDModel;
import com.shiroha.mmdskin.renderer.api.RenderContext;
import com.shiroha.mmdskin.renderer.runtime.mode.RenderModeManager;
import com.shiroha.mmdskin.renderer.runtime.texture.MMDTextureManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** 管理场景模型的放置、异步加载与渲染。 */
public final class SceneModelManager {
    private static final Logger logger = LogManager.getLogger();
    private static final SceneModelManager INSTANCE = new SceneModelManager();
    private static final float MODEL_SCALE = 0.09f;

    private static final ExecutorService loadExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MMD-SceneLoader");
        t.setDaemon(true);
        return t;
    });

    private volatile IMMDModel sceneModel;
    private volatile String sceneModelName;
    private double placeX, placeY, placeZ;
    private float placeYaw;
    private boolean active;

    private Future<LoadResult> pendingLoad;
    private volatile boolean loading;
    private final SceneModelCatalog catalog;

    private SceneModelManager() {
        this.catalog = SceneModelCatalog.getInstance();
    }

    public static SceneModelManager getInstance() { return INSTANCE; }

    public boolean isActive() { return active && sceneModel != null; }

    public boolean isLoading() { return loading; }

    public String getSceneModelName() { return sceneModelName; }

    public void placeScene(String folderName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        removeScene();

        this.placeX = mc.player.getX();
        this.placeY = mc.player.getY();
        this.placeZ = mc.player.getZ();
        this.placeYaw = mc.player.getYRot();
        this.sceneModelName = folderName;
        this.active = true;

        ModelInfo info = catalog.findByFolderName(folderName).orElse(null);
        if (info == null) {
            logger.warn("场景模型未找到: {}", folderName);
            this.active = false;
            return;
        }

        startBackgroundLoad(info);
    }

    public void removeScene() {
        if (pendingLoad != null && !pendingLoad.isDone()) {
            pendingLoad.cancel(true);
        }
        pendingLoad = null;
        loading = false;

        if (sceneModel != null) {
            MMDAnimManager.DeleteModel(sceneModel);
            sceneModel.dispose();
            sceneModel = null;
        }
        sceneModelName = null;
        active = false;
    }

    public void renderScene(PoseStack matrixStack, float tickDelta, int packedLight,
                            double entityRenderX, double entityRenderY, double entityRenderZ) {
        checkPendingLoad();

        if (!active || sceneModel == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        NativeFunc nf = NativeFunc.GetInst();
        long handle = sceneModel.getModelHandle();
        float posX = (float)(placeX * MODEL_SCALE);
        float posY = (float)(placeY * MODEL_SCALE);
        float posZ = (float)(placeZ * MODEL_SCALE);
        float yawRad = placeYaw * ((float) Math.PI / 180F);

        nf.SetModelPositionAndYaw(handle, posX, posY, posZ, yawRad);

        float offsetX = (float)(placeX - entityRenderX);
        float offsetY = (float)(placeY - entityRenderY);
        float offsetZ = (float)(placeZ - entityRenderZ);

        matrixStack.pushPose();
        matrixStack.translate(offsetX, offsetY, offsetZ);

        RenderSystem.setShader(GameRenderer::getRendertypeEntityTranslucentShader);
        sceneModel.render(mc.player, placeYaw, 0.0f, new Vector3f(0, 0, 0), tickDelta,
                matrixStack, packedLight, RenderContext.WORLD);

        matrixStack.popPose();

        nf.SetModelPositionAndYaw(handle, posX, posY, posZ, yawRad);
    }

    private void checkPendingLoad() {
        if (pendingLoad == null || !pendingLoad.isDone()) return;

        try {
            LoadResult result = pendingLoad.get();
            pendingLoad = null;
            loading = false;

            if (result == null || result.modelHandle == 0) {
                logger.error("场景模型加载失败: {}", sceneModelName);
                active = false;
                return;
            }

            IMMDModel m = RenderModeManager.createModelFromHandle(
                    result.modelHandle, result.modelInfo.getFolderPath(), result.modelInfo.isPMD());
            if (m == null) {
                logger.error("场景模型 GL 资源创建失败: {}", sceneModelName);
                NativeFunc.GetInst().DeleteModel(result.modelHandle);
                active = false;
                return;
            }

            MMDAnimManager.AddModel(m);
            this.sceneModel = m;

            NativeFunc nativeFunc = NativeFunc.GetInst();
            float posX = (float)(placeX * MODEL_SCALE);
            float posY = (float)(placeY * MODEL_SCALE);
            float posZ = (float)(placeZ * MODEL_SCALE);
            float yawRad = placeYaw * ((float) Math.PI / 180F);
            nativeFunc.SetModelPositionAndYaw(m.getModelHandle(), posX, posY, posZ, yawRad);

            logger.info("场景模型加载完成: {}", sceneModelName);
        } catch (Exception e) {
            logger.error("场景模型加载异常: {}", sceneModelName, e);
            pendingLoad = null;
            loading = false;
            active = false;
        }
    }

    private void startBackgroundLoad(ModelInfo modelInfo) {
        loading = true;
        pendingLoad = loadExecutor.submit(() -> {
            long handle = 0;
            try {
                NativeFunc nf = NativeFunc.GetInst();
                if (modelInfo.isVRM()) {
                    handle = nf.LoadModelVRM(modelInfo.getModelFilePath(), modelInfo.getFolderPath(), 3);
                } else if (modelInfo.isPMD()) {
                    handle = nf.LoadModelPMD(modelInfo.getModelFilePath(), modelInfo.getFolderPath(), 3);
                } else {
                    handle = nf.LoadModelPMX(modelInfo.getModelFilePath(), modelInfo.getFolderPath(), 3);
                }
                if (handle == 0) return null;

                preloadTextures(nf, handle, modelInfo.getFolderPath());
                return new LoadResult(handle, modelInfo);
            } catch (Exception e) {
                logger.error("场景模型后台加载异常", e);
                if (handle != 0) {
                    try { NativeFunc.GetInst().DeleteModel(handle); } catch (Exception ignored) {}
                }
                return null;
            }
        });
    }

    private void preloadTextures(NativeFunc nf, long modelHandle, String modelDir) {
        try {
            int matCount = (int) nf.GetMaterialCount(modelHandle);
            for (int i = 0; i < matCount; i++) {
                String texPath = nf.GetMaterialTex(modelHandle, i);
                if (texPath != null && !texPath.isEmpty()) {
                    MMDTextureManager.preloadTexture(texPath);
                }
            }
        } catch (Exception e) {
            logger.warn("场景模型纹理预解码部分失败", e);
        }
    }

    private static class LoadResult {
        final long modelHandle;
        final ModelInfo modelInfo;

        LoadResult(long modelHandle, ModelInfo modelInfo) {
            this.modelHandle = modelHandle;
            this.modelInfo = modelInfo;
        }
    }
}
