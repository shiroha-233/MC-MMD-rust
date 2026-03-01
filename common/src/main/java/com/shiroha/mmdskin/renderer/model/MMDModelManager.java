package com.shiroha.mmdskin.renderer.model;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.config.ModelConfigData;
import com.shiroha.mmdskin.config.ModelConfigManager;
import com.shiroha.mmdskin.renderer.animation.MMDAnimManager;
import com.shiroha.mmdskin.renderer.core.EntityAnimState;
import com.shiroha.mmdskin.renderer.core.IMMDModel;
import com.shiroha.mmdskin.renderer.core.IrisCompat;
import com.shiroha.mmdskin.renderer.core.ModelCache;
import com.shiroha.mmdskin.renderer.core.RenderModeManager;
import com.shiroha.mmdskin.renderer.model.factory.ModelFactoryRegistry;
import com.shiroha.mmdskin.renderer.resource.MMDTextureManager;
import com.shiroha.mmdskin.maid.MaidMMDModelManager;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * MMD 模型管理器：模型加载生命周期管理，两阶段异步加载（后台 Rust 解析 + 渲染线程 GL 创建）
 */
public class MMDModelManager {
    static final Logger logger = LogManager.getLogger();
    
    private static ModelCache<Model> modelCache;
    
    /** 单线程加载，避免多个大模型同时占满内存 */
    private static final ExecutorService loadingExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MMD-ModelLoader");
        t.setDaemon(true);
        return t;
    });
    
    static class AsyncLoadResult {
        final long modelHandle;
        final ModelInfo modelInfo;
        final String modelName;
        
        AsyncLoadResult(long modelHandle, ModelInfo modelInfo, String modelName) {
            this.modelHandle = modelHandle;
            this.modelInfo = modelInfo;
            this.modelName = modelName;
        }
    }
    
    private static final ConcurrentHashMap<String, Future<AsyncLoadResult>> pendingLoads = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> failedLoads = new ConcurrentHashMap<>();
    private static final long FAILED_RETRY_INTERVAL_MS = 10_000;
    private static final Set<String> missingModels = ConcurrentHashMap.newKeySet();
    
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
        
        Future<AsyncLoadResult> future = pendingLoads.get(fullCacheKey);
        if (future != null) {
            if (!future.isDone()) {
                return null;
            }
            
            pendingLoads.remove(fullCacheKey);
            try {
                AsyncLoadResult result = future.get();
                if (result == null || result.modelHandle == 0) {
                    logger.error("后台模型加载返回空句柄: {}", fullCacheKey);
                    markFailed(fullCacheKey);
                    return null;
                }
                
                return finalizeModelOnRenderThread(fullCacheKey, result);
            } catch (Exception e) {
                logger.error("获取后台加载结果失败: {}", fullCacheKey, e);
                markFailed(fullCacheKey);
                return null;
            }
        }
        
        Long failedTime = failedLoads.get(fullCacheKey);
        if (failedTime != null && (System.currentTimeMillis() - failedTime) < FAILED_RETRY_INTERVAL_MS) {
            return null;
        }
        failedLoads.remove(fullCacheKey);
        
        ModelInfo modelInfo = ModelInfo.findByFolderName(modelName);
        if (modelInfo == null) {
            if (missingModels.add(modelName)) {
                logger.warn("模型本地不存在，跳过加载: {}", modelName);
            }
            return null;
        }
        
        startBackgroundLoad(fullCacheKey, modelInfo, modelName);
        return null;
    }
    
    private static void startBackgroundLoad(String fullCacheKey, ModelInfo modelInfo, String modelName) {
        if (pendingLoads.containsKey(fullCacheKey)) {
            return;
        }
        
        logger.info("[异步加载] 开始后台加载模型: {} ({})", modelName, modelInfo.getModelFileName());
        long startTime = System.currentTimeMillis();
        
        Future<AsyncLoadResult> future = loadingExecutor.submit(() -> {
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
                
                long elapsed = System.currentTimeMillis() - startTime;
                if (handle == 0) {
                    logger.error("[异步加载] 后台加载失败 ({}ms): {}", elapsed, modelName);
                    return null;
                }
                
                if (!pendingLoads.containsKey(fullCacheKey) || Thread.interrupted()) {
                    logger.info("[异步加载] 后台任务已被取消，释放句柄: {}", modelName);
                    nf.DeleteModel(handle);
                    return null;
                }
                
                logger.info("[异步加载] 模型解析完成 ({}ms)，开始预解码纹理: {}", elapsed, modelName);
                
                preloadModelTextures(nf, handle, modelInfo.getFolderPath());
                
                if (!pendingLoads.containsKey(fullCacheKey) || Thread.interrupted()) {
                    logger.info("[异步加载] 后台任务已被取消（纹理预解码后），释放句柄: {}", modelName);
                    nf.DeleteModel(handle);
                    return null;
                }
                
                long totalElapsed = System.currentTimeMillis() - startTime;
                logger.info("[异步加载] 后台加载全部完成 ({}ms): {}", totalElapsed, modelName);
                return new AsyncLoadResult(handle, modelInfo, modelName);
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - startTime;
                logger.error("[异步加载] 后台加载异常 ({}ms): {}", elapsed, modelName, e);
                if (handle != 0) {
                    try { NativeFunc.GetInst().DeleteModel(handle); } catch (Exception ignored) {}
                }
                return null;
            }
        });
        
        if (pendingLoads.putIfAbsent(fullCacheKey, future) != null) {
            future.cancel(false);
        }
    }
    
    /** 后台预解码纹理（不涉及 GL） */
    private static void preloadModelTextures(NativeFunc nf, long modelHandle, String modelDir) {
        try {
            int matCount = (int) nf.GetMaterialCount(modelHandle);
            int preloaded = 0;
            
            for (int i = 0; i < matCount; i++) {
                String texPath = nf.GetMaterialTex(modelHandle, i);
                if (texPath == null || texPath.isEmpty()) continue;
                
                MMDTextureManager.preloadTexture(texPath);
                preloaded++;
            }
            
            MMDTextureManager.preloadTexture(modelDir + "/lightMap.png");
        } catch (Exception e) {
            logger.warn("[异步加载] 纹理预解码部分失败（不影响后续加载）", e);
        }
    }
    
    /** 渲染线程完成 GL 资源创建 */
    private static Model finalizeModelOnRenderThread(String fullCacheKey, AsyncLoadResult result) {
        long startTime = System.currentTimeMillis();
        
        try {
            IMMDModel m = RenderModeManager.createModelFromHandle(
                result.modelHandle, result.modelInfo.getFolderPath(), result.modelInfo.isPMD());
            
            if (m == null) {
                logger.error("[异步加载] GL 资源创建失败，释放模型句柄: {}", result.modelName);
                NativeFunc.GetInst().DeleteModel(result.modelHandle);
                markFailed(fullCacheKey);
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
                NativeFunc.GetInst().DeleteModel(result.modelHandle);
            } catch (Exception ex) {
                logger.error("释放模型句柄失败", ex);
            }
            markFailed(fullCacheKey);
            return null;
        }
    }
    
    private static void markFailed(String fullCacheKey) {
        failedLoads.put(fullCacheKey, System.currentTimeMillis());
    }

    public static Model GetModel(String modelName) {
        return GetModel(modelName, "default");
    }
    
    public static void forceReloadModel(String modelName) {
        String prefix = modelName + "_";
        pendingLoads.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(prefix)) {
                cleanupFutureHandle(entry.getValue());
                return true;
            }
            return false;
        });
        failedLoads.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
        MMDTextureManager.clearPreloaded();
        modelCache.removeMatching(key -> key.startsWith(prefix), MMDModelManager::disposeModel);
    }
    
    public static void forceReloadPlayerModels(String playerCacheKey) {
        String suffix = "_" + playerCacheKey;
        pendingLoads.entrySet().removeIf(entry -> {
            if (entry.getKey().endsWith(suffix)) {
                cleanupFutureHandle(entry.getValue());
                return true;
            }
            return false;
        });
        failedLoads.entrySet().removeIf(entry -> entry.getKey().endsWith(suffix));
        modelCache.removeMatching(key -> key.endsWith(suffix), MMDModelManager::disposeModel);
    }
    
    public static void forceReloadAllModels() {
        cancelAllPendingLoads();
        modelCache.clear(MMDModelManager::disposeModel);
        MaidMMDModelManager.invalidateLoadedModels();
        MMDTextureManager.clearPreloaded();
    }
    
    private static void cancelAllPendingLoads() {
        if (!pendingLoads.isEmpty()) {
            for (var entry : pendingLoads.entrySet()) {
                cleanupFutureHandle(entry.getValue());
            }
            pendingLoads.clear();
            failedLoads.clear();
            MMDTextureManager.clearPreloaded();
        }
    }
    
    private static void cleanupFutureHandle(Future<AsyncLoadResult> future) {
        if (future.isDone()) {
            if (!future.isCancelled()) {
                try {
                    AsyncLoadResult result = future.get();
                    if (result != null && result.modelHandle != 0) {
                        NativeFunc.GetInst().DeleteModel(result.modelHandle);
                    }
                } catch (Exception ignored) {}
            }
        } else {
            future.cancel(true);
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
            
            NativeFunc nf = NativeFunc.GetInst();
            int materialCount = (int) nf.GetMaterialCount(modelHandle);
            
            for (int index : config.hiddenMaterials) {
                if (index >= 0 && index < materialCount) {
                    nf.SetMaterialVisible(modelHandle, index, false);
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
        return pendingLoads.containsKey(fullCacheKey);
    }
    
    public static boolean isAnyModelLoading() {
        return !pendingLoads.isEmpty();
    }
    
    public static int getPendingLoadCount() {
        return pendingLoads.size();
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
            
            ModelInfo info = ModelInfo.findByFolderName(modelName);
            if (info == null) {
                logger.warn("模型属性加载失败，模型未找到: {}", modelName);
                isPropertiesLoaded = true;
                return;
            }
            
            String path2Properties = info.getFolderPath() + "/model.properties";
            try (InputStream istream = new FileInputStream(path2Properties)) {
                properties.load(istream);
                logger.debug("模型属性加载成功: {}", modelName);
            } catch (IOException e) {
                logger.debug("模型属性文件未找到: {}", modelName);
            }
            isPropertiesLoaded = true;
        } 
    }

}
