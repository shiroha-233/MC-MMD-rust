package com.shiroha.mmdskin.config;

/**
 * 渲染相关配置子接口
 */

public interface IRenderConfig {
    boolean isOpenGLLightingEnabled();
    int getModelPoolMaxCount();
    boolean isMMDShaderEnabled();

    default boolean isGpuSkinningEnabled() { return false; }

    default boolean isGpuMorphEnabled() { return false; }

    default int getMaxBones() { return 2048; }

    default boolean isFirstPersonModelEnabled() { return false; }

    default float getFirstPersonCameraForwardOffset() { return 0.0f; }

    default float getFirstPersonCameraVerticalOffset() { return 0.0f; }

    default boolean isDebugHudEnabled() { return false; }

    default int getTextureCacheBudgetMB() { return 256; }
}
