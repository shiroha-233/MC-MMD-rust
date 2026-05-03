package com.shiroha.mmdskin.model.runtime;

import com.shiroha.mmdskin.config.ModelConfigData;
import com.shiroha.mmdskin.config.ModelConfigManager;
import com.shiroha.mmdskin.model.port.ModelDiagnosticsPort;
import com.shiroha.mmdskin.model.port.ModelRepositoryExtensionPort;
import com.shiroha.mmdskin.model.port.ModelRepositoryPort;
import com.shiroha.mmdskin.model.port.ModelRuntimeAccessPort;
import com.shiroha.mmdskin.model.runtime.cache.ModelCache;
import com.shiroha.mmdskin.model.runtime.loading.ModelLoadCoordinator;
import com.shiroha.mmdskin.model.runtime.loading.ModelPropertiesLoader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** 文件职责：统一管理模型实例的异步加载、缓存、重载与诊断。 */
public final class ModelRepository implements ModelRepositoryPort, ModelDiagnosticsPort {
    private static final Logger logger = LogManager.getLogger();

    private final ModelRuntimeAccessPort runtimeAccessPort;
    private final ModelRepositoryExtensionPort extensionPort;
    private final ModelCache<ManagedModel> modelCache = new ModelCache<>("MMDModel");
    private final ModelLoadCoordinator loadCoordinator;
    private final AtomicInteger totalModelsLoaded = new AtomicInteger();

    public ModelRepository(ModelRuntimeAccessPort runtimeAccessPort) {
        this(runtimeAccessPort, new ModelRepositoryExtensionPort() {
        });
    }

    public ModelRepository(ModelRuntimeAccessPort runtimeAccessPort,
                           ModelRepositoryExtensionPort extensionPort) {
        if (runtimeAccessPort == null) {
            throw new IllegalArgumentException("runtimeAccessPort cannot be null");
        }
        this.runtimeAccessPort = runtimeAccessPort;
        this.extensionPort = extensionPort != null ? extensionPort : new ModelRepositoryExtensionPort() {
        };
        this.loadCoordinator = new ModelLoadCoordinator(runtimeAccessPort);
    }

    @Override
    public ManagedModel acquire(ModelRequestKey requestKey) {
        if (requestKey == null || "unknown".equals(requestKey.modelName())) {
            return null;
        }

        ModelCache.CacheEntry<ManagedModel> cached = modelCache.get(requestKey.cacheKey());
        if (cached != null) {
            return cached.value;
        }

        if (runtimeAccessPort.isRenderingShadows()) {
            return null;
        }

        ManagedModel loadedModel = loadCoordinator.resolveOrQueue(
                requestKey.cacheKey(),
                requestKey.modelName(),
                result -> finalizeLoadedModel(requestKey, result));
        if (loadedModel != null) {
            modelCache.put(requestKey.cacheKey(), loadedModel);
            totalModelsLoaded.incrementAndGet();
        }
        return loadedModel;
    }

    @Override
    public boolean isPending(ModelRequestKey requestKey) {
        return requestKey != null && loadCoordinator.isPending(requestKey.cacheKey());
    }

    @Override
    public void reloadModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return;
        }
        loadCoordinator.removeMatching(key -> key.endsWith(":" + modelName), this::cleanupLoadedResult);
        runtimeAccessPort.clearPreloadedTextures();
        modelCache.removeMatching(key -> key.endsWith(":" + modelName), this::disposeModel);
    }

    @Override
    public void reloadSubject(ModelRequestKey requestKey) {
        if (requestKey == null) {
            return;
        }
        loadCoordinator.removeMatching(key -> key.equals(requestKey.cacheKey()), this::cleanupLoadedResult);
        modelCache.removeMatching(key -> key.equals(requestKey.cacheKey()), this::disposeModel);
    }

    @Override
    public void reloadAll() {
        loadCoordinator.cancelAll(this::cleanupLoadedResult);
        modelCache.clear(this::disposeModel);
        extensionPort.onRepositoryReloadAll();
        runtimeAccessPort.clearPreloadedTextures();
    }

    @Override
    public void tick() {
        modelCache.tick(this::disposeModel);
        runtimeAccessPort.tickTextures();
    }

    @Override
    public List<ManagedModel> loadedModels() {
        Set<Long> seenHandles = new HashSet<>();
        List<ManagedModel> result = new ArrayList<>();
        modelCache.forEach((key, entry) -> collectModel(entry.value, seenHandles, result));
        try {
            for (ManagedModel externalModel : extensionPort.additionalLoadedModels()) {
                collectModel(externalModel, seenHandles, result);
            }
        } catch (Exception e) {
            logger.warn("Failed to collect external managed models", e);
        }
        return result;
    }

    @Override
    public int totalModelsLoaded() {
        return totalModelsLoaded.get();
    }

    @Override
    public int pendingReleaseCount() {
        return modelCache.pendingSize();
    }

    private ManagedModel finalizeLoadedModel(ModelRequestKey requestKey, ModelLoadCoordinator.AsyncLoadResult result) {
        try {
            ModelInstance modelInstance = runtimeAccessPort.createModelFromHandle(
                    result.modelHandle,
                    result.modelInfo.getFolderPath(),
                    result.modelInfo.isPMD());
            if (modelInstance == null) {
                cleanupLoadedResult(result);
                return null;
            }

            ManagedModel managedModel = createManagedModel(requestKey, modelInstance);
            return managedModel;
        } catch (Exception e) {
            logger.error("Failed to finalize model: {}", requestKey, e);
            cleanupLoadedResult(result);
            return null;
        }
    }

    private ManagedModel createManagedModel(ModelRequestKey requestKey, ModelInstance modelInstance) {
        Properties properties = loadProperties(requestKey.modelName());
        ManagedModel managedModel = new ManagedModel(
                requestKey,
                requestKey.modelName(),
                modelInstance,
                properties,
                ModelRenderProperties.from(properties));
        modelInstance.resetPhysics();
        modelInstance.changeAnim(managedModel.animationLibrary().animation("idle"), 0);
        applyMaterialVisibility(modelInstance.getModelHandle(), requestKey.modelName());
        return managedModel;
    }

    private Properties loadProperties(String modelName) {
        Properties properties = new Properties();
        ModelPropertiesLoader.load(modelName, properties);
        return properties;
    }

    private void applyMaterialVisibility(long modelHandle, String modelName) {
        try {
            ModelConfigData config = ModelConfigManager.getConfig(modelName);
            if (config.hiddenMaterials.isEmpty()) {
                return;
            }
            int materialCount = runtimeAccessPort.getMaterialCount(modelHandle);
            for (int index : config.hiddenMaterials) {
                if (index >= 0 && index < materialCount) {
                    runtimeAccessPort.setMaterialVisible(modelHandle, index, false);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to apply material visibility for {}", modelName, e);
        }
    }

    private void collectModel(ManagedModel model, Set<Long> seenHandles, List<ManagedModel> target) {
        if (model == null || model.modelInstance() == null) {
            return;
        }
        long handle = model.modelInstance().getModelHandle();
        if (handle != 0 && seenHandles.add(handle)) {
            target.add(model);
        }
    }

    private void disposeModel(ManagedModel model) {
        try {
            extensionPort.onManagedModelDisposed(model);
        } catch (Exception e) {
            logger.warn("Failed to notify model repository extension for {}", model != null ? model.modelName() : "unknown", e);
        }
        try {
            model.dispose();
        } catch (Exception e) {
            logger.error("Failed to dispose model {}", model != null ? model.modelName() : "unknown", e);
        }
    }

    private void cleanupLoadedResult(ModelLoadCoordinator.AsyncLoadResult result) {
        if (result != null && result.modelHandle != 0L) {
            runtimeAccessPort.deleteModel(result.modelHandle);
        }
    }
}
