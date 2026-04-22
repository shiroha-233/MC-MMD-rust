package com.shiroha.mmdskin.render.backend.mode;

import com.shiroha.mmdskin.asset.catalog.ModelInfo;
import com.shiroha.mmdskin.model.runtime.ModelInstance;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 渲染模式管理器。
 */
public class RenderModeManager {
    private static final Logger logger = LogManager.getLogger();

    private static final List<ModelInstanceFactory> factories = new CopyOnWriteArrayList<>();

    private static final Map<RenderCategory, Boolean> enabledStates = new ConcurrentHashMap<>();

    private static volatile boolean initialized = false;

    public static synchronized void registerFactory(ModelInstanceFactory factory) {
        if (factory == null) return;

        for (ModelInstanceFactory existing : factories) {
            if (existing.getCategory() == factory.getCategory()) {
                logger.warn("渲染分类 {} 已存在，跳过注册", factory.getCategory());
                return;
            }
        }

        factories.add(factory);
        enabledStates.putIfAbsent(factory.getCategory(), factory.isEnabledByDefault());
    }

    public static void unregisterFactory(RenderCategory category) {
        factories.removeIf(f -> f.getCategory() == category);
        enabledStates.remove(category);
    }

    public static void init() {
        if (initialized) return;

        syncFactoryStates();
        initialized = true;
    }

    private static void syncFactoryStates() {
        for (ModelInstanceFactory factory : factories) {
            enabledStates.put(factory.getCategory(), factory.isEnabledInCurrentEnvironment());
        }
    }

    public static boolean isEnabled(RenderCategory category) {
        return Boolean.TRUE.equals(enabledStates.get(category));
    }

    public static void setEnabled(RenderCategory category, boolean enabled) {
        enabledStates.put(category, enabled);
    }

    public static void setUseGpuSkinning(boolean enabled) {
        setEnabled(RenderCategory.GPU_SKINNING, enabled);
    }

    public static boolean isUseGpuSkinning() {
        return isEnabled(RenderCategory.GPU_SKINNING);
    }

    public static String getCurrentModeDescription() {
        List<ModelInstanceFactory> candidates = getOrderedFactories(false, false);
        if (!candidates.isEmpty()) {
            return candidates.get(0).getModeName();
        }
        return "无可用渲染模式";
    }

    private static List<ModelInstanceFactory> getOrderedFactories(boolean isPMD, boolean includeDisabled) {
        List<ModelInstanceFactory> result = new ArrayList<>();

        List<ModelInstanceFactory> sorted = new ArrayList<>(factories);
        sorted.sort(Comparator.comparingInt(ModelInstanceFactory::getPriority).reversed());

        for (ModelInstanceFactory factory : sorted) {
            if (!factory.isAvailable()) continue;
            if (isPMD && !factory.supportsPMD()) continue;
            if (!includeDisabled && !isEnabled(factory.getCategory())) continue;
            result.add(factory);
        }
        return result;
    }

    public static ModelInstance createModel(String modelFilename, String modelDir, boolean isPMD, long layerCount) {
        syncFactoryStates();

        List<ModelInstanceFactory> enabled = getOrderedFactories(isPMD, false);
        ModelInstance model = tryCreateWithFactories(enabled, modelFilename, modelDir, isPMD, layerCount);
        if (model != null) return model;

        List<ModelInstanceFactory> all = getOrderedFactories(isPMD, true);
        all.removeAll(enabled);
        model = tryCreateWithFactories(all, modelFilename, modelDir, isPMD, layerCount);
        if (model != null) return model;

        logger.error("所有工厂都无法创建模型: {}", modelFilename);
        return null;
    }

    public static ModelInstance createModel(ModelInfo modelInfo, long layerCount) {
        if (modelInfo == null) return null;
        return createModel(
            modelInfo.getModelFilePath(), modelInfo.getFolderPath(),
            modelInfo.isPMD(), layerCount);
    }

    public static ModelInstance createModelFromHandle(long modelHandle, String modelDir, boolean isPMD) {
        syncFactoryStates();

        List<ModelInstanceFactory> enabled = getOrderedFactories(isPMD, false);
        ModelInstance model = tryCreateFromHandle(enabled, modelHandle, modelDir);
        if (model != null) return model;

        List<ModelInstanceFactory> all = getOrderedFactories(isPMD, true);
        all.removeAll(enabled);
        model = tryCreateFromHandle(all, modelHandle, modelDir);
        if (model != null) return model;

        logger.error("所有工厂都无法从句柄创建模型");
        return null;
    }

    private static ModelInstance tryCreateWithFactories(List<ModelInstanceFactory> candidates,
            String modelFilename, String modelDir, boolean isPMD, long layerCount) {
        for (ModelInstanceFactory factory : candidates) {
            try {
                ModelInstance model = factory.createModel(modelFilename, modelDir, isPMD, layerCount);
                if (model != null) return model;
                logger.warn("{} 创建失败，尝试下一个工厂", factory.getModeName());
            } catch (Exception e) {
                logger.error("{} 创建异常: {}", factory.getModeName(), e.getMessage());
            }
        }
        return null;
    }

    private static ModelInstance tryCreateFromHandle(List<ModelInstanceFactory> candidates,
            long modelHandle, String modelDir) {
        for (ModelInstanceFactory factory : candidates) {
            try {
                ModelInstance model = factory.createModelFromHandle(modelHandle, modelDir);
                if (model != null) return model;
                logger.warn("{} 从句柄创建失败，尝试下一个工厂", factory.getModeName());
            } catch (Exception e) {
                logger.error("{} 从句柄创建异常: {}", factory.getModeName(), e.getMessage());
            }
        }
        return null;
    }

    public static List<ModelInstanceFactory> getFactories() {
        return new ArrayList<>(factories);
    }
}
