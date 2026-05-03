package com.shiroha.mmdskin.render.backend;

import com.shiroha.mmdskin.bridge.runtime.NativeRenderBackendPort;
import com.shiroha.mmdskin.bridge.runtime.PlatformCapabilityPort;
import com.shiroha.mmdskin.config.RuntimeConfigPort;
import com.shiroha.mmdskin.model.runtime.ModelInstance;
import com.shiroha.mmdskin.render.backend.factory.ModelFactoryRegistry;
import com.shiroha.mmdskin.render.backend.mode.RenderModeManager;
import com.shiroha.mmdskin.render.port.RenderBackendSettingsPort;

/** 文件职责：封装渲染后端注册、选择与配置入口。 */
public final class RenderBackendRegistry implements RenderBackendSettingsPort {
    private final NativeRenderBackendPort nativeRenderBackendPort;
    private final PlatformCapabilityPort platformCapabilityPort;
    private final RuntimeConfigPort runtimeConfigPort;
    private volatile boolean shaderEnabled;
    private volatile int shaderPipelineMode;

    public RenderBackendRegistry(NativeRenderBackendPort nativeRenderBackendPort,
                                 PlatformCapabilityPort platformCapabilityPort,
                                 RuntimeConfigPort runtimeConfigPort) {
        if (nativeRenderBackendPort == null) {
            throw new IllegalArgumentException("nativeRenderBackendPort cannot be null");
        }
        if (platformCapabilityPort == null) {
            throw new IllegalArgumentException("platformCapabilityPort cannot be null");
        }
        if (runtimeConfigPort == null) {
            throw new IllegalArgumentException("runtimeConfigPort cannot be null");
        }
        this.nativeRenderBackendPort = nativeRenderBackendPort;
        this.platformCapabilityPort = platformCapabilityPort;
        this.runtimeConfigPort = runtimeConfigPort;
    }

    public void initialize() {
        ModelFactoryRegistry.registerAll(nativeRenderBackendPort, platformCapabilityPort);
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
        setGpuSkinningEnabled(runtimeConfigPort.isGpuSkinningEnabled());
        setShaderEnabled(runtimeConfigPort.isMmdShaderEnabled());
    }
}
