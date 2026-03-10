package com.shiroha.mmdskin.renderer.runtime.model.factory;

import com.shiroha.mmdskin.renderer.api.IMMDModel;
import com.shiroha.mmdskin.renderer.runtime.mode.IMMDModelFactory;
import com.shiroha.mmdskin.renderer.runtime.mode.RenderCategory;
import com.shiroha.mmdskin.renderer.runtime.model.opengl.MMDModelOpenGL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * CPU 蒙皮模型工厂。
 */
public class OpenGLModelFactory implements IMMDModelFactory {
    private static final Logger logger = LogManager.getLogger();

    private static final int PRIORITY = 0;

    @Override
    public RenderCategory getCategory() {
        return RenderCategory.CPU_SKINNING;
    }

    @Override
    public String getModeName() {
        return "CPU蒙皮";
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public boolean isAvailable() {

        return true;
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public IMMDModel createModel(String modelFilename, String modelDir, boolean isPMD, long layerCount) {
        try {
            return MMDModelOpenGL.Create(modelFilename, modelDir, isPMD, layerCount);
        } catch (Throwable e) {
            logger.error("CPU 蒙皮模型创建失败: {}", modelFilename, e);
            return null;
        }
    }

    @Override
    public IMMDModel createModelFromHandle(long modelHandle, String modelDir) {
        try {
            return MMDModelOpenGL.createFromHandle(modelHandle, modelDir);
        } catch (Throwable e) {
            logger.error("CPU 蒙皮模型（从句柄）创建失败", e);
            return null;
        }
    }
}
