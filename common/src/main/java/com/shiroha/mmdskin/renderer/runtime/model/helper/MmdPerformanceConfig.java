package com.shiroha.mmdskin.renderer.runtime.model.helper;

interface MmdPerformanceConfig {
    boolean isPerformanceProfilingEnabled();
    int getPerformanceLogIntervalSeconds();
    int getMaxVisibleModelsPerFrame();
    float getAnimationLodMediumDistance();
    float getAnimationLodFarDistance();
    int getAnimationLodMediumUpdateInterval();
    int getAnimationLodFarUpdateInterval();
    boolean isPhysicsEnabled();
    int getMaxPhysicsModelsPerFrame();
    float getPhysicsLodMaxDistance();
}
