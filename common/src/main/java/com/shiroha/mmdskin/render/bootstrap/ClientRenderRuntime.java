package com.shiroha.mmdskin.render.bootstrap;

import com.shiroha.mmdskin.maid.MaidModelRepositoryExtension;
import com.shiroha.mmdskin.model.port.ModelDiagnosticsPort;
import com.shiroha.mmdskin.model.port.ModelRepositoryPort;
import com.shiroha.mmdskin.model.runtime.ModelRepository;
import com.shiroha.mmdskin.render.backend.RenderBackendRegistry;
import com.shiroha.mmdskin.render.port.RenderBackendSettingsPort;
import com.shiroha.mmdskin.texture.runtime.TextureRepository;

/** 文件职责：作为客户端渲染运行时的唯一组合根，统一持有仓储与后端服务。 */
public final class ClientRenderRuntime {
    private static volatile ClientRenderRuntime instance;

    private final RenderBackendRegistry renderBackendRegistry;
    private final ModelRepository modelRepository;

    private ClientRenderRuntime() {
        this.renderBackendRegistry = new RenderBackendRegistry();
        TextureRepository.Init();
        this.renderBackendRegistry.initialize();
        this.modelRepository = new ModelRepository(renderBackendRegistry, MaidModelRepositoryExtension.INSTANCE);
    }

    public static synchronized void initialize() {
        if (instance == null) {
            instance = new ClientRenderRuntime();
        }
    }

    public static ClientRenderRuntime get() {
        if (instance == null) {
            throw new IllegalStateException("ClientRenderRuntime has not been initialized");
        }
        return instance;
    }

    public ModelRepositoryPort modelRepository() {
        return modelRepository;
    }

    public ModelDiagnosticsPort modelDiagnostics() {
        return modelRepository;
    }

    public RenderBackendSettingsPort renderBackendSettings() {
        return renderBackendRegistry;
    }

    public RenderBackendRegistry renderBackendRegistry() {
        return renderBackendRegistry;
    }
}
