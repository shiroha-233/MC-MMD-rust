package com.shiroha.mmdskin.renderer.runtime.model.loading;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.asset.catalog.ModelInfo;
import com.shiroha.mmdskin.renderer.runtime.texture.MMDTextureManager;
import com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 模型后台加载协调器。
 */
public final class ModelLoadCoordinator {

    private static final Logger logger = LogManager.getLogger();

    private static final long FAILED_RETRY_INTERVAL_MS = 10_000L;

    public static final class AsyncLoadResult {
        public final long modelHandle;
        public final ModelInfo modelInfo;
        public final String modelName;

        AsyncLoadResult(long modelHandle, ModelInfo modelInfo, String modelName) {
            this.modelHandle = modelHandle;
            this.modelInfo = modelInfo;
            this.modelName = modelName;
        }
    }

    private final ExecutorService loadingExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "MMD-ModelLoader");
        thread.setDaemon(true);
        return thread;
    });

    private final ConcurrentHashMap<String, Future<AsyncLoadResult>> pendingLoads = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> failedLoads = new ConcurrentHashMap<>();
    private final Set<String> missingModels = ConcurrentHashMap.newKeySet();

    public MMDModelManager.Model resolveOrQueue(String fullCacheKey,
                                                String modelName,
                                                Function<AsyncLoadResult, MMDModelManager.Model> finalizer) {
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

                MMDModelManager.Model model = finalizer.apply(result);
                if (model == null) {
                    markFailed(fullCacheKey);
                }
                return model;
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

    public boolean isPending(String fullCacheKey) {
        return pendingLoads.containsKey(fullCacheKey);
    }

    public boolean hasPendingLoads() {
        return !pendingLoads.isEmpty();
    }

    public int getPendingLoadCount() {
        return pendingLoads.size();
    }

    public void removeMatching(Predicate<String> keyMatcher, Consumer<AsyncLoadResult> resultCleaner) {
        pendingLoads.entrySet().removeIf(entry -> {
            if (!keyMatcher.test(entry.getKey())) {
                return false;
            }
            cleanupFutureResult(entry.getValue(), resultCleaner);
            return true;
        });
        failedLoads.entrySet().removeIf(entry -> keyMatcher.test(entry.getKey()));
    }

    public void cancelAll(Consumer<AsyncLoadResult> resultCleaner) {
        if (pendingLoads.isEmpty()) {
            failedLoads.clear();
            return;
        }

        for (Future<AsyncLoadResult> future : pendingLoads.values()) {
            cleanupFutureResult(future, resultCleaner);
        }
        pendingLoads.clear();
        failedLoads.clear();
    }

    private void startBackgroundLoad(String fullCacheKey, ModelInfo modelInfo, String modelName) {
        if (pendingLoads.containsKey(fullCacheKey)) {
            return;
        }

        logger.info("[异步加载] 开始后台加载模型: {} ({})", modelName, modelInfo.getModelFileName());
        long startTime = System.currentTimeMillis();

        Future<AsyncLoadResult> future = loadingExecutor.submit(() -> loadModelHandle(fullCacheKey, modelInfo, modelName, startTime));
        if (pendingLoads.putIfAbsent(fullCacheKey, future) != null) {
            future.cancel(false);
        }
    }

    private AsyncLoadResult loadModelHandle(String fullCacheKey,
                                            ModelInfo modelInfo,
                                            String modelName,
                                            long startTime) {
        long handle = 0;
        try {
            NativeFunc nativeFunc = NativeFunc.GetInst();
            if (modelInfo.isVRM()) {
                handle = nativeFunc.LoadModelVRM(modelInfo.getModelFilePath(), modelInfo.getFolderPath(), 3);
            } else if (modelInfo.isPMD()) {
                handle = nativeFunc.LoadModelPMD(modelInfo.getModelFilePath(), modelInfo.getFolderPath(), 3);
            } else {
                handle = nativeFunc.LoadModelPMX(modelInfo.getModelFilePath(), modelInfo.getFolderPath(), 3);
            }

            long elapsed = System.currentTimeMillis() - startTime;
            if (handle == 0) {
                logger.error("[异步加载] 后台加载失败 ({}ms): {}", elapsed, modelName);
                return null;
            }

            if (!pendingLoads.containsKey(fullCacheKey) || Thread.interrupted()) {
                logger.info("[异步加载] 后台任务已被取消，释放句柄: {}", modelName);
                nativeFunc.DeleteModel(handle);
                return null;
            }

            logger.info("[异步加载] 模型解析完成 ({}ms)，开始预解码纹理: {}", elapsed, modelName);
            preloadModelTextures(nativeFunc, handle, modelInfo.getFolderPath());

            if (!pendingLoads.containsKey(fullCacheKey) || Thread.interrupted()) {
                logger.info("[异步加载] 后台任务已被取消（纹理预解码后），释放句柄: {}", modelName);
                nativeFunc.DeleteModel(handle);
                return null;
            }

            long totalElapsed = System.currentTimeMillis() - startTime;
            logger.info("[异步加载] 后台加载全部完成 ({}ms): {}", totalElapsed, modelName);
            return new AsyncLoadResult(handle, modelInfo, modelName);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            logger.error("[异步加载] 后台加载异常 ({}ms): {}", elapsed, modelName, e);
            if (handle != 0) {
                try {
                    NativeFunc.GetInst().DeleteModel(handle);
                } catch (Exception ignored) {
                }
            }
            return null;
        }
    }

    private void preloadModelTextures(NativeFunc nativeFunc, long modelHandle, String modelDir) {
        try {
            int materialCount = (int) nativeFunc.GetMaterialCount(modelHandle);
            for (int i = 0; i < materialCount; i++) {
                String texturePath = nativeFunc.GetMaterialTex(modelHandle, i);
                if (texturePath == null || texturePath.isEmpty()) {
                    continue;
                }
                MMDTextureManager.preloadTexture(texturePath);
            }

            MMDTextureManager.preloadTexture(modelDir + "/lightMap.png");
        } catch (Exception e) {
            logger.warn("[异步加载] 纹理预解码部分失败（不影响后续加载）", e);
        }
    }

    private void cleanupFutureResult(Future<AsyncLoadResult> future, Consumer<AsyncLoadResult> resultCleaner) {
        if (future.isDone()) {
            if (!future.isCancelled()) {
                try {
                    AsyncLoadResult result = future.get();
                    if (result != null) {
                        resultCleaner.accept(result);
                    }
                } catch (Exception ignored) {
                }
            }
            return;
        }

        future.cancel(true);
    }

    private void markFailed(String fullCacheKey) {
        failedLoads.put(fullCacheKey, System.currentTimeMillis());
    }
}
