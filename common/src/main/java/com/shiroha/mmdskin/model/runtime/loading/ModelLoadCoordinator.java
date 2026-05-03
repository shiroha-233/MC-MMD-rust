package com.shiroha.mmdskin.model.runtime.loading;

import com.shiroha.mmdskin.asset.catalog.ModelInfo;
import com.shiroha.mmdskin.model.port.ModelRuntimeAccessPort;
import com.shiroha.mmdskin.model.runtime.ManagedModel;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** 文件职责：协调模型异步加载、失败退避与预解码纹理预热。 */
public final class ModelLoadCoordinator {

    private static final Logger logger = LogManager.getLogger();

    private static final long FAILED_RETRY_INTERVAL_MS = 10_000L;

    private final ModelRuntimeAccessPort runtimeAccessPort;

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

    private static final class PendingLoad {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean completionClaimed = new AtomicBoolean();
        private FutureTask<AsyncLoadResult> future;

        Future<AsyncLoadResult> future() {
            return future;
        }

        void attach(FutureTask<AsyncLoadResult> future) {
            this.future = future;
        }

        boolean claimCompletion() {
            return completionClaimed.compareAndSet(false, true);
        }

        boolean isCancelled() {
            return cancelled.get();
        }

        void cancel() {
            cancelled.set(true);
            FutureTask<AsyncLoadResult> currentFuture = future;
            if (currentFuture != null) {
                currentFuture.cancel(true);
            }
        }
    }

    private final ExecutorService loadingExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "MMD-ModelLoader");
        thread.setDaemon(true);
        return thread;
    });

    private final ConcurrentHashMap<String, PendingLoad> pendingLoads = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> failedLoads = new ConcurrentHashMap<>();
    private final Set<String> missingModels = ConcurrentHashMap.newKeySet();

    public ModelLoadCoordinator(ModelRuntimeAccessPort runtimeAccessPort) {
        if (runtimeAccessPort == null) {
            throw new IllegalArgumentException("runtimeAccessPort cannot be null");
        }
        this.runtimeAccessPort = runtimeAccessPort;
    }

    public ManagedModel resolveOrQueue(String fullCacheKey,
                                       String modelName,
                                       Function<AsyncLoadResult, ManagedModel> finalizer) {
        PendingLoad pendingLoad = pendingLoads.get(fullCacheKey);
        if (pendingLoad != null) {
            Future<AsyncLoadResult> future = pendingLoad.future();
            if (!future.isDone()) {
                return null;
            }

            return finalizeCompletedLoad(fullCacheKey, pendingLoad, finalizer);
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
        pendingLoads.forEach((key, pendingLoad) -> {
            if (keyMatcher.test(key) && pendingLoads.remove(key, pendingLoad)) {
                cleanupPendingLoad(pendingLoad, resultCleaner);
            }
        });
        failedLoads.entrySet().removeIf(entry -> keyMatcher.test(entry.getKey()));
    }

    public void cancelAll(Consumer<AsyncLoadResult> resultCleaner) {
        if (pendingLoads.isEmpty()) {
            failedLoads.clear();
            return;
        }

        pendingLoads.forEach((key, pendingLoad) -> {
            if (pendingLoads.remove(key, pendingLoad)) {
                cleanupPendingLoad(pendingLoad, resultCleaner);
            }
        });
        failedLoads.clear();
    }

    private void startBackgroundLoad(String fullCacheKey, ModelInfo modelInfo, String modelName) {
        long startTime = System.currentTimeMillis();

        PendingLoad pendingLoad = new PendingLoad();
        FutureTask<AsyncLoadResult> future = new FutureTask<>(
                () -> loadModelHandle(fullCacheKey, modelInfo, modelName, startTime, pendingLoad));
        pendingLoad.attach(future);
        if (pendingLoads.putIfAbsent(fullCacheKey, pendingLoad) != null) {
            return;
        }

        logger.info("[异步加载] 开始后台加载模型 {} ({})", modelName, modelInfo.getModelFileName());
        try {
            loadingExecutor.execute(future);
        } catch (RuntimeException e) {
            pendingLoads.remove(fullCacheKey, pendingLoad);
            markFailed(fullCacheKey);
            logger.error("[异步加载] 提交后台任务失败: {}", modelName, e);
        }
    }

    private AsyncLoadResult loadModelHandle(String fullCacheKey,
                                            ModelInfo modelInfo,
                                            String modelName,
                                            long startTime,
                                            PendingLoad pendingLoad) {
        long handle = 0;
        try {
            handle = loadNativeModel(modelInfo);

            long elapsed = System.currentTimeMillis() - startTime;
            if (handle == 0) {
                logger.error("[异步加载] 后台加载失败 ({}ms): {}", elapsed, modelName);
                return null;
            }

            if (shouldDiscardLoadedHandle(fullCacheKey, pendingLoad)) {
                logger.info("[异步加载] 后台任务已被取消，释放句柄: {}", modelName);
                runtimeAccessPort.deleteModel(handle);
                return null;
            }

            logger.info("[异步加载] 模型解析完成 ({}ms)，开始预解码纹理: {}", elapsed, modelName);
            preloadModelTextures(handle, modelInfo.getFolderPath());

            if (shouldDiscardLoadedHandle(fullCacheKey, pendingLoad)) {
                logger.info("[异步加载] 后台任务已被取消（纹理预解码后），释放句柄: {}", modelName);
                runtimeAccessPort.deleteModel(handle);
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
                    runtimeAccessPort.deleteModel(handle);
                } catch (Exception cleanupError) {
                    logger.warn("[异步加载] 清理失败的模型句柄时发生异常: {} ({})", modelName, handle, cleanupError);
                }
            }
            return null;
        }
    }

    private long loadNativeModel(ModelInfo modelInfo) {
        if (modelInfo.isVRM()) {
            return runtimeAccessPort.loadVrmModel(modelInfo.getModelFilePath(), modelInfo.getFolderPath(), 3);
        }
        if (modelInfo.isPMD()) {
            return runtimeAccessPort.loadPmdModel(modelInfo.getModelFilePath(), modelInfo.getFolderPath(), 3);
        }
        return runtimeAccessPort.loadPmxModel(modelInfo.getModelFilePath(), modelInfo.getFolderPath(), 3);
    }

    private void preloadModelTextures(long modelHandle, String modelDir) {
        try {
            int materialCount = runtimeAccessPort.getMaterialCount(modelHandle);
            for (int i = 0; i < materialCount; i++) {
                String texturePath = runtimeAccessPort.getMaterialTexturePath(modelHandle, i);
                if (texturePath == null || texturePath.isEmpty()) {
                    continue;
                }
                runtimeAccessPort.preloadTexture(texturePath);
            }
            runtimeAccessPort.preloadTexture(modelDir + "/lightMap.png");
        } catch (Exception e) {
            logger.warn("[异步加载] 纹理预解码部分失败（不影响后续加载）", e);
        }
    }

    private ManagedModel finalizeCompletedLoad(String fullCacheKey,
                                               PendingLoad pendingLoad,
                                               Function<AsyncLoadResult, ManagedModel> finalizer) {
        Future<AsyncLoadResult> future = pendingLoad.future();
        if (!pendingLoad.claimCompletion()) {
            return null;
        }

        try {
            AsyncLoadResult result = future.get();
            if (result == null || result.modelHandle == 0L) {
                if (!pendingLoad.isCancelled()) {
                    logger.error("后台模型加载返回空句柄: {}", fullCacheKey);
                    markFailed(fullCacheKey);
                }
                return null;
            }
            if (pendingLoad.isCancelled()) {
                cleanupLoadedResult(result);
                return null;
            }

            ManagedModel model = finalizer.apply(result);
            if (model == null) {
                if (!pendingLoad.isCancelled()) {
                    markFailed(fullCacheKey);
                }
                return null;
            }
            if (pendingLoad.isCancelled()) {
                disposeManagedModel(model);
                return null;
            }
            return model;
        } catch (CancellationException ignored) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (!pendingLoad.isCancelled()) {
                logger.warn("获取后台加载结果时线程被中断: {}", fullCacheKey, e);
                markFailed(fullCacheKey);
            }
            return null;
        } catch (Exception e) {
            logger.error("获取后台加载结果失败: {}", fullCacheKey, e);
            if (!pendingLoad.isCancelled()) {
                markFailed(fullCacheKey);
            }
            return null;
        } finally {
            pendingLoads.remove(fullCacheKey, pendingLoad);
        }
    }

    private void cleanupPendingLoad(PendingLoad pendingLoad, Consumer<AsyncLoadResult> resultCleaner) {
        pendingLoad.cancel();
        Future<AsyncLoadResult> future = pendingLoad.future();
        if (!future.isDone() || !pendingLoad.claimCompletion()) {
            return;
        }

        try {
            AsyncLoadResult result = future.get();
            if (result != null) {
                resultCleaner.accept(result);
            }
        } catch (CancellationException ignored) {
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("[异步加载] 清理已完成任务结果时线程被中断", e);
        } catch (Exception e) {
            logger.warn("[异步加载] 清理已完成任务结果失败", e);
        }
    }

    private boolean shouldDiscardLoadedHandle(String fullCacheKey, PendingLoad pendingLoad) {
        return pendingLoad.isCancelled()
                || pendingLoads.get(fullCacheKey) != pendingLoad
                || Thread.currentThread().isInterrupted();
    }

    private void cleanupLoadedResult(AsyncLoadResult result) {
        if (result != null && result.modelHandle != 0L) {
            runtimeAccessPort.deleteModel(result.modelHandle);
        }
    }

    private void disposeManagedModel(ManagedModel model) {
        try {
            model.dispose();
        } catch (Exception e) {
            logger.warn("[异步加载] 取消后的模型实例清理失败: {}", model.modelName(), e);
        }
    }

    private void markFailed(String fullCacheKey) {
        failedLoads.put(fullCacheKey, System.currentTimeMillis());
    }
}
