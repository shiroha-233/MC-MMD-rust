package com.shiroha.mmdskin.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Files;

/**
 * 舞台模式配置
 * 持久化上次选择的动作/相机 VMD、影院模式开关等
 */
public class StageConfig {
    private static final Logger logger = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private static StageConfig instance;
    
    // 配置字段
    public String lastStagePack = "";
    public boolean cinematicMode = true;
    public float cameraHeightOffset = 0.0f; // 镜头高度偏移（MC单位，正值=抬高）
    
    private StageConfig() {}
    
    public static StageConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }
    
    /**
     * 从文件加载配置
     */
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
    
    /**
     * 保存配置到文件
     */
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
