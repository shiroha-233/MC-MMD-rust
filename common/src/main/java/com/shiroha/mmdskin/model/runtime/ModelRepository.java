package com.shiroha.mmdskin.model.runtime;

import com.shiroha.mmdskin.compat.iris.IrisCompat;
import com.shiroha.mmdskin.config.ModelConfigData;
import com.shiroha.mmdskin.config.ModelConfigManager;
import com.shiroha.mmdskin.model.port.ModelDiagnosticsPort;
import com.shiroha.mmdskin.model.port.ModelRepositoryExtensionPort;
import com.shiroha.mmdskin.model.port.ModelRepositoryPort;
import com.shiroha.mmdskin.model.runtime.cache.ModelCache;
import com.shiroha.mmdskin.model.runtime.loading.ModelLoadCoordinator;
import com.shiroha.mmdskin.model.runtime.loading.ModelPropertiesLoader;
import com.shiroha.mmdskin.render.backend.RenderBackendRegistry;
import com.shiroha.mmdskin.bridge.runtime.NativeRuntimeBridgeHolder;
import com.shiroha.mmdskin.texture.runtime.TextureRepository;
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

    private final RenderBackendRegistry renderBackendRegistry;
    private final ModelRepositoryExtensionPort extensionPort;
    private final ModelCache<ManagedModel> modelCache = new ModelCache<>("MMDModel");
    private final ModelLoadCoordinator loadCoordinator = new ModelLoadCoordinator();
    private final AtomicInteger totalModelsLoaded = new AtomicInteger();

    public ModelRepository(RenderBackendRegistry renderBackendRegistry) {
        this(renderBackendRegistry, new ModelRepositoryExtensionPort() {
        });
    }

    public ModelRepository(RenderBackendRegistry renderBackendRegistry,
                           ModelRepositoryExtensionPort extensionPort) {
        this.renderBackendRegistry = renderBackendRegistry;
        this.extensionPort = extensionPort != null ? extensionPort : new ModelRepositoryExtensionPort() {
        };
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

        if (IrisCompat.isRenderingShadows()) {
            return null;
        }

        return loadCoordinator.resolveOrQueue(requestKey.cacheKey(), requestKey.modelName(),
                result -> finalizeLoadedModel(requestKey, result));
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
        loadCoordinator.removeMatching(key -> key.endsWith(":" + modelName), ModelRepository::cleanupLoadedResult);
        TextureRepository.clearPreloaded();
        modelCache.removeMatching(key -> key.endsWith(":" + modelName), this::disposeModel);
    }

    @Override
    public void reloadSubject(ModelRequestKey requestKey) {
        if (requestKey == null) {
            return;
        }
        loadCoordinator.removeMatching(key -> key.equals(requestKey.cacheKey()), ModelRepository::cleanupLoadedResult);
        modelCache.removeMatching(key -> key.equals(requestKey.cacheKey()), this::disposeModel);
    }

    @Override
    public void reloadAll() {
        loadCoordinator.cancelAll(ModelRepository::cleanupLoadedResult);
        modelCache.clear(this::disposeModel);
        extensionPort.onRepositoryReloadAll();
        TextureRepository.clearPreloaded();
    }

    @Override
    public void tick() {
        modelCache.tick(this::disposeModel);
        TextureRepository.tick();
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
            ModelInstance modelInstance = renderBackendRegistry.createModelFromHandle(
                    result.modelHandle,
                    result.modelInfo.getFolderPath(),
                    result.modelInfo.isPMD());
            if (modelInstance == null) {
                cleanupLoadedResult(result);
                return null;
            }

            ManagedModel managedModel = createManagedModel(requestKey, modelInstance);
            modelCache.put(requestKey.cacheKey(), managedModel);
            totalModelsLoaded.incrementAndGet();
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
            int materialCount = NativeRuntimeBridgeHolder.get().getMaterialCount(modelHandle);
            for (int index : config.hiddenMaterials) {
                if (index >= 0 && index < materialCount) {
                    NativeRuntimeBridgeHolder.get().setMaterialVisible(modelHandle, index, false);
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

    private static void cleanupLoadedResult(ModelLoadCoordinator.AsyncLoadResult result) {
        if (result != null && result.modelHandle != 0L) {
            NativeRuntimeBridgeHolder.get().deleteModel(result.modelHandle);
        }
    }
}
