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

    public boolean toonRenderingEnabled = false;
    public int toonLevels = 3;
    public float toonRimPower = 5.0f;
    public float toonRimIntensity = 0.1f;
    public float toonShadowR = 0.8f;
    public float toonShadowG = 0.8f;
    public float toonShadowB = 0.8f;
    public float toonSpecularPower = 30.0f;
    public float toonSpecularIntensity = 0.08f;
    public boolean toonOutlineEnabled = false;
    public float toonOutlineWidth = 0.003f;
    public float toonOutlineR = 0.0f;
    public float toonOutlineG = 0.0f;
    public float toonOutlineB = 0.0f;

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

    public boolean firstPersonModelEnabled = false;
    public float firstPersonCameraForwardOffset = 0.0f;
    public float firstPersonCameraVerticalOffset = 0.0f;

    public int textureCacheBudgetMB = 256;

    public boolean debugHudEnabled = false;

    public boolean vrEnabled = false;
    public float vrArmIKStrength = 1.0f;

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
