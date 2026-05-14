/* 文件职责：管理模型独立配置缓存，并区分对外防御性副本与内部共享读取视图。 */
package com.shiroha.mmdskin.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class ModelConfigManager {
    private static final Logger logger = LogManager.getLogger();
    private static final String MODEL_CONFIGS_DIR = "model_configs";
    private static final ConcurrentHashMap<String, ModelConfigData> cache = new ConcurrentHashMap<>();
    private static volatile Supplier<File> configRootDirSupplier = PathConstants::getConfigRootDir;

    private ModelConfigManager() {
    }

    public static ModelConfigData getConfig(String modelName) {
        return getCachedConfig(modelName).copy();
    }

    public static ModelConfigData getLiveConfig(String modelName) {
        return getCachedConfig(modelName);
    }

    private static ModelConfigData getCachedConfig(String modelName) {
        if (modelName == null || modelName.isEmpty()
                || modelName.equals(UIConstants.DEFAULT_MODEL_NAME)) {
            return new ModelConfigData();
        }

        return cache.computeIfAbsent(modelName, name -> {
            File configFile = getConfigFile(name);
            ModelConfigData config = ModelConfigData.load(configFile).normalizedCopy();
            logger.debug("加载模型配置: {} (眼球角度: {})", name, config.eyeMaxAngle);
            return config;
        });
    }

    public static void saveConfig(String modelName, ModelConfigData config) {
        if (modelName == null || modelName.isEmpty()
                || modelName.equals(UIConstants.DEFAULT_MODEL_NAME)) {
            return;
        }

        ModelConfigData safeCopy = config == null ? new ModelConfigData() : config.normalizedCopy();
        cache.put(modelName, safeCopy);
        File configFile = getConfigFile(modelName);
        safeCopy.save(configFile);
    }

    public static void invalidate(String modelName) {
        cache.remove(modelName);
    }

    public static void invalidateAll() {
        cache.clear();
    }

    public static File getConfigFile(String modelName) {
        File dir = new File(resolveConfigRootDir(), MODEL_CONFIGS_DIR);
        return new File(dir, sanitizeModelName(modelName) + ".json");
    }

    private static String sanitizeModelName(String name) {
        if (name == null) return "unknown";
        return name.replace('/', '_').replace('\\', '_')
                .replace("..", "__").replace('\0', '_');
    }

    public static File getConfigDir() {
        return new File(resolveConfigRootDir(), MODEL_CONFIGS_DIR);
    }

    static void setConfigRootDirSupplierForTesting(Supplier<File> supplier) {
        configRootDirSupplier = supplier != null ? supplier : PathConstants::getConfigRootDir;
    }

    private static File resolveConfigRootDir() {
        return Objects.requireNonNullElseGet(configRootDirSupplier.get(), PathConstants::getConfigRootDir);
    }
}
