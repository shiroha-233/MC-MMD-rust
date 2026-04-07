package com.shiroha.mmdskin.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一配置数据类
 */

public class ConfigData {
    private static final Logger logger = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean openGLEnableLighting = true;
    public int modelPoolMaxCount = 20;
    public boolean mmdShaderEnabled = false;

    public boolean gpuSkinningEnabled = false;
    public boolean gpuMorphEnabled = false;
    public int maxBones = 2048;
    public boolean performanceProfilingEnabled = false;
    public int performanceLogIntervalSeconds = 5;
    public int maxVisibleModelsPerFrame = 10;
    public float animationLodMediumDistance = 24.0f;
    public float animationLodFarDistance = 48.0f;
    public int animationLodMediumUpdateInterval = 2;
    public int animationLodFarUpdateInterval = 4;

    public boolean toonRenderingEnabled = false;
    public int toonLevels = 4;
    public float toonRimPower = 5.6f;
    public float toonRimIntensity = 0.02f;
    public float toonShadowR = 0.78f;
    public float toonShadowG = 0.84f;
    public float toonShadowB = 0.94f;
    public float toonSpecularPower = 96.0f;
    public float toonSpecularIntensity = 0.015f;
    public boolean toonOutlineEnabled = true;
    public float toonOutlineWidth = 0.0022f;
    public float toonOutlineR = 0.06f;
    public float toonOutlineG = 0.08f;
    public float toonOutlineB = 0.12f;

    public boolean physicsEnabled = true;
    public float physicsGravityY = -98.0f;
    public float physicsFps = 60.0f;
    public int physicsMaxSubstepCount = 5;
    public float physicsInertiaStrength = 0.5f;
    public float physicsMaxLinearVelocity = 20.0f;
    public float physicsMaxAngularVelocity = 20.0f;
    public boolean physicsJointsEnabled = true;
    public boolean physicsKinematicFilter = true;
    public boolean physicsDebugLog = false;
    public int maxPhysicsModelsPerFrame = 10;
    public float physicsLodMaxDistance = 24.0f;

    public boolean firstPersonModelEnabled = false;
    public float firstPersonCameraForwardOffset = 0.0f;
    public float firstPersonCameraVerticalOffset = 0.0f;

    public int textureCacheBudgetMB = 256;

    public boolean debugHudEnabled = false;

    public boolean vrEnabled = false;
    public float vrArmIKStrength = 1.0f;

    public Map<String, String> mobModelReplacements = new LinkedHashMap<>();

    public static ConfigData load(Path configPath) {
        Path configFile = configPath.resolve("config.json");

        if (!Files.exists(configFile)) {
            ConfigData defaultConfig = new ConfigData();
            defaultConfig.save(configPath);
            return defaultConfig;
        }

        try (Reader reader = Files.newBufferedReader(configFile)) {
            ConfigData config = GSON.fromJson(reader, ConfigData.class);
            if (config == null) {
                logger.warn("配置文件为空，使用默认配置");
                return new ConfigData();
            }
            config.normalize();
            return config;
        } catch (Exception e) {
            logger.error("配置加载失败，使用默认配置: {}", e.getMessage());
            return new ConfigData();
        }
    }

    public void save(Path configPath) {
        try {

            if (!Files.exists(configPath)) {
                Files.createDirectories(configPath);
            }

            Path configFile = configPath.resolve("config.json");
            try (Writer writer = Files.newBufferedWriter(configFile)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            logger.error("保存配置失败: {}", e.getMessage());
        }
    }

    private void normalize() {
        if (mobModelReplacements == null) {
            mobModelReplacements = new LinkedHashMap<>();
        }

        performanceLogIntervalSeconds = Math.max(1, performanceLogIntervalSeconds);
        maxVisibleModelsPerFrame = Math.max(1, maxVisibleModelsPerFrame);
        animationLodMediumDistance = Math.max(0.0f, animationLodMediumDistance);
        animationLodFarDistance = Math.max(animationLodMediumDistance, animationLodFarDistance);
        animationLodMediumUpdateInterval = Math.max(1, animationLodMediumUpdateInterval);
        animationLodFarUpdateInterval = Math.max(animationLodMediumUpdateInterval, animationLodFarUpdateInterval);
        toonLevels = Math.max(2, Math.min(5, toonLevels));
        toonRimPower = clamp(toonRimPower, 0.1f, 10.0f);
        toonRimIntensity = clamp(toonRimIntensity, 0.0f, 1.0f);
        toonShadowR = clamp(toonShadowR, 0.0f, 1.0f);
        toonShadowG = clamp(toonShadowG, 0.0f, 1.0f);
        toonShadowB = clamp(toonShadowB, 0.0f, 1.0f);
        toonSpecularPower = clamp(toonSpecularPower, 1.0f, 128.0f);
        toonSpecularIntensity = clamp(toonSpecularIntensity, 0.0f, 1.0f);
        toonOutlineWidth = clamp(toonOutlineWidth, 0.001f, 0.02f);
        toonOutlineR = clamp(toonOutlineR, 0.0f, 1.0f);
        toonOutlineG = clamp(toonOutlineG, 0.0f, 1.0f);
        toonOutlineB = clamp(toonOutlineB, 0.0f, 1.0f);
        maxPhysicsModelsPerFrame = Math.max(1, maxPhysicsModelsPerFrame);
        physicsLodMaxDistance = Math.max(0.0f, physicsLodMaxDistance);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public void copyTo(ConfigData other) {
        ConfigData copy = GSON.fromJson(GSON.toJson(this), ConfigData.class);

        try {
            for (var field : ConfigData.class.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                field.set(other, field.get(copy));
            }
        } catch (IllegalAccessException e) {
            logger.error("配置复制失败", e);
        }
    }
}
