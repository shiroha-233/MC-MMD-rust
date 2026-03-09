package com.shiroha.mmdskin.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型专属动画映射配置
 */

public final class ModelAnimConfig {
    private static final Logger logger = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private static final ConcurrentHashMap<String, Map<String, String>> cache = new ConcurrentHashMap<>();

    private ModelAnimConfig() {}

    public static Map<String, String> getMapping(String modelDir) {
        if (modelDir == null || modelDir.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(
            cache.computeIfAbsent(modelDir, ModelAnimConfig::loadFromDisk)
        );
    }

    public static String getMappedFile(String modelDir, String slotName) {
        Map<String, String> mapping = getMapping(modelDir);
        return mapping.get(slotName);
    }

    public static void setMapping(String modelDir, String slotName, String vmdFileName) {
        Map<String, String> mapping = cache.computeIfAbsent(modelDir, ModelAnimConfig::loadFromDisk);

        if (vmdFileName == null || vmdFileName.isEmpty()) {
            mapping.remove(slotName);
        } else {
            mapping.put(slotName, vmdFileName);
        }

        saveToDisk(modelDir, mapping);
    }

    public static void saveMapping(String modelDir, Map<String, String> mapping) {
        ConcurrentHashMap<String, String> safeCopy = new ConcurrentHashMap<>(mapping);

        safeCopy.entrySet().removeIf(e -> e.getValue() == null || e.getValue().isEmpty());
        cache.put(modelDir, safeCopy);
        saveToDisk(modelDir, safeCopy);
    }

    public static void invalidate(String modelDir) {
        cache.remove(modelDir);
    }

    public static void invalidateAll() {
        cache.clear();
    }

    private static ConcurrentHashMap<String, String> loadFromDisk(String modelDir) {
        File configFile = PathConstants.getModelAnimConfigFile(modelDir);
        if (!configFile.exists()) {
            return new ConcurrentHashMap<>();
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
            Map<String, String> raw = GSON.fromJson(reader, MAP_TYPE);
            if (raw == null) {
                return new ConcurrentHashMap<>();
            }
            ConcurrentHashMap<String, String> result = new ConcurrentHashMap<>();

            raw.forEach((k, v) -> {
                if (k != null && !k.isEmpty() && v != null && !v.isEmpty()) {
                    result.put(k, v);
                }
            });
            return result;
        } catch (Exception e) {
            logger.warn("加载模型动画映射失败: {}", e.getMessage());
            return new ConcurrentHashMap<>();
        }
    }

    private static void saveToDisk(String modelDir, Map<String, String> mapping) {
        File configFile = PathConstants.getModelAnimConfigFile(modelDir);
        PathConstants.ensureDirectoryExists(configFile.getParentFile());

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8)) {
            GSON.toJson(mapping, writer);
            logger.debug("保存模型动画映射: {} ({} 条)", configFile.getName(), mapping.size());
        } catch (IOException e) {
            logger.error("保存模型动画映射失败: {}", e.getMessage());
        }
    }
}
