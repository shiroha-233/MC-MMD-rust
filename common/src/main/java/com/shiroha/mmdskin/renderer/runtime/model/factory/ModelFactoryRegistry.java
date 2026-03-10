package com.shiroha.mmdskin.renderer.runtime.model.factory;

import com.shiroha.mmdskin.renderer.runtime.mode.RenderModeManager;

import java.util.List;
import java.util.function.Supplier;

/**
 * 模型工厂注册器。
 */
public final class ModelFactoryRegistry {
    private static final List<Supplier<com.shiroha.mmdskin.renderer.runtime.mode.IMMDModelFactory>> BUILT_IN_FACTORIES = List.of(
            OpenGLModelFactory::new,
            GpuSkinningModelFactory::new
    );

    private static boolean registered = false;

    private ModelFactoryRegistry() {

    }

    public static void registerAll() {
        if (registered) {
            return;
        }

        for (Supplier<com.shiroha.mmdskin.renderer.runtime.mode.IMMDModelFactory> factorySupplier : BUILT_IN_FACTORIES) {
            RenderModeManager.registerFactory(factorySupplier.get());
        }

        registered = true;
    }
}
