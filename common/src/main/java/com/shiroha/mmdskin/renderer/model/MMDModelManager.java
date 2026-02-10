package com.shiroha.mmdskin.renderer.model;

import com.shiroha.mmdskin.MmdSkinClient;
import com.shiroha.mmdskin.NativeFunc;
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
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * MMD 模型管理器 (SRP - 单一职责原则)
 * 
 * 负责模型的加载和高层管理。
 * 
 * 职责拆分：
 * - 缓存管理：委托给 {@link ModelCache}
 * - 渲染模式：委托给 {@link RenderModeManager}
 * - 实体状态：使用 {@link EntityAnimState}
 * - 模型创建：委托给已注册的 {@link com.shiroha.mmdskin.renderer.core.IMMDModelFactory}
 */
public class MMDModelManager {
    static final Logger logger = LogManager.getLogger();
    
    private static ModelCache<Model> modelCache;

    // ===== 异步加载系统 =====

    /** 后台加载线程池（单线程，避免同时加载多个大模型占满内存） */
    private static final ExecutorService loadingExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MMD-ModelLoader");
        t.setDaemon(true);
        return t;
    });

    /** 后台加载结果 */
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

    /** 正在后台加载的任务：fullCacheKey -> Future */
    private static final ConcurrentHashMap<String, Future<AsyncLoadResult>> pendingLoads = new ConcurrentHashMap<>();

    /** 已标记失败的 key（防止反复提交失败的加载任务） */
    private static final ConcurrentHashMap<String, Long> failedLoads = new ConcurrentHashMap<>();
    private static final long FAILED_RETRY_INTERVAL_MS = 10_000; // 失败后 10 秒内不重试

    /** 加载代次：每次 cancel 时递增，后台任务完成后检查代次是否匹配，不匹配则自行清理句柄 */
    private static final AtomicLong loadGeneration = new AtomicLong(0);

    public static void Init() {
        // 先注册工厂，再初始化 RenderModeManager
        ModelFactoryRegistry.registerAll();
        
        modelCache = new ModelCache<>("MMDModel");
        RenderModeManager.init();
        logger.info("MMDModelManager 初始化完成（异步加载模式）");
    }

    /**
     * 获取模型（带缓存、异步加载和自动清理）
     *
     * 两阶段异步加载流程：
     * 1. 缓存命中 → 直接返回
     * 2. 缓存未命中 → 提交 Phase 1 到后台线程（nf.LoadModelPMX，最重的 Rust 计算）→ 返回 null
     * 3. 后台完成 → 下一帧检测到 Future 完成 → 执行 Phase 2（GL 资源创建，在渲染线程）→ 放入缓存
     */
    public static Model GetModel(String modelName, String cacheKey) {
        String fullCacheKey = modelName + "_" + cacheKey;
        
        // 1. 缓存命中
        ModelCache.CacheEntry<Model> entry = modelCache.get(fullCacheKey);
        if (entry != null) {
            return entry.value;
        }

        // 阴影 pass 期间不创建新模型，也不启动后台加载
        if (IrisCompat.isRenderingShadows()) {
            return null;
        }

        // 2. 检查是否有后台加载已完成
        Future<AsyncLoadResult> future = pendingLoads.get(fullCacheKey);
        if (future != null) {
            if (!future.isDone()) {
                return null; // 仍在加载中
            }

            // 后台加载完成，执行 Phase 2（渲染线程上的 GL 资源创建）
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

        // 3. 检查是否在冷却期内
        Long failedTime = failedLoads.get(fullCacheKey);
        if (failedTime != null) {
            if (System.currentTimeMillis() - failedTime < FAILED_RETRY_INTERVAL_MS) {
                return null; // 冷却期内不重试
            }
            failedLoads.remove(fullCacheKey);
        }

        // 4. 提交后台加载
        modelCache.checkAndClean(MMDModelManager::disposeModel);

        ModelInfo modelInfo = ModelInfo.findByFolderName(modelName);
        if (modelInfo == null) {
            logger.warn("模型未找到: " + modelName);
            return null;
        }

        startBackgroundLoad(fullCacheKey, modelInfo, modelName);
        return null;
    }

    /**
     * Phase 1：在后台线程执行 Rust 模型加载（最重的计算）
     */
    private static void startBackgroundLoad(String fullCacheKey, ModelInfo modelInfo, String modelName) {
        // 防止重复提交
        if (pendingLoads.containsKey(fullCacheKey)) {
            return;
        }
        logger.info("[异步加载] 开始后台加载模型: {} ({})", modelName, modelInfo.getModelFileName());
        long startTime = System.currentTimeMillis();
        final long myGeneration = loadGeneration.get();

        Future<AsyncLoadResult> future = loadingExecutor.submit(() -> {
            long handle = 0;
            try {
                NativeFunc nf = NativeFunc.GetInst();
                if (modelInfo.isPMD()) {
                    handle = nf.LoadModelPMD(modelInfo.getModelFilePath(), modelInfo.getFolderPath(), 3);
                } else {
                    handle = nf.LoadModelPMX(modelInfo.getModelFilePath(), modelInfo.getFolderPath(), 3);
                }

                if (handle == 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    logger.error("[异步加载] 后台加载失败 ({}ms): {}", elapsed, modelName);
                    return null;
                }

                // 检查是否已被取消（代次不匹配 = 加载期间发生了 cancel）
                if (loadGeneration.get() != myGeneration) {
                    logger.info("[异步加载] 后台任务已被取消，释放句柄: {}", modelName);
                    nf.DeleteModel(handle);
                    return null;
                }

                long elapsed = System.currentTimeMillis() - startTime;
                logger.info("[异步加载] 模型解析完成 ({}ms)，开始预解码纹理: {}", elapsed, modelName);

                // Phase 1.5：预解码所有材质纹理（Rust 解码图片，不涉及 GL）
                preloadModelTextures(nf, handle, modelInfo.getFolderPath());

                // 再次检查取消状态（纹理预解码可能耗时较长）
                if (loadGeneration.get() != myGeneration) {
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
                // 异常时清理已加载的句柄
                if (handle != 0) {
                    try { NativeFunc.GetInst().DeleteModel(handle); } catch (Exception ignored) {}
                }
                return null;
            }
        });

        pendingLoads.put(fullCacheKey, future);
    }

    /**
     * Phase 1.5：在后台线程预解码所有材质纹理（不涉及 GL，可在任意线程调用）
     */
    private static void preloadModelTextures(NativeFunc nf, long modelHandle, String modelDir) {
        try {
            int matCount = (int) nf.GetMaterialCount(modelHandle);
            int preloaded = 0;

            for (int i = 0; i < matCount; i++) {
                String texPath = nf.GetMaterialTex(modelHandle, i);
                if (texPath == null || texPath.isEmpty()) continue;

                // Rust loader 已将路径组合为绝对路径
                MMDTextureManager.preloadTexture(texPath);
                preloaded++;
            }

            // lightMap 也预解码
            MMDTextureManager.preloadTexture(modelDir + "/lightMap.png");

            if (preloaded > 0) {
                logger.info("[异步加载] 预解码 {} 个材质纹理", preloaded);
            }
        } catch (Exception e) {
            logger.warn("[异步加载] 纹理预解码部分失败（不影响后续加载）", e);
        }
    }
    
    /**
     * Phase 2：在渲染线程完成 GL 资源创建
     */
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

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("[异步加载] GL 资源创建完成 ({}ms): {} (缓存: {})", elapsed, fullCacheKey, modelCache.size());
            return model;
        } catch (Exception e) {
            logger.error("[异步加载] GL 资源创建异常: {}", fullCacheKey, e);
            // 异常时尝试释放后台加载的句柄，避免内存泄漏
            try {
                NativeFunc.GetInst().DeleteModel(result.modelHandle);
            } catch (Exception ex) {
                logger.error("释放模型句柄失败", ex);
            }
            markFailed(fullCacheKey);
            return null;
        }
    }
    
    /**
     * 标记加载失败（冷却期内不重试）
     */
    private static void markFailed(String fullCacheKey) {
        failedLoads.put(fullCacheKey, System.currentTimeMillis());
    }

    public static Model GetModel(String modelName) {
        return GetModel(modelName, "default");
    }
    
    /**
     * 记录模型切换事件，触发延迟清理
     */
    public static void onModelSwitch() {
        modelCache.onSwitch();
    }
    
    /**
     * 强制重载指定模型（立即清除缓存并重新加载）
     * 适用于模型切换时需要立即释放旧模型资源的场景
     * 
     * @param modelName 模型名称
     */
    public static void forceReloadModel(String modelName) {
        // 取消该模型的后台加载任务
        String prefix = modelName + "_";
        // 递增代次，让正在运行的后台任务自行清理
        loadGeneration.incrementAndGet();
        // 清理已完成但未消费的 Future 中的句柄
        pendingLoads.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(prefix)) {
                cleanupFutureHandle(entry.getValue());
                return true;
            }
            return false;
        });
        failedLoads.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
        MMDTextureManager.clearPreloaded();

        // 收集所有与该模型相关的缓存键
        java.util.List<String> keysToRemove = new java.util.ArrayList<>();
        modelCache.forEach((key, entry) -> {
            if (key.startsWith(prefix)) {
                keysToRemove.add(key);
            }
        });
        
        // 释放并移除
        for (String key : keysToRemove) {
            ModelCache.CacheEntry<Model> entry = modelCache.remove(key);
            if (entry != null) {
                disposeModel(entry.value);
                logger.info("强制释放模型: {}", key);
            }
        }
    }
    
    /**
     * 强制重载所有模型（立即清除所有缓存）
     * 适用于渲染模式切换（CPU/GPU）时需要完全重建所有模型的场景
     */
    public static void forceReloadAllModels() {
        // 取消所有正在进行的后台加载
        cancelAllPendingLoads();
        modelCache.clear(MMDModelManager::disposeModel);
        MaidMMDModelManager.invalidateLoadedModels();
        MMDTextureManager.clearPreloaded();
        logger.info("强制重载所有模型完成");
    }

    /**
     * 取消所有正在进行的后台加载任务
     */
    private static void cancelAllPendingLoads() {
        if (!pendingLoads.isEmpty()) {
            int count = pendingLoads.size();
            // 递增代次，让正在运行的后台任务完成后自行清理句柄
            loadGeneration.incrementAndGet();
            // 清理已完成但未消费的 Future 中的句柄
            for (var entry : pendingLoads.entrySet()) {
                cleanupFutureHandle(entry.getValue());
            }
            pendingLoads.clear();
            failedLoads.clear();
            MMDTextureManager.clearPreloaded();
            logger.info("已取消 {} 个后台加载任务", count);
        }
    }
    
    /**
     * 清理已完成 Future 中的模型句柄（防止原生内存泄漏）
     * 对于仍在运行的任务，loadGeneration 机制会让它们自行清理。
     */
    private static void cleanupFutureHandle(Future<AsyncLoadResult> future) {
        if (future.isDone() && !future.isCancelled()) {
            try {
                AsyncLoadResult result = future.get();
                if (result != null && result.modelHandle != 0) {
                    NativeFunc.GetInst().DeleteModel(result.modelHandle);
                    logger.info("[异步加载] 清理已完成但未消费的模型句柄: {}", result.modelName);
                }
            } catch (Exception ignored) {}
        }
    }
    
    /**
     * 定期检查，在渲染循环中调用
     */
    public static void tick() {
        modelCache.tick(MMDModelManager::disposeModel);
    }
    
    /**
     * 创建模型包装器
     */
    private static Model createModelWrapper(String name, IMMDModel model, String modelName) {
        ModelWithEntityData m = new ModelWithEntityData();
        m.entityName = name;
        m.model = model;
        m.modelName = modelName;
        m.entityData = new EntityAnimState(3);
        
        model.ResetPhysics();
        model.ChangeAnim(MMDAnimManager.GetAnimModel(model, "idle"), 0);
        return m;
    }
    
    public static void ReloadModel() {
        cancelAllPendingLoads();
        modelCache.clear(MMDModelManager::disposeModel);
        MaidMMDModelManager.invalidateLoadedModels();
        MMDTextureManager.clearPreloaded();
        logger.info("模型已重载");
    }
    
    /**
     * 查询是否有正在加载的模型
     */
    public static boolean isAnyModelLoading() {
        return !pendingLoads.isEmpty();
    }
    
    /**
     * 获取当前正在加载的模型数量
     */
    public static int getPendingLoadCount() {
        return pendingLoads.size();
    }

    /**
     * 释放模型资源
     */
    private static void disposeModel(Model model) {
        try {
            // 使用多态调用 dispose()，无需 instanceof 判断
            model.model.dispose();
            MMDAnimManager.DeleteModel(model.model);
            
            // 释放 EntityAnimState 资源
            if (model instanceof ModelWithEntityData med && med.entityData != null) {
                med.entityData.dispose();
            }
        } catch (Exception e) {
            logger.error("删除模型失败", e);
        }
    }
    
    public static class Model {
        public IMMDModel model;
        String entityName;
        String modelName;
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
            MmdSkinClient.reloadProperties = false;
        } 
    }

    public static class ModelWithEntityData extends Model {
        public EntityAnimState entityData;
    }
}
