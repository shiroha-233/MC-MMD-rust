package com.shiroha.mmdskin.config;

/**
 * 渲染相关配置子接口
 * 包含 OpenGL、模型池、GPU 加速、第一人称等核心渲染设置
 */
public interface IRenderConfig {
    boolean isOpenGLLightingEnabled();
    int getModelPoolMaxCount();
    boolean isMMDShaderEnabled();

    /** GPU 蒙皮启用状态（默认关闭） */
    default boolean isGpuSkinningEnabled() { return false; }

    /** GPU Morph 启用状态（默认关闭） */
    default boolean isGpuMorphEnabled() { return false; }

    /** GPU 蒙皮最大骨骼数量（默认2048） */
    default int getMaxBones() { return 2048; }

    /** 第一人称模型显示是否启用（默认 false） */
    default boolean isFirstPersonModelEnabled() { return false; }

    /** 第一人称相机前后偏移（默认 0.0） */
    default float getFirstPersonCameraForwardOffset() { return 0.0f; }

    /** 第一人称相机上下偏移（默认 0.0） */
    default float getFirstPersonCameraVerticalOffset() { return 0.0f; }
}
