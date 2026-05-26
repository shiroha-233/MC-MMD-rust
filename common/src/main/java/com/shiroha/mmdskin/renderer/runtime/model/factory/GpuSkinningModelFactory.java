package com.shiroha.mmdskin.renderer.runtime.model.factory;

import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.renderer.api.IMMDModel;
import com.shiroha.mmdskin.renderer.runtime.bridge.ModelRuntimeBridgeHolder;
import com.shiroha.mmdskin.renderer.runtime.mode.IMMDModelFactory;
import com.shiroha.mmdskin.renderer.runtime.mode.RenderCategory;
import com.shiroha.mmdskin.renderer.runtime.model.gpu.MMDModelGpuSkinning;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * GPU 蒙皮模型工厂。
 */
public class GpuSkinningModelFactory implements IMMDModelFactory {
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

        if (ModelRuntimeBridgeHolder.get().isAndroid()) return false;

        return true;
    }

    @Override
    public boolean isEnabledInCurrentEnvironment() {
        return isAvailable() && ConfigManager.isGpuSkinningEnabled();
    }

    @Override
    public IMMDModel createModel(String modelFilename, String modelDir, boolean isPMD, long layerCount) {
        if (!isAvailable()) {
            logger.warn("GPU 蒙皮不可用，无法创建模型");
            return null;
        }

        try {
            return MMDModelGpuSkinning.Create(modelFilename, modelDir, isPMD, layerCount);
        } catch (Exception e) {
            logger.error("GPU 蒙皮模型创建失败: {}", modelFilename, e);
            return null;
        }
    }

    @Override
    public IMMDModel createModelFromHandle(long modelHandle, String modelDir) {
        try {
            return MMDModelGpuSkinning.createFromHandle(modelHandle, modelDir);
        } catch (Exception e) {
            logger.error("GPU 蒙皮模型（从句柄）创建失败", e);
            return null;
        }
    }
}
