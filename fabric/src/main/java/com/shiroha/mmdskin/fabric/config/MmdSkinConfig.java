package com.shiroha.mmdskin.fabric.config;

import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.shiroha.mmdskin.config.ConfigData;
import com.shiroha.mmdskin.config.ConfigManager;

import java.nio.file.Path;

/**
 * Fabric 配置实现
 * 使用 JSON 格式，配置文件位于 config/mmdskin/config.json
 */
public final class MmdSkinConfig implements ConfigManager.IConfigProvider {
    private static final Logger logger = LogManager.getLogger();
    private static MmdSkinConfig instance;
    private static ConfigData data;
    private static Path configPath;
    
    private MmdSkinConfig() {
        configPath = FabricLoader.getInstance().getConfigDir().resolve("mmdskin");
        data = ConfigData.load(configPath);
    }
    
    public static void init() {
        instance = new MmdSkinConfig();
        ConfigManager.init(instance);
        logger.info("Fabric 配置系统初始化完成 (JSON)");
    }
    
    /** 获取配置数据（供 UI 使用） */
    public static ConfigData getData() {
        return data;
    }
    
    /** 保存配置 */
    public static void save() {
        data.save(configPath);
    }
    
    // ==================== IConfigProvider 实现 ====================
    
    @Override
    public boolean isOpenGLLightingEnabled() {
        return data.openGLEnableLighting;
    }
    
    @Override
    public int getModelPoolMaxCount() {
        return data.modelPoolMaxCount;
    }
    
    @Override
    public boolean isMMDShaderEnabled() {
        return data.mmdShaderEnabled;
    }
    
    @Override
    public boolean isGpuSkinningEnabled() {
        return data.gpuSkinningEnabled;
    }
    
    @Override
    public boolean isGpuMorphEnabled() {
        return data.gpuMorphEnabled;
    }
    
    @Override
    public boolean isToonRenderingEnabled() {
        return data.toonRenderingEnabled;
    }
    
    @Override
    public int getToonLevels() {
        return data.toonLevels;
    }
    
    @Override
    public boolean isToonOutlineEnabled() {
        return data.toonOutlineEnabled;
    }
    
    @Override
    public float getToonOutlineWidth() {
        return data.toonOutlineWidth;
    }
    
    @Override
    public float getToonRimPower() {
        return data.toonRimPower;
    }
    
    @Override
    public float getToonRimIntensity() {
        return data.toonRimIntensity;
    }
    
    @Override
    public float getToonShadowR() {
        return data.toonShadowR;
    }
    
    @Override
    public float getToonShadowG() {
        return data.toonShadowG;
    }
    
    @Override
    public float getToonShadowB() {
        return data.toonShadowB;
    }
    
    @Override
    public float getToonSpecularPower() {
        return data.toonSpecularPower;
    }
    
    @Override
    public float getToonSpecularIntensity() {
        return data.toonSpecularIntensity;
    }
    
    @Override
    public float getToonOutlineR() {
        return data.toonOutlineR;
    }
    
    @Override
    public float getToonOutlineG() {
        return data.toonOutlineG;
    }
    
    @Override
    public float getToonOutlineB() {
        return data.toonOutlineB;
    }
    
    @Override
    public int getMaxBones() {
        return data.maxBones;
    }
    
    // ==================== 物理引擎配置 ====================
    
    @Override
    public float getPhysicsGravityY() {
        return data.physicsGravityY;
    }
    
    @Override
    public float getPhysicsFps() {
        return data.physicsFps;
    }
    
    @Override
    public int getPhysicsMaxSubstepCount() {
        return data.physicsMaxSubstepCount;
    }
    
    @Override
    public int getPhysicsSolverIterations() {
        return data.physicsSolverIterations;
    }
    
    @Override
    public int getPhysicsPgsIterations() {
        return data.physicsPgsIterations;
    }
    
    @Override
    public float getPhysicsMaxCorrectiveVelocity() {
        return data.physicsMaxCorrectiveVelocity;
    }
    
    @Override
    public float getPhysicsLinearDampingScale() {
        return data.physicsLinearDampingScale;
    }
    
    @Override
    public float getPhysicsAngularDampingScale() {
        return data.physicsAngularDampingScale;
    }
    
    @Override
    public float getPhysicsMassScale() {
        return data.physicsMassScale;
    }
    
    @Override
    public float getPhysicsLinearSpringStiffnessScale() {
        return data.physicsLinearSpringStiffnessScale;
    }
    
    @Override
    public float getPhysicsAngularSpringStiffnessScale() {
        return data.physicsAngularSpringStiffnessScale;
    }
    
    @Override
    public float getPhysicsLinearSpringDampingFactor() {
        return data.physicsLinearSpringDampingFactor;
    }
    
    @Override
    public float getPhysicsAngularSpringDampingFactor() {
        return data.physicsAngularSpringDampingFactor;
    }
    
    @Override
    public float getPhysicsInertiaStrength() {
        return data.physicsInertiaStrength;
    }
    
    @Override
    public float getPhysicsMaxLinearVelocity() {
        return data.physicsMaxLinearVelocity;
    }
    
    @Override
    public float getPhysicsMaxAngularVelocity() {
        return data.physicsMaxAngularVelocity;
    }
    
    // ==================== 胸部物理配置 ====================
    
    @Override
    public boolean isPhysicsBustEnabled() {
        return data.physicsBustEnabled;
    }
    
    @Override
    public float getPhysicsBustLinearDampingScale() {
        return data.physicsBustLinearDampingScale;
    }
    
    @Override
    public float getPhysicsBustAngularDampingScale() {
        return data.physicsBustAngularDampingScale;
    }
    
    @Override
    public float getPhysicsBustMassScale() {
        return data.physicsBustMassScale;
    }
    
    @Override
    public float getPhysicsBustLinearSpringStiffnessScale() {
        return data.physicsBustLinearSpringStiffnessScale;
    }
    
    @Override
    public float getPhysicsBustAngularSpringStiffnessScale() {
        return data.physicsBustAngularSpringStiffnessScale;
    }
    
    @Override
    public float getPhysicsBustLinearSpringDampingFactor() {
        return data.physicsBustLinearSpringDampingFactor;
    }
    
    @Override
    public float getPhysicsBustAngularSpringDampingFactor() {
        return data.physicsBustAngularSpringDampingFactor;
    }
    
    @Override
    public boolean isPhysicsJointsEnabled() {
        return data.physicsJointsEnabled;
    }
    
    @Override
    public boolean isPhysicsDebugLog() {
        return data.physicsDebugLog;
    }
}
