package com.shiroha.mmdskin.renderer.runtime.model.helper;

import com.shiroha.mmdskin.config.ConfigManager;

final class ConfigManagerMmdPerformanceConfig implements MmdPerformanceConfig {
    private static final ConfigManagerMmdPerformanceConfig INSTANCE = new ConfigManagerMmdPerformanceConfig();

    private ConfigManagerMmdPerformanceConfig() {
    }

    static ConfigManagerMmdPerformanceConfig get() {
        return INSTANCE;
    }

    @Override public boolean isPerformanceProfilingEnabled() { return ConfigManager.isPerformanceProfilingEnabled(); }
    @Override public int getPerformanceLogIntervalSeconds() { return ConfigManager.getPerformanceLogIntervalSeconds(); }
    @Override public int getMaxVisibleModelsPerFrame() { return ConfigManager.getMaxVisibleModelsPerFrame(); }
    @Override public float getAnimationLodMediumDistance() { return ConfigManager.getAnimationLodMediumDistance(); }
    @Override public float getAnimationLodFarDistance() { return ConfigManager.getAnimationLodFarDistance(); }
    @Override public int getAnimationLodMediumUpdateInterval() { return ConfigManager.getAnimationLodMediumUpdateInterval(); }
    @Override public int getAnimationLodFarUpdateInterval() { return ConfigManager.getAnimationLodFarUpdateInterval(); }
    @Override public boolean isPhysicsEnabled() { return ConfigManager.isPhysicsEnabled(); }
    @Override public int getMaxPhysicsModelsPerFrame() { return ConfigManager.getMaxPhysicsModelsPerFrame(); }
    @Override public float getPhysicsLodMaxDistance() { return ConfigManager.getPhysicsLodMaxDistance(); }
}
