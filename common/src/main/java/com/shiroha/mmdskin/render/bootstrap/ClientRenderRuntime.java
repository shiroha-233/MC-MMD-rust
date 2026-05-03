package com.shiroha.mmdskin.render.bootstrap;

import com.shiroha.mmdskin.bridge.runtime.NativeAnimationBridgeHolder;
import com.shiroha.mmdskin.bridge.runtime.NativeModelLoadPort;
import com.shiroha.mmdskin.bridge.runtime.NativeModelPort;
import com.shiroha.mmdskin.bridge.runtime.NativeModelQueryPort;
import com.shiroha.mmdskin.bridge.runtime.NativeMorphPort;
import com.shiroha.mmdskin.bridge.runtime.NativeRenderBackendPort;
import com.shiroha.mmdskin.bridge.runtime.NativeRuntimeBridgeHolder;
import com.shiroha.mmdskin.bridge.runtime.NativeRuntimePort;
import com.shiroha.mmdskin.bridge.runtime.NativeScenePort;
import com.shiroha.mmdskin.bridge.runtime.NativeTextureBridgeHolder;
import com.shiroha.mmdskin.bridge.runtime.PlatformCapabilityPort;
import com.shiroha.mmdskin.compat.vr.DefaultVrRuntimePort;
import com.shiroha.mmdskin.config.PhysicsConfigSnapshot;
import com.shiroha.mmdskin.config.RuntimeConfigPort;
import com.shiroha.mmdskin.config.RuntimeConfigPortHolder;
import com.shiroha.mmdskin.expression.ExpressionApplicationService;
import com.shiroha.mmdskin.expression.ModelMorphCatalog;
import com.shiroha.mmdskin.maid.MaidModelRepositoryExtension;
import com.shiroha.mmdskin.model.port.ModelDiagnosticsPort;
import com.shiroha.mmdskin.model.port.ModelRepositoryPort;
import com.shiroha.mmdskin.model.runtime.DefaultModelRuntimeAccessPort;
import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.model.runtime.ModelRepository;
import com.shiroha.mmdskin.player.render.ItemRenderHelper;
import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import com.shiroha.mmdskin.api.MmdSkinApi;
import com.shiroha.mmdskin.compat.vr.VRBoneDriver;
import com.shiroha.mmdskin.debug.client.PerformanceHud;
import com.shiroha.mmdskin.render.backend.RenderBackendRegistry;
import com.shiroha.mmdskin.render.pipeline.LivingEntityModelStateHelper;
import com.shiroha.mmdskin.render.port.RenderBackendSettingsPort;
import com.shiroha.mmdskin.scene.client.SceneModelManager;
import com.shiroha.mmdskin.texture.runtime.TextureRepository;

/** 文件职责：作为客户端渲染运行时的唯一组合根，统一持有仓储与后端服务。 */
public final class ClientRenderRuntime {
    private static volatile ClientRenderRuntime instance;

    private final NativeRuntimePort nativeRuntimePort;
    private final RuntimeConfigPort runtimeConfigPort;
    private final RenderBackendRegistry renderBackendRegistry;
    private final ModelRepository modelRepository;

    private ClientRenderRuntime() {
        this.nativeRuntimePort = NativeRuntimeBridgeHolder.get();
        this.runtimeConfigPort = RuntimeConfigPort.fromConfigManager();
        RuntimeConfigPortHolder.set(runtimeConfigPort);
        NativeRenderBackendPort nativeRenderBackendPort = nativeRuntimePort;
        PlatformCapabilityPort platformCapabilityPort = nativeRuntimePort;
        this.renderBackendRegistry = new RenderBackendRegistry(nativeRenderBackendPort, platformCapabilityPort, runtimeConfigPort);
        TextureRepository.Init();
        this.renderBackendRegistry.initialize();
        NativeModelPort modelPort = (NativeModelPort) nativeRuntimePort;
        NativeModelLoadPort modelLoadPort = (NativeModelLoadPort) nativeRuntimePort;
        NativeModelQueryPort modelQueryPort = (NativeModelQueryPort) nativeRuntimePort;
        NativeScenePort scenePort = (NativeScenePort) nativeRuntimePort;
        NativeMorphPort morphPort = (NativeMorphPort) nativeRuntimePort;
        this.modelRepository = new ModelRepository(
                new DefaultModelRuntimeAccessPort(renderBackendRegistry, modelLoadPort, modelPort),
                MaidModelRepositoryExtension.INSTANCE);
        ManagedModel.configureRuntimeCollaborators(NativeAnimationBridgeHolder.get(), nativeRuntimePort);
        ItemRenderHelper.configureRuntimeCollaborators(nativeRuntimePort);
        TextureRepository.configureRuntimeCollaborators(NativeTextureBridgeHolder.get());
        VRBoneDriver.configureRuntimeCollaborators(modelPort);
        PerformanceHud.configureRuntimeCollaborators(modelQueryPort);
        FirstPersonManager.configureVrRuntime(new DefaultVrRuntimePort());
        FirstPersonManager.configureNativeModelPort(modelPort);
        LivingEntityModelStateHelper.configureRuntimeCollaborators(scenePort);
        SceneModelManager.getInstance().configureRuntimeCollaborators(scenePort);
        ExpressionApplicationService.configureRuntimeCollaborators(morphPort);
        ModelMorphCatalog.configureRuntimeCollaborators(modelQueryPort);
        MmdSkinApi.configureRuntimeCollaborators(modelPort, modelQueryPort);
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

    public void applyPhysicsConfig(PhysicsConfigSnapshot physicsConfig) {
        nativeRuntimePort.applyPhysicsConfig(physicsConfig);
    }
}
