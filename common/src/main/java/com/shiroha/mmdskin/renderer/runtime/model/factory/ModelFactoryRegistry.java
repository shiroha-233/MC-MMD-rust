package com.shiroha.mmdskin.renderer.runtime.model.factory;

import com.shiroha.mmdskin.renderer.runtime.mode.RenderModeManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 模型工厂注册器。
 */
public final class ModelFactoryRegistry {
    private static final Logger logger = LogManager.getLogger();

    private static boolean registered = false;

    private ModelFactoryRegistry() {

    }

    public static void registerAll() {
        if (registered) {
            return;
        }

        RenderModeManager.registerFactory(new OpenGLModelFactory());
        RenderModeManager.registerFactory(new GpuSkinningModelFactory());

        registered = true;
    }
}
