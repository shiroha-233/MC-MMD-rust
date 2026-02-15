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
 * 使用 JSON 格式存储，Fabric/Forge 共用
 */
public class ConfigData {
    private static final Logger logger = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    // 全局功能开关（调试用途）
    // 当此开关为 false 时，模组完全停止运作
    public boolean modEnabled = true;

    // 渲染设置
    public boolean openGLEnableLighting = true;
    public int modelPoolMaxCount = 20;
    public boolean mmdShaderEnabled = false;
    
    // GPU 加速
    public boolean gpuSkinningEnabled = false;
    public boolean gpuMorphEnabled = false;
    public int maxBones = 2048;
    
    // Toon 渲染（3渲2）
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
    
    // ==================== 物理引擎配置（Bullet3） ====================
    // 默认值与 Rust PhysicsConfig 保持一致
    public boolean physicsEnabled = true;
    public float physicsGravityY = -98.0f;
    public float physicsFps = 60.0f;
    public int physicsMaxSubstepCount = 5;
    public float physicsInertiaStrength = 0.5f;
    public float physicsMaxLinearVelocity = 20.0f;
    public float physicsMaxAngularVelocity = 20.0f;
    public boolean physicsJointsEnabled = true;
    public boolean physicsDebugLog = false;
    
    // 第一人称模型显示
    public boolean firstPersonModelEnabled = false;
    public float firstPersonCameraForwardOffset = 0.0f;
    public float firstPersonCameraVerticalOffset = 0.0f;
    
    // 纹理缓存
    public int textureCacheBudgetMB = 256;
    
    // 调试
    public boolean debugHudEnabled = false;
    
    /**
     * 从文件加载配置
     */
    public static ConfigData load(Path configPath) {
        Path configFile = configPath.resolve("config.json");
        
        if (!Files.exists(configFile)) {
            logger.info("配置文件不存在，创建默认配置: {}", configFile);
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
            logger.info("配置加载成功: {}", configFile);
            return config;
        } catch (Exception e) {
            logger.error("配置加载失败，使用默认配置: {}", e.getMessage());
            return new ConfigData();
        }
    }
    
    /**
     * 保存配置到文件
     */
    public void save(Path configPath) {
        try {
            // 确保目录存在
            if (!Files.exists(configPath)) {
                Files.createDirectories(configPath);
            }
            
            Path configFile = configPath.resolve("config.json");
            try (Writer writer = Files.newBufferedWriter(configFile)) {
                GSON.toJson(this, writer);
                logger.info("配置已保存: {}", configFile);
            }
        } catch (IOException e) {
            logger.error("保存配置失败: {}", e.getMessage());
        }
    }
    
    /**
     * 复制当前值到另一个配置对象（直接字段赋值，新增字段时需同步更新）
     */
    public void copyTo(ConfigData other) {
        // 渲染设置
        other.openGLEnableLighting = this.openGLEnableLighting;
        other.modelPoolMaxCount = this.modelPoolMaxCount;
        other.mmdShaderEnabled = this.mmdShaderEnabled;
        // GPU 加速
        other.gpuSkinningEnabled = this.gpuSkinningEnabled;
        other.gpuMorphEnabled = this.gpuMorphEnabled;
        other.maxBones = this.maxBones;
        // Toon 渲染
        other.toonRenderingEnabled = this.toonRenderingEnabled;
        other.toonLevels = this.toonLevels;
        other.toonRimPower = this.toonRimPower;
        other.toonRimIntensity = this.toonRimIntensity;
        other.toonShadowR = this.toonShadowR;
        other.toonShadowG = this.toonShadowG;
        other.toonShadowB = this.toonShadowB;
        other.toonSpecularPower = this.toonSpecularPower;
        other.toonSpecularIntensity = this.toonSpecularIntensity;
        other.toonOutlineEnabled = this.toonOutlineEnabled;
        other.toonOutlineWidth = this.toonOutlineWidth;
        other.toonOutlineR = this.toonOutlineR;
        other.toonOutlineG = this.toonOutlineG;
        other.toonOutlineB = this.toonOutlineB;
        // 物理引擎
        other.physicsEnabled = this.physicsEnabled;
        other.physicsGravityY = this.physicsGravityY;
        other.physicsFps = this.physicsFps;
        other.physicsMaxSubstepCount = this.physicsMaxSubstepCount;
        other.physicsInertiaStrength = this.physicsInertiaStrength;
        other.physicsMaxLinearVelocity = this.physicsMaxLinearVelocity;
        other.physicsMaxAngularVelocity = this.physicsMaxAngularVelocity;
        other.physicsJointsEnabled = this.physicsJointsEnabled;
        other.physicsDebugLog = this.physicsDebugLog;
        // 第一人称
        other.firstPersonModelEnabled = this.firstPersonModelEnabled;
        other.firstPersonCameraForwardOffset = this.firstPersonCameraForwardOffset;
        other.firstPersonCameraVerticalOffset = this.firstPersonCameraVerticalOffset;
        // 纹理缓存
        other.textureCacheBudgetMB = this.textureCacheBudgetMB;
        // 调试
        other.debugHudEnabled = this.debugHudEnabled;
    }
}
