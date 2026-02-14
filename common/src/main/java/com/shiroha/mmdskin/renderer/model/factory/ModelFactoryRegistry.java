package com.shiroha.mmdskin.renderer.model.factory;

import com.shiroha.mmdskin.renderer.core.RenderModeManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 模型工厂注册器
 * 
 * 在模块初始化时注册所有工厂到 RenderModeManager。
 * 这样 core 层不需要知道具体的工厂实现。
 */
public final class ModelFactoryRegistry {
    private static final Logger logger = LogManager.getLogger();
    
    private static boolean registered = false;
    
    private ModelFactoryRegistry() {
        // 工具类，禁止实例化
    }
    
    /**
     * 注册所有模型工厂
     * 应该在 MMDModelManager.Init() 之前调用
     */
    public static void registerAll() {
        if (registered) {
            return;
        }
        
        logger.info("注册模型工厂...");
        
        // 注册所有工厂（按优先级从低到高）
        RenderModeManager.registerFactory(new OpenGLModelFactory());
        RenderModeManager.registerFactory(new GpuSkinningModelFactory());
        
        registered = true;
        logger.info("模型工厂注册完成");
    }
}
