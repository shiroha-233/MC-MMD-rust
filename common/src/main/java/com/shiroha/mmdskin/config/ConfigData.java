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
    
    // 渲染设置
    public boolean openGLEnableLighting = true;
    public int modelPoolMaxCount = 100;
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
    
    // ==================== 物理引擎配置 ====================
    // 重力
    public float physicsGravityY = -3.8f;
    
    // 模拟参数
    public float physicsFps = 60.0f;
    public int physicsMaxSubstepCount = 4;
    public int physicsSolverIterations = 4;
    public int physicsPgsIterations = 2;
    public float physicsMaxCorrectiveVelocity = 0.1f;
    
    // 刚体阻尼
    public float physicsLinearDampingScale = 0.3f;
    public float physicsAngularDampingScale = 0.2f;
    
    // 质量
    public float physicsMassScale = 2.0f;
    
    // 弹簧刚度
    public float physicsLinearSpringStiffnessScale = 0.01f;
    public float physicsAngularSpringStiffnessScale = 0.01f;
    
    // 弹簧阻尼
    public float physicsLinearSpringDampingFactor = 8.0f;
    public float physicsAngularSpringDampingFactor = 8.0f;
    
    // 惯性效果
    public float physicsInertiaStrength = 1.0f;
    
    // 速度限制
    public float physicsMaxLinearVelocity = 1.0f;
    public float physicsMaxAngularVelocity = 1.0f;
    
    // 胸部物理专用参数
    public boolean physicsBustEnabled = true;
    public float physicsBustLinearDampingScale = 1.5f;
    public float physicsBustAngularDampingScale = 1.5f;
    public float physicsBustMassScale = 1.0f;
    public float physicsBustLinearSpringStiffnessScale = 10.0f;
    public float physicsBustAngularSpringStiffnessScale = 10.0f;
    public float physicsBustLinearSpringDampingFactor = 3.0f;
    public float physicsBustAngularSpringDampingFactor = 3.0f;
    
    // 胸部防凹陷
    public boolean physicsBustClampInward = true;
    
    // 调试
    public boolean physicsJointsEnabled = true;
    public boolean physicsDebugLog = false;
    
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
     * 复制当前值到另一个配置对象
     */
    public void copyTo(ConfigData other) {
        other.openGLEnableLighting = this.openGLEnableLighting;
        other.modelPoolMaxCount = this.modelPoolMaxCount;
        other.mmdShaderEnabled = this.mmdShaderEnabled;
        other.gpuSkinningEnabled = this.gpuSkinningEnabled;
        other.gpuMorphEnabled = this.gpuMorphEnabled;
        other.maxBones = this.maxBones;
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
        // 物理引擎配置
        other.physicsGravityY = this.physicsGravityY;
        other.physicsFps = this.physicsFps;
        other.physicsMaxSubstepCount = this.physicsMaxSubstepCount;
        other.physicsSolverIterations = this.physicsSolverIterations;
        other.physicsPgsIterations = this.physicsPgsIterations;
        other.physicsMaxCorrectiveVelocity = this.physicsMaxCorrectiveVelocity;
        other.physicsLinearDampingScale = this.physicsLinearDampingScale;
        other.physicsAngularDampingScale = this.physicsAngularDampingScale;
        other.physicsMassScale = this.physicsMassScale;
        other.physicsLinearSpringStiffnessScale = this.physicsLinearSpringStiffnessScale;
        other.physicsAngularSpringStiffnessScale = this.physicsAngularSpringStiffnessScale;
        other.physicsLinearSpringDampingFactor = this.physicsLinearSpringDampingFactor;
        other.physicsAngularSpringDampingFactor = this.physicsAngularSpringDampingFactor;
        other.physicsInertiaStrength = this.physicsInertiaStrength;
        other.physicsMaxLinearVelocity = this.physicsMaxLinearVelocity;
        other.physicsMaxAngularVelocity = this.physicsMaxAngularVelocity;
        // 胸部物理配置
        other.physicsBustEnabled = this.physicsBustEnabled;
        other.physicsBustLinearDampingScale = this.physicsBustLinearDampingScale;
        other.physicsBustAngularDampingScale = this.physicsBustAngularDampingScale;
        other.physicsBustMassScale = this.physicsBustMassScale;
        other.physicsBustLinearSpringStiffnessScale = this.physicsBustLinearSpringStiffnessScale;
        other.physicsBustAngularSpringStiffnessScale = this.physicsBustAngularSpringStiffnessScale;
        other.physicsBustLinearSpringDampingFactor = this.physicsBustLinearSpringDampingFactor;
        other.physicsBustAngularSpringDampingFactor = this.physicsBustAngularSpringDampingFactor;
        other.physicsBustClampInward = this.physicsBustClampInward;
        other.physicsJointsEnabled = this.physicsJointsEnabled;
        other.physicsDebugLog = this.physicsDebugLog;
    }
}
