/* 文件职责：保存和加载舞台模式的本地偏好配置。 */
package com.shiroha.mmdskin.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
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
    private static final float MIN_CAMERA_HEIGHT_OFFSET = -2.0f;
    private static final float MAX_CAMERA_HEIGHT_OFFSET = 2.0f;
    private static final float MIN_AUDIO_VOLUME = 0.0f;
    private static final float MAX_AUDIO_VOLUME = 1.0f;

    private static volatile StageConfig instance;

    public String lastStagePack = "";
    public boolean cinematicMode = true;
    public float cameraHeightOffset = 0.0f;
    public float audioVolume = 1.0f;

    private StageConfig() {
    }

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
                JsonObject root = GSON.fromJson(json, JsonObject.class);
                StageConfig config = GSON.fromJson(root, StageConfig.class);
                if (config != null) {
                    config.normalizeLoadedValues(root);
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

    private void normalizeLoadedValues(JsonObject root) {
        if (lastStagePack == null) {
            lastStagePack = "";
        }
        if (!Float.isFinite(cameraHeightOffset)) {
            cameraHeightOffset = 0.0f;
        }
        cameraHeightOffset = clamp(cameraHeightOffset, MIN_CAMERA_HEIGHT_OFFSET, MAX_CAMERA_HEIGHT_OFFSET);
        if (root == null || !root.has("audioVolume") || !Float.isFinite(audioVolume)) {
            audioVolume = 1.0f;
        }
        audioVolume = clamp(audioVolume, MIN_AUDIO_VOLUME, MAX_AUDIO_VOLUME);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
