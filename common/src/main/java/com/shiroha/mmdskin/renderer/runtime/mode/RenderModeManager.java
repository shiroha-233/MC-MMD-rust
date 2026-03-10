package com.shiroha.mmdskin.renderer.runtime.mode;

import com.shiroha.mmdskin.asset.catalog.ModelInfo;
import com.shiroha.mmdskin.renderer.api.IMMDModel;

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

    private static final List<IMMDModelFactory> factories = new CopyOnWriteArrayList<>();

    private static final Map<RenderCategory, Boolean> enabledStates = new ConcurrentHashMap<>();

    private static volatile boolean initialized = false;

    public static synchronized void registerFactory(IMMDModelFactory factory) {
        if (factory == null) return;

        for (IMMDModelFactory existing : factories) {
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
        for (IMMDModelFactory factory : factories) {
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
        List<IMMDModelFactory> candidates = getOrderedFactories(false, false);
        if (!candidates.isEmpty()) {
            return candidates.get(0).getModeName();
        }
        return "无可用渲染模式";
    }

    private static List<IMMDModelFactory> getOrderedFactories(boolean isPMD, boolean includeDisabled) {
        List<IMMDModelFactory> result = new ArrayList<>();

        List<IMMDModelFactory> sorted = new ArrayList<>(factories);
        sorted.sort(Comparator.comparingInt(IMMDModelFactory::getPriority).reversed());

        for (IMMDModelFactory factory : sorted) {
            if (!factory.isAvailable()) continue;
            if (isPMD && !factory.supportsPMD()) continue;
            if (!includeDisabled && !isEnabled(factory.getCategory())) continue;
            result.add(factory);
        }
        return result;
    }

    public static IMMDModel createModel(String modelFilename, String modelDir, boolean isPMD, long layerCount) {
        syncFactoryStates();

        List<IMMDModelFactory> enabled = getOrderedFactories(isPMD, false);
        IMMDModel model = tryCreateWithFactories(enabled, modelFilename, modelDir, isPMD, layerCount);
        if (model != null) return model;

        List<IMMDModelFactory> all = getOrderedFactories(isPMD, true);
        all.removeAll(enabled);
        model = tryCreateWithFactories(all, modelFilename, modelDir, isPMD, layerCount);
        if (model != null) return model;

        logger.error("所有工厂都无法创建模型: {}", modelFilename);
        return null;
    }

    public static IMMDModel createModel(ModelInfo modelInfo, long layerCount) {
        if (modelInfo == null) return null;
        return createModel(
            modelInfo.getModelFilePath(), modelInfo.getFolderPath(),
            modelInfo.isPMD(), layerCount);
    }

    public static IMMDModel createModelFromHandle(long modelHandle, String modelDir, boolean isPMD) {
        syncFactoryStates();

        List<IMMDModelFactory> enabled = getOrderedFactories(isPMD, false);
        IMMDModel model = tryCreateFromHandle(enabled, modelHandle, modelDir);
        if (model != null) return model;

        List<IMMDModelFactory> all = getOrderedFactories(isPMD, true);
        all.removeAll(enabled);
        model = tryCreateFromHandle(all, modelHandle, modelDir);
        if (model != null) return model;

        logger.error("所有工厂都无法从句柄创建模型");
        return null;
    }

    private static IMMDModel tryCreateWithFactories(List<IMMDModelFactory> candidates,
            String modelFilename, String modelDir, boolean isPMD, long layerCount) {
        for (IMMDModelFactory factory : candidates) {
            try {
                IMMDModel model = factory.createModel(modelFilename, modelDir, isPMD, layerCount);
                if (model != null) return model;
                logger.warn("{} 创建失败，尝试下一个工厂", factory.getModeName());
            } catch (Exception e) {
                logger.error("{} 创建异常: {}", factory.getModeName(), e.getMessage());
            }
        }
        return null;
    }

    private static IMMDModel tryCreateFromHandle(List<IMMDModelFactory> candidates,
            long modelHandle, String modelDir) {
        for (IMMDModelFactory factory : candidates) {
            try {
                IMMDModel model = factory.createModelFromHandle(modelHandle, modelDir);
                if (model != null) return model;
                logger.warn("{} 从句柄创建失败，尝试下一个工厂", factory.getModeName());
            } catch (Exception e) {
                logger.error("{} 从句柄创建异常: {}", factory.getModeName(), e.getMessage());
            }
        }
        return null;
    }

    public static List<IMMDModelFactory> getFactories() {
        return new ArrayList<>(factories);
    }
}
