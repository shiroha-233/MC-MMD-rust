package com.shiroha.mmdskin.renderer.runtime.model;

import com.shiroha.mmdskin.config.ModelConfigData;
import com.shiroha.mmdskin.config.ModelConfigManager;
import com.shiroha.mmdskin.renderer.runtime.animation.MMDAnimManager;
import com.shiroha.mmdskin.player.runtime.EntityAnimState;
import com.shiroha.mmdskin.renderer.api.IMMDModel;
import com.shiroha.mmdskin.renderer.runtime.bridge.ModelRuntimeBridgeHolder;
import com.shiroha.mmdskin.renderer.compat.IrisCompat;
import com.shiroha.mmdskin.renderer.runtime.cache.ModelCache;
import com.shiroha.mmdskin.renderer.runtime.model.loading.ModelLoadCoordinator;
import com.shiroha.mmdskin.renderer.runtime.model.loading.ModelPropertiesLoader;
import com.shiroha.mmdskin.renderer.runtime.mode.RenderModeManager;
import com.shiroha.mmdskin.renderer.runtime.model.factory.ModelFactoryRegistry;
import com.shiroha.mmdskin.renderer.runtime.texture.MMDTextureManager;
import com.shiroha.mmdskin.maid.MaidMMDModelManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * MMD 模型管理器：模型加载生命周期管理，两阶段异步加载。
 */
public class MMDModelManager {
    static final Logger logger = LogManager.getLogger();

    private static ModelCache<Model> modelCache;

    private static final ModelLoadCoordinator loadCoordinator = new ModelLoadCoordinator();

    private static final AtomicInteger totalModelsLoaded = new AtomicInteger(0);

    public static int getTotalModelsLoaded() { return totalModelsLoaded.get(); }

    public static void Init() {
        ModelFactoryRegistry.registerAll();

        modelCache = new ModelCache<>("MMDModel");
        RenderModeManager.init();
    }

    public static Model GetModel(String modelName, String cacheKey) {
        String fullCacheKey = modelName + "_" + cacheKey;

        ModelCache.CacheEntry<Model> entry = modelCache.get(fullCacheKey);
        if (entry != null) {
            return entry.value;
        }

        if (IrisCompat.isRenderingShadows()) {
            return null;
        }

        return loadCoordinator.resolveOrQueue(fullCacheKey, modelName,
                result -> finalizeModelOnRenderThread(fullCacheKey, result));
    }

    private static Model finalizeModelOnRenderThread(String fullCacheKey, ModelLoadCoordinator.AsyncLoadResult result) {
        long startTime = System.currentTimeMillis();

        try {
            IMMDModel m = RenderModeManager.createModelFromHandle(
                result.modelHandle, result.modelInfo.getFolderPath(), result.modelInfo.isPMD());

            if (m == null) {
                logger.error("[异步加载] GL 资源创建失败，释放模型句柄: {}", result.modelName);
                cleanupLoadedResult(result);
                return null;
            }

            MMDAnimManager.AddModel(m);
            Model model = createModelWrapper(fullCacheKey, m, result.modelName);
            modelCache.put(fullCacheKey, model);
            totalModelsLoaded.incrementAndGet();

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("[异步加载] GL 资源创建完成 ({}ms): {}", elapsed, fullCacheKey);
            return model;
        } catch (Exception e) {
            logger.error("[异步加载] GL 资源创建异常: {}", fullCacheKey, e);
            try {
                cleanupLoadedResult(result);
            } catch (Exception ex) {
                logger.error("释放模型句柄失败", ex);
            }
            return null;
        }
    }

    public static Model GetModel(String modelName) {
        return GetModel(modelName, "default");
    }

    public static void forceReloadModel(String modelName) {
        String prefix = modelName + "_";
        loadCoordinator.removeMatching(key -> key.startsWith(prefix), MMDModelManager::cleanupLoadedResult);
        MMDTextureManager.clearPreloaded();
        modelCache.removeMatching(key -> key.startsWith(prefix), MMDModelManager::disposeModel);
    }

    public static void forceReloadPlayerModels(String playerCacheKey) {
        String suffix = "_" + playerCacheKey;
        loadCoordinator.removeMatching(key -> key.endsWith(suffix), MMDModelManager::cleanupLoadedResult);
        modelCache.removeMatching(key -> key.endsWith(suffix), MMDModelManager::disposeModel);
    }

    public static void forceReloadAllModels() {
        cancelAllPendingLoads();
        modelCache.clear(MMDModelManager::disposeModel);
        MaidMMDModelManager.invalidateLoadedModels();
        MMDTextureManager.clearPreloaded();
    }

    private static void cancelAllPendingLoads() {
        loadCoordinator.cancelAll(MMDModelManager::cleanupLoadedResult);
        MMDTextureManager.clearPreloaded();
    }

    private static void cleanupLoadedResult(ModelLoadCoordinator.AsyncLoadResult result) {
        if (result != null && result.modelHandle != 0) {
            ModelRuntimeBridgeHolder.get().deleteModel(result.modelHandle);
        }
    }

    public static void tick() {
        modelCache.tick(MMDModelManager::disposeModel);
        MMDTextureManager.tick();
    }

    private static Model createModelWrapper(String name, IMMDModel model, String modelName) {
        Model m = new Model();
        m.entityName = name;
        m.model = model;
        m.modelName = modelName;
        m.entityData = new EntityAnimState(3);

        model.resetPhysics();
        model.changeAnim(MMDAnimManager.GetAnimModel(model, "idle"), 0);

        applyMaterialVisibility(model.getModelHandle(), modelName);

        return m;
    }

    private static void applyMaterialVisibility(long modelHandle, String modelName) {
        try {
            ModelConfigData config = ModelConfigManager.getConfig(modelName);
            if (config.hiddenMaterials.isEmpty()) return;

            int materialCount = ModelRuntimeBridgeHolder.get().getMaterialCount(modelHandle);

            for (int index : config.hiddenMaterials) {
                if (index >= 0 && index < materialCount) {
                    ModelRuntimeBridgeHolder.get().setMaterialVisible(modelHandle, index, false);
                }
            }
        } catch (Exception e) {
            logger.warn("恢复材质可见性失败: {}", modelName, e);
        }
    }

    public static void ReloadModel() {
        cancelAllPendingLoads();
        modelCache.clear(MMDModelManager::disposeModel);
        MaidMMDModelManager.invalidateLoadedModels();
        MMDTextureManager.clearPreloaded();
    }

    public static boolean isModelPending(String modelName, String cacheKey) {
        String fullCacheKey = modelName + "_" + cacheKey;
        return loadCoordinator.isPending(fullCacheKey);
    }

    public static boolean isAnyModelLoading() {
        return loadCoordinator.hasPendingLoads();
    }

    public static int getPendingLoadCount() {
        return loadCoordinator.getPendingLoadCount();
    }

    public static int getCachePendingReleaseCount() {
        return modelCache != null ? modelCache.pendingSize() : 0;
    }

    public static List<Model> getLoadedModels() {
        Set<Long> seen = new HashSet<>();
        List<Model> result = new ArrayList<>();
        if (modelCache != null) {
            modelCache.forEach((key, entry) -> {
                long handle = entry.value.model.getModelHandle();
                if (handle != 0 && seen.add(handle)) {
                    result.add(entry.value);
                }
            });
        }
        for (Model m : com.shiroha.mmdskin.maid.MaidMMDModelManager.getLoadedMaidModels()) {
            if (m != null && m.model != null) {
                long handle = m.model.getModelHandle();
                if (handle != 0 && seen.add(handle)) {
                    result.add(m);
                }
            }
        }
        return result;
    }

    private static void disposeModel(Model model) {
        try {
            MaidMMDModelManager.onModelDisposed(model);
            model.model.dispose();
            MMDAnimManager.DeleteModel(model.model);
            if (model.entityData != null) {
                model.entityData.dispose();
            }
        } catch (Exception e) {
            logger.error("删除模型失败", e);
        }
    }

    public static class Model {
        public IMMDModel model;
        public EntityAnimState entityData;
        String entityName;
        String modelName;

        public String getModelName() {
            return modelName;
        }
        public Properties properties = new Properties();
        boolean isPropertiesLoaded = false;

        public void loadModelProperties(boolean forceReload) {
            if (isPropertiesLoaded && !forceReload) {
                return;
            }

            ModelPropertiesLoader.load(modelName, properties);
            isPropertiesLoaded = true;
        }
    }

}
