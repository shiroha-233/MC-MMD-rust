package com.shiroha.mmdskin.render.port;

/** 文件职责：向外暴露渲染后端与 shader 相关配置入口。 */
public interface RenderBackendSettingsPort {

    void setGpuSkinningEnabled(boolean enabled);

    boolean isGpuSkinningEnabled();

    void setShaderEnabled(boolean enabled);

    boolean isShaderEnabled();

    String currentModeDescription();
}
