package com.shiroha.mmdskin.render.backend;

import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.model.runtime.ModelInstance;
import com.shiroha.mmdskin.render.backend.factory.ModelFactoryRegistry;
import com.shiroha.mmdskin.render.backend.mode.RenderModeManager;
import com.shiroha.mmdskin.render.port.RenderBackendSettingsPort;

/** 文件职责：封装渲染后端注册、选择与配置入口。 */
public final class RenderBackendRegistry implements RenderBackendSettingsPort {
    private volatile boolean shaderEnabled;
    private volatile int shaderPipelineMode;

    public void initialize() {
        ModelFactoryRegistry.registerAll();
        RenderModeManager.init();
        applyConfig();
    }

    public ModelInstance createModelFromHandle(long modelHandle, String modelDir, boolean isPmd) {
        return RenderModeManager.createModelFromHandle(modelHandle, modelDir, isPmd);
    }

    public ModelInstance createModel(String modelFilename, String modelDir, boolean isPmd, long layerCount) {
        return RenderModeManager.createModel(modelFilename, modelDir, isPmd, layerCount);
    }

    @Override
    public void setGpuSkinningEnabled(boolean enabled) {
        RenderModeManager.setUseGpuSkinning(enabled);
    }

    @Override
    public boolean isGpuSkinningEnabled() {
        return RenderModeManager.isUseGpuSkinning();
    }

    @Override
    public void setShaderEnabled(boolean enabled) {
        this.shaderEnabled = enabled;
    }

    @Override
    public boolean isShaderEnabled() {
        return shaderEnabled;
    }

    public int shaderPipelineMode() {
        return shaderPipelineMode;
    }

    public void setShaderPipelineMode(int shaderPipelineMode) {
        this.shaderPipelineMode = shaderPipelineMode;
    }

    @Override
    public String currentModeDescription() {
        return RenderModeManager.getCurrentModeDescription();
    }

    private void applyConfig() {
        setGpuSkinningEnabled(ConfigManager.isGpuSkinningEnabled());
        setShaderEnabled(ConfigManager.isMMDShaderEnabled());
    }
}
