/* 文件职责：定义渲染层可读取的全局渲染配置。 */
package com.shiroha.mmdskin.config;

public interface IRenderConfig {
    boolean isLightingEnabled();

    default boolean isGpuSkinningEnabled() { return true; }

    int getModelPoolMaxCount();

    /** 是否自动下载附近玩家正在使用的模型（仅限服务器授权分发的模型）。 */
    default boolean isAutoDownloadNearbyModels() { return false; }

    /** 自动下载半径（格）。 */
    default int getAutoDownloadRadius() { return 48; }

    default boolean isPerformanceProfilingEnabled() { return false; }

    default int getPerformanceLogIntervalSeconds() { return 5; }

    default int getMaxVisibleModelsPerFrame() { return 20; }

    /** 加载前距离闸门（格）：超过且未加载则不加载模型；0 = 不限制。 */
    default float getModelLoadMaxDistance() { return 64.0f; }

    default float getAnimationLodMediumDistance() { return 24.0f; }

    default float getAnimationLodFarDistance() { return 48.0f; }

    default int getAnimationLodMediumUpdateInterval() { return 2; }

    default int getAnimationLodFarUpdateInterval() { return 4; }

    default boolean isFirstPersonModelEnabled() { return false; }

    default float getFirstPersonCameraForwardOffset() { return 0.0f; }

    default float getFirstPersonCameraVerticalOffset() { return 0.0f; }

    default boolean isDebugHudEnabled() { return false; }

    default int getTextureCacheBudgetMB() { return 256; }
}
