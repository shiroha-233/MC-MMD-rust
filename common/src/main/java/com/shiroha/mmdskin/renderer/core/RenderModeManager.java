package com.shiroha.mmdskin.renderer.core;

import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.renderer.model.ModelInfo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 渲染模式管理器 (OCP - 开闭原则, DIP - 依赖倒置原则)
 * 
 * 使用工厂注册机制管理不同的渲染模式。
 * 新的渲染模式只需注册工厂，无需修改此类。
 * 
 * 渲染模式优先级由工厂的 getPriority() 决定，数值越大优先级越高。
 */
public class RenderModeManager {
    private static final Logger logger = LogManager.getLogger();
    
    /** 已注册的工厂列表（线程安全） */
    private static final List<IMMDModelFactory> factories = new CopyOnWriteArrayList<>();
    
    /** 是否已初始化 */
    private static boolean initialized = false;
    
    /**
     * 注册模型工厂
     * 
     * @param factory 要注册的工厂
     */
    public static void registerFactory(IMMDModelFactory factory) {
        if (factory == null) {
            return;
        }
        
        // 避免重复注册
        for (IMMDModelFactory existing : factories) {
            if (existing.getModeName().equals(factory.getModeName())) {
                logger.warn("工厂 '{}' 已存在，跳过注册", factory.getModeName());
                return;
            }
        }
        
        factories.add(factory);
        logger.info("注册渲染工厂: {} (优先级: {}, 可用: {})", 
            factory.getModeName(), factory.getPriority(), factory.isAvailable());
    }
    
    /**
     * 取消注册工厂
     */
    public static void unregisterFactory(String modeName) {
        factories.removeIf(f -> f.getModeName().equals(modeName));
    }
    
    /**
     * 从配置初始化渲染模式
     */
    public static void init() {
        if (initialized) {
            return;
        }
        
        // 从配置同步工厂启用状态
        syncFactoryStates();
        
        initialized = true;
        logger.info("RenderModeManager 初始化完成 (已注册 {} 个工厂)", factories.size());
        logger.info("当前渲染模式: {}", getCurrentModeDescription());
    }
    
    /**
     * 从配置同步工厂启用状态
     */
    private static void syncFactoryStates() {
        boolean gpuEnabled = ConfigManager.isGpuSkinningEnabled();
        
        for (IMMDModelFactory factory : factories) {
            String name = factory.getModeName();
            if (name.contains("GPU") || name.contains("Gpu")) {
                factory.setEnabled(gpuEnabled);
            }
        }
    }
    
    /**
     * 设置是否使用 GPU 蒙皮
     */
    public static void setUseGpuSkinning(boolean enabled) {
        for (IMMDModelFactory factory : factories) {
            String name = factory.getModeName();
            if (name.contains("GPU") || name.contains("Gpu")) {
                factory.setEnabled(enabled);
            }
        }
        logger.info("GPU 蒙皮模式: {}", enabled ? "启用" : "禁用");
    }
    
    public static boolean isUseGpuSkinning() {
        for (IMMDModelFactory factory : factories) {
            String name = factory.getModeName();
            if ((name.contains("GPU") || name.contains("Gpu")) && factory.isEnabled()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 设置是否使用原生渲染模式（Iris 兼容）
     */
    public static void setUseNativeRender(boolean enabled) {
        for (IMMDModelFactory factory : factories) {
            String name = factory.getModeName();
            if (name.contains("Native") || name.contains("原生")) {
                factory.setEnabled(enabled);
            }
        }
        logger.info("原生渲染模式: {}", enabled ? "启用" : "禁用");
    }
    
    public static boolean isUseNativeRender() {
        for (IMMDModelFactory factory : factories) {
            String name = factory.getModeName();
            if ((name.contains("Native") || name.contains("原生")) && factory.isEnabled()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 获取当前渲染模式的描述
     */
    public static String getCurrentModeDescription() {
        IMMDModelFactory activeFactory = getActiveFactory(false);
        if (activeFactory != null) {
            return activeFactory.getModeName();
        }
        return "无可用渲染模式";
    }
    
    /**
     * 获取当前激活的工厂（按优先级排序，返回第一个可用且启用的）
     * 
     * @param isPMD 是否为 PMD 格式（某些工厂可能不支持）
     */
    private static IMMDModelFactory getActiveFactory(boolean isPMD) {
        // 按优先级降序排序
        List<IMMDModelFactory> sorted = new ArrayList<>(factories);
        sorted.sort(Comparator.comparingInt(IMMDModelFactory::getPriority).reversed());
        
        for (IMMDModelFactory factory : sorted) {
            if (factory.isAvailable() && factory.isEnabled()) {
                // 原生渲染不支持 PMD
                if (isPMD && factory.getModeName().contains("Native")) {
                    continue;
                }
                return factory;
            }
        }
        
        // 回退：返回任何可用的工厂
        for (IMMDModelFactory factory : sorted) {
            if (factory.isAvailable()) {
                return factory;
            }
        }
        
        return null;
    }
    
    /**
     * 根据当前渲染模式创建模型
     * 
     * @param modelFilename 模型文件路径
     * @param modelDir 模型目录
     * @param isPMD 是否为 PMD 格式
     * @param layerCount 动画层数
     * @return 创建的模型实例，失败返回 null
     */
    public static IMMDModel createModel(String modelFilename, String modelDir, boolean isPMD, long layerCount) {
        // 每次创建模型时同步配置状态
        syncFactoryStates();
        
        // 按优先级尝试所有可用工厂
        List<IMMDModelFactory> sorted = new ArrayList<>(factories);
        sorted.sort(Comparator.comparingInt(IMMDModelFactory::getPriority).reversed());
        
        for (IMMDModelFactory factory : sorted) {
            if (!factory.isAvailable() || !factory.isEnabled()) {
                continue;
            }
            
            // 原生渲染不支持 PMD
            if (isPMD && factory.getModeName().contains("Native")) {
                continue;
            }
            
            logger.info("尝试使用 {} 创建模型: {}", factory.getModeName(), modelFilename);
            
            try {
                IMMDModel model = factory.createModel(modelFilename, modelDir, isPMD, layerCount);
                if (model != null) {
                    return model;
                }
                logger.warn("{} 创建失败，尝试下一个工厂", factory.getModeName());
            } catch (Exception e) {
                logger.error("{} 创建异常: {}", factory.getModeName(), e.getMessage());
            }
        }
        
        logger.error("所有工厂都无法创建模型: {}", modelFilename);
        return null;
    }
    
    /**
     * 根据模型信息创建模型（便捷方法）
     */
    public static IMMDModel createModel(ModelInfo modelInfo, long layerCount) {
        if (modelInfo == null) {
            return null;
        }
        return createModel(
            modelInfo.getModelFilePath(),
            modelInfo.getFolderPath(),
            modelInfo.isPMD(),
            layerCount
        );
    }
    
    /**
     * 从已加载的模型句柄创建渲染实例（Phase 2：GL 资源创建，必须在渲染线程调用）
     * 用于两阶段异步加载。
     * 
     * @param modelHandle 后台线程加载的模型句柄
     * @param modelDir 模型目录
     * @param isPMD 是否为 PMD 格式
     * @return 创建的模型实例，失败返回 null
     */
    public static IMMDModel createModelFromHandle(long modelHandle, String modelDir, boolean isPMD) {
        syncFactoryStates();
        
        List<IMMDModelFactory> sorted = new ArrayList<>(factories);
        sorted.sort(Comparator.comparingInt(IMMDModelFactory::getPriority).reversed());
        
        for (IMMDModelFactory factory : sorted) {
            if (!factory.isAvailable() || !factory.isEnabled()) {
                continue;
            }
            if (isPMD && factory.getModeName().contains("Native")) {
                continue;
            }

            logger.info("尝试使用 {} 从句柄创建模型", factory.getModeName());

            try {
                IMMDModel model = factory.createModelFromHandle(modelHandle, modelDir);
                if (model != null) {
                    return model;
                }
                logger.warn("{} 从句柄创建失败，尝试下一个工厂", factory.getModeName());
            } catch (Exception e) {
                logger.error("{} 从句柄创建异常: {}", factory.getModeName(), e.getMessage());
            }
        }
        
        // 回退：尝试任何可用工厂
        for (IMMDModelFactory factory : sorted) {
            if (factory.isAvailable()) {
                try {
                    IMMDModel model = factory.createModelFromHandle(modelHandle, modelDir);
                    if (model != null) return model;
                } catch (Exception e) {
                    logger.error("回退工厂 {} 从句柄创建异常", factory.getModeName(), e);
                }
            }
        }
        
        logger.error("所有工厂都无法从句柄创建模型");
        return null;
    }
    
    /**
     * 获取所有已注册的工厂
     */
    public static List<IMMDModelFactory> getFactories() {
        return new ArrayList<>(factories);
    }
}
