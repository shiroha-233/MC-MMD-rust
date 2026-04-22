package com.shiroha.mmdskin.render.policy;

/** 文件职责：抽象渲染性能预算相关配置读取。 */
public interface RenderPerformanceConfig {
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
