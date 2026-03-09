package com.shiroha.mmdskin.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型独立配置管理器
 */

public class ModelConfigManager {
    private static final Logger logger = LogManager.getLogger();

    private static final String MODEL_CONFIGS_DIR = "model_configs";

    private static final ConcurrentHashMap<String, ModelConfigData> cache = new ConcurrentHashMap<>();

    private ModelConfigManager() {

    }

    public static ModelConfigData getConfig(String modelName) {
        if (modelName == null || modelName.isEmpty()
                || modelName.equals(UIConstants.DEFAULT_MODEL_NAME)) {
            return new ModelConfigData();
        }

        return cache.computeIfAbsent(modelName, name -> {
            File configFile = getConfigFile(name);
            ModelConfigData config = ModelConfigData.load(configFile);
            logger.debug("加载模型配置: {} (眼球角度: {})", name, config.eyeMaxAngle);
            return config;
        });
    }

    public static void saveConfig(String modelName, ModelConfigData config) {
        if (modelName == null || modelName.isEmpty()
                || modelName.equals(UIConstants.DEFAULT_MODEL_NAME)) {
            return;
        }

        ModelConfigData safeCopy = config.copy();
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
        File dir = new File(PathConstants.getConfigRootDir(), MODEL_CONFIGS_DIR);
        return new File(dir, sanitizeModelName(modelName) + ".json");
    }

    private static String sanitizeModelName(String name) {
        if (name == null) return "unknown";
        return name.replace('/', '_').replace('\\', '_')
                   .replace("..", "__").replace('\0', '_');
    }

    public static File getConfigDir() {
        return new File(PathConstants.getConfigRootDir(), MODEL_CONFIGS_DIR);
    }
}
