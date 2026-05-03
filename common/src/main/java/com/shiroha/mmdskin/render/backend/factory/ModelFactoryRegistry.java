package com.shiroha.mmdskin.render.backend.factory;

import com.shiroha.mmdskin.bridge.runtime.NativeRenderBackendPort;
import com.shiroha.mmdskin.bridge.runtime.PlatformCapabilityPort;
import com.shiroha.mmdskin.render.backend.mode.ModelInstanceFactory;
import com.shiroha.mmdskin.render.backend.mode.RenderModeManager;
import java.util.List;

/**
 * 模型工厂注册器。
 */
public final class ModelFactoryRegistry {
    private static boolean registered = false;

    private ModelFactoryRegistry() {

    }

    public static void registerAll(NativeRenderBackendPort nativeRenderBackendPort,
                                   PlatformCapabilityPort platformCapabilityPort) {
        if (registered) {
            return;
        }

        List<ModelInstanceFactory> builtInFactories = List.of(
                new CpuSkinningBackendFactory(nativeRenderBackendPort),
                new GpuSkinningBackendFactory(nativeRenderBackendPort, platformCapabilityPort)
        );
        for (ModelInstanceFactory factory : builtInFactories) {
            RenderModeManager.registerFactory(factory);
        }

        registered = true;
    }
}
