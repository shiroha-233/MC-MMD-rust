package com.shiroha.mmdskin.renderer.model.factory;

import com.shiroha.mmdskin.renderer.core.IMMDModel;
import com.shiroha.mmdskin.renderer.core.IMMDModelFactory;
import com.shiroha.mmdskin.renderer.model.MMDModelOpenGL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * CPU 蒙皮模型工厂
 * 
 * 最基础的渲染模式，使用 CPU 进行蒙皮计算。
 * 优先级最低，作为其他模式失败时的回退选项。
 */
public class OpenGLModelFactory implements IMMDModelFactory {
    private static final Logger logger = LogManager.getLogger();
    
    /** 优先级：最低（作为回退） */
    private static final int PRIORITY = 0;
    
    private boolean enabled = true;
    
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
        // CPU 蒙皮始终可用
        return true;
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
