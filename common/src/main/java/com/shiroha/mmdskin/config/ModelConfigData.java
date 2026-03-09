package com.shiroha.mmdskin.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * 单个模型的独立配置数据
 */

public class ModelConfigData {
    private static final Logger logger = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean eyeTrackingEnabled = true;

    public float eyeMaxAngle = 0.1745f;

    public float modelScale = 1.0f;

    public Set<Integer> hiddenMaterials = new HashSet<>();

    public static ModelConfigData load(File configFile) {
        if (!configFile.exists()) {
            return new ModelConfigData();
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
            ModelConfigData config = GSON.fromJson(reader, ModelConfigData.class);
            if (config == null) {
                return new ModelConfigData();
            }
            return config;
        } catch (Exception e) {
            logger.warn("加载模型配置失败: {}, 使用默认配置", e.getMessage());
            return new ModelConfigData();
        }
    }

    public ModelConfigData copy() {
        ModelConfigData c = new ModelConfigData();
        c.eyeTrackingEnabled = this.eyeTrackingEnabled;
        c.eyeMaxAngle = this.eyeMaxAngle;
        c.modelScale = this.modelScale;
        c.hiddenMaterials = new HashSet<>(this.hiddenMaterials);
        return c;
    }

    public void save(File configFile) {
        PathConstants.ensureDirectoryExists(configFile.getParentFile());

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
            logger.debug("模型配置已保存: {}", configFile.getName());
        } catch (IOException e) {
            logger.error("保存模型配置失败: {}", e.getMessage());
        }
    }
}
