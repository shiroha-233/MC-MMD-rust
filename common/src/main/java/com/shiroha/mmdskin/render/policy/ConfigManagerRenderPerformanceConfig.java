package com.shiroha.mmdskin.render.policy;

import com.shiroha.mmdskin.config.ConfigManager;

/** 文件职责：从全局配置读取渲染性能预算参数。 */
public final class ConfigManagerRenderPerformanceConfig implements RenderPerformanceConfig {
    private static final ConfigManagerRenderPerformanceConfig INSTANCE = new ConfigManagerRenderPerformanceConfig();

    private ConfigManagerRenderPerformanceConfig() {
    }

    public static ConfigManagerRenderPerformanceConfig get() {
        return INSTANCE;
    }

    @Override
    public boolean isPerformanceProfilingEnabled() {
        return ConfigManager.isPerformanceProfilingEnabled();
    }

    @Override
    public int getPerformanceLogIntervalSeconds() {
        return ConfigManager.getPerformanceLogIntervalSeconds();
    }

    @Override
    public int getMaxVisibleModelsPerFrame() {
        return ConfigManager.getMaxVisibleModelsPerFrame();
    }

    @Override
    public float getAnimationLodMediumDistance() {
        return ConfigManager.getAnimationLodMediumDistance();
    }

    @Override
    public float getAnimationLodFarDistance() {
        return ConfigManager.getAnimationLodFarDistance();
    }

    @Override
    public int getAnimationLodMediumUpdateInterval() {
        return ConfigManager.getAnimationLodMediumUpdateInterval();
    }

    @Override
    public int getAnimationLodFarUpdateInterval() {
        return ConfigManager.getAnimationLodFarUpdateInterval();
    }

    @Override
    public boolean isPhysicsEnabled() {
        return ConfigManager.isPhysicsEnabled();
    }

    @Override
    public int getMaxPhysicsModelsPerFrame() {
        return ConfigManager.getMaxPhysicsModelsPerFrame();
    }

    @Override
    public float getPhysicsLodMaxDistance() {
        return ConfigManager.getPhysicsLodMaxDistance();
    }
}
