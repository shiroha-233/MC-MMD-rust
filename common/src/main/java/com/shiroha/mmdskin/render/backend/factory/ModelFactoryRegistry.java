package com.shiroha.mmdskin.render.backend.factory;

import com.shiroha.mmdskin.render.backend.mode.ModelInstanceFactory;
import com.shiroha.mmdskin.render.backend.mode.RenderModeManager;
import java.util.List;
import java.util.function.Supplier;

/**
 * 模型工厂注册器。
 */
public final class ModelFactoryRegistry {
    private static final List<Supplier<ModelInstanceFactory>> BUILT_IN_FACTORIES = List.of(
            CpuSkinningBackendFactory::new,
            GpuSkinningBackendFactory::new
    );

    private static boolean registered = false;

    private ModelFactoryRegistry() {

    }

    public static void registerAll() {
        if (registered) {
            return;
        }

        for (Supplier<ModelInstanceFactory> factorySupplier : BUILT_IN_FACTORIES) {
            RenderModeManager.registerFactory(factorySupplier.get());
        }

        registered = true;
    }
}
