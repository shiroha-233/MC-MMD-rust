package com.shiroha.mmdskin.renderer.model.factory;

import com.shiroha.mmdskin.renderer.core.IMMDModel;
import com.shiroha.mmdskin.renderer.core.IMMDModelFactory;
import com.shiroha.mmdskin.renderer.model.MMDModelNativeRender;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 原生渲染模型工厂（Iris 兼容）
 * 
 * 使用 Minecraft 原生 ShaderInstance 系统渲染。
 * Iris 可以正确拦截并应用光影效果。
 * 
 * 注意：不支持 PMD 格式模型。
 */
public class NativeRenderModelFactory implements IMMDModelFactory {
    private static final Logger logger = LogManager.getLogger();
    
    /** 优先级：最高（Iris 兼容是首选） */
    private static final int PRIORITY = 20;
    
    private boolean enabled = false;
    
    @Override
    public String getModeName() {
        return "Native渲染(Iris兼容)";
    }
    
    @Override
    public int getPriority() {
        return PRIORITY;
    }
    
    @Override
    public boolean isAvailable() {
        // 原生渲染始终可用（使用 Minecraft 自带系统）
        return true;
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        logger.debug("原生渲染工厂: {}", enabled ? "启用" : "禁用");
    }
    
    @Override
    public IMMDModel createModel(String modelFilename, String modelDir, boolean isPMD, long layerCount) {
        // 原生渲染不支持 PMD 格式
        if (isPMD) {
            logger.debug("原生渲染不支持 PMD 格式: {}", modelFilename);
            return null;
        }
        
        try {
            return MMDModelNativeRender.LoadModel(modelFilename, modelDir, layerCount);
        } catch (Exception e) {
            logger.error("原生渲染模型创建失败: {}", modelFilename, e);
            return null;
        }
    }
    
    @Override
    public IMMDModel createModelFromHandle(long modelHandle, String modelDir) {
        try {
            return MMDModelNativeRender.createFromHandle(modelHandle, modelDir);
        } catch (Exception e) {
            logger.error("原生渲染模型（从句柄）创建失败", e);
            return null;
        }
    }
}
