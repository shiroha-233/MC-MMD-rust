package com.shiroha.mmdskin.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Files;

/**
 * 舞台模式配置
 */

public class StageConfig {
    private static final Logger logger = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile StageConfig instance;

    public String lastStagePack = "";
    public boolean cinematicMode = true;
    public float cameraHeightOffset = 0.0f;

    private StageConfig() {}

    public static StageConfig getInstance() {
        StageConfig local = instance;
        if (local == null) {
            synchronized (StageConfig.class) {
                local = instance;
                if (local == null) {
                    local = load();
                    instance = local;
                }
            }
        }
        return local;
    }

    private static StageConfig load() {
        try {
            File configFile = PathConstants.getConfigFile(PathConstants.STAGE_CONFIG);
            if (configFile.exists()) {
                String json = Files.readString(configFile.toPath());
                StageConfig config = GSON.fromJson(json, StageConfig.class);
                if (config != null) {
                    return config;
                }
            }
        } catch (Exception e) {
            logger.warn("[StageConfig] 加载失败: {}", e.getMessage());
        }
        return new StageConfig();
    }

    public void save() {
        try {
            File configFile = PathConstants.getConfigFile(PathConstants.STAGE_CONFIG);
            configFile.getParentFile().mkdirs();
            Files.writeString(configFile.toPath(), GSON.toJson(this));
        } catch (Exception e) {
            logger.warn("[StageConfig] 保存失败: {}", e.getMessage());
        }
    }
}
