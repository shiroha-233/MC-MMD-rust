package com.shiroha.mmdskin.renderer.model;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.renderer.animation.MMDAnimManager;
import com.shiroha.mmdskin.renderer.core.IMMDModel;
import com.shiroha.mmdskin.renderer.core.RenderContext;
import com.shiroha.mmdskin.renderer.core.RenderModeManager;
import com.shiroha.mmdskin.renderer.resource.MMDTextureManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 场景模型管理器
 * 管理放置在世界中的静态 MMD 场景模型的生命周期
 */
public final class SceneModelManager {
    private static final Logger logger = LogManager.getLogger();
    private static final SceneModelManager INSTANCE = new SceneModelManager();

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

    private SceneModelManager() {}

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

        ModelInfo info = scanSceneModelByFolder(folderName);
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

    /**
     * 每帧渲染调用（由 PlayerMixinDelegate 在本地玩家渲染后调用）
     * PoseStack 原点为玩家插值渲染位置
     *
     * 关键：世界空间偏移必须在 PoseStack 上直接 translate，而非通过 entityTrans 传递。
     * 原因：doRenderModel 中旋转(entityYaw)在 entityTrans 平移之前应用，
     * 若将偏移放在 entityTrans 中，偏移方向会随模型朝向旋转导致位置错误。
     */
    public void renderScene(PoseStack matrixStack, float tickDelta, int packedLight,
                            double entityRenderX, double entityRenderY, double entityRenderZ) {
        checkPendingLoad();

        if (!active || sceneModel == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        NativeFunc nf = NativeFunc.GetInst();
        long handle = sceneModel.getModelHandle();
        float posX = (float)(placeX * AbstractMMDModel.MODEL_SCALE);
        float posY = (float)(placeY * AbstractMMDModel.MODEL_SCALE);
        float posZ = (float)(placeZ * AbstractMMDModel.MODEL_SCALE);
        float yawRad = placeYaw * ((float) Math.PI / 180F);

        // 渲染前设置物理位置（handleLivingEntity 会用玩家坐标覆盖，渲染后需再次修正）
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
            float posX = (float)(placeX * AbstractMMDModel.MODEL_SCALE);
            float posY = (float)(placeY * AbstractMMDModel.MODEL_SCALE);
            float posZ = (float)(placeZ * AbstractMMDModel.MODEL_SCALE);
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

    // ===== 场景模型扫描 =====

    private static volatile List<ModelInfo> sceneModelCache;
    private static volatile long sceneCacheTimestamp;
    private static final long SCENE_CACHE_TTL = 5000;

    public static List<ModelInfo> scanSceneModels() {
        long now = System.currentTimeMillis();
        if (sceneModelCache != null && (now - sceneCacheTimestamp) < SCENE_CACHE_TTL) {
            return sceneModelCache;
        }

        PathConstants.ensureSceneModelDir();
        List<ModelInfo> models = new ArrayList<>();
        File sceneDir = PathConstants.getSceneModelDir();

        if (!sceneDir.exists() || !sceneDir.isDirectory()) {
            return models;
        }

        File[] dirs = sceneDir.listFiles(File::isDirectory);
        if (dirs == null) return models;

        for (File dir : dirs) {
            ModelInfo info = scanFolder(dir);
            if (info != null) {
                models.add(info);
            }
        }

        models.sort((a, b) -> a.getFolderName().compareToIgnoreCase(b.getFolderName()));
        sceneModelCache = models;
        sceneCacheTimestamp = System.currentTimeMillis();
        return models;
    }

    public static void invalidateSceneCache() {
        sceneModelCache = null;
    }

    private static ModelInfo scanSceneModelByFolder(String folderName) {
        List<ModelInfo> models = scanSceneModels();
        for (ModelInfo info : models) {
            if (info.getFolderName().equals(folderName)) {
                return info;
            }
        }
        return null;
    }

    private static ModelInfo scanFolder(File dir) {
        FileFilter pmxFilter = f -> f.isFile() && f.getName().toLowerCase().endsWith(".pmx");
        FileFilter pmdFilter = f -> f.isFile() && f.getName().toLowerCase().endsWith(".pmd");
        FileFilter vrmFilter = f -> f.isFile() && f.getName().toLowerCase().endsWith(".vrm");

        File[] pmxFiles = dir.listFiles(pmxFilter);
        if (pmxFiles != null && pmxFiles.length > 0) {
            File sel = pickFirst(pmxFiles);
            return new ModelInfo(dir.getName(), dir.getAbsolutePath(),
                    sel.getAbsolutePath(), sel.getName(), false, false, sel.length());
        }

        File[] pmdFiles = dir.listFiles(pmdFilter);
        if (pmdFiles != null && pmdFiles.length > 0) {
            File sel = pickFirst(pmdFiles);
            return new ModelInfo(dir.getName(), dir.getAbsolutePath(),
                    sel.getAbsolutePath(), sel.getName(), true, false, sel.length());
        }

        File[] vrmFiles = dir.listFiles(vrmFilter);
        if (vrmFiles != null && vrmFiles.length > 0) {
            File sel = pickFirst(vrmFiles);
            return new ModelInfo(dir.getName(), dir.getAbsolutePath(),
                    sel.getAbsolutePath(), sel.getName(), false, true, sel.length());
        }

        return null;
    }

    private static File pickFirst(File[] files) {
        if (files.length == 1) return files[0];
        for (File f : files) {
            String name = f.getName().toLowerCase();
            if (name.equals("model.pmx") || name.equals("model.pmd")) return f;
        }
        java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return files[0];
    }
}
