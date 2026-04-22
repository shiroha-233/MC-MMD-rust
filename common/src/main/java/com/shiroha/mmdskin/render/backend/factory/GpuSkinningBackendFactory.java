package com.shiroha.mmdskin.render.backend.factory;

import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.model.runtime.ModelInstance;
import com.shiroha.mmdskin.bridge.runtime.NativeRuntimeBridgeHolder;
import com.shiroha.mmdskin.render.backend.gpu.GpuSkinningModelInstance;
import com.shiroha.mmdskin.render.backend.mode.ModelInstanceFactory;
import com.shiroha.mmdskin.render.backend.mode.RenderCategory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * GPU 蒙皮模型工厂。
 */
public class GpuSkinningBackendFactory implements ModelInstanceFactory {
    private static final Logger logger = LogManager.getLogger();

    private static final int PRIORITY = 10;

    @Override
    public RenderCategory getCategory() {
        return RenderCategory.GPU_SKINNING;
    }

    @Override
    public String getModeName() {
        return "GPU蒙皮";
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public boolean isAvailable() {

        if (NativeRuntimeBridgeHolder.get().isAndroid()) return false;

        return true;
    }

    @Override
    public boolean isEnabledInCurrentEnvironment() {
        return isAvailable() && ConfigManager.isGpuSkinningEnabled();
    }

    @Override
    public ModelInstance createModel(String modelFilename, String modelDir, boolean isPMD, long layerCount) {
        if (!isAvailable()) {
            logger.warn("GPU 蒙皮不可用，无法创建模型");
            return null;
        }

        try {
            return GpuSkinningModelInstance.create(modelFilename, modelDir, isPMD, layerCount);
        } catch (Exception e) {
            logger.error("GPU 蒙皮模型创建失败: {}", modelFilename, e);
            return null;
        }
    }

    @Override
    public ModelInstance createModelFromHandle(long modelHandle, String modelDir) {
        try {
            return GpuSkinningModelInstance.createFromHandle(modelHandle, modelDir);
        } catch (Exception e) {
            logger.error("GPU 蒙皮模型（从句柄）创建失败", e);
            return null;
        }
    }
}
