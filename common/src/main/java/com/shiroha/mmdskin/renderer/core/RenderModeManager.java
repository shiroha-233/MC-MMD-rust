package com.shiroha.mmdskin.renderer.core;

import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.renderer.model.ModelInfo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 渲染模式管理器 (OCP - 开闭原则, DIP - 依赖倒置原则)
 * 
 * 使用工厂注册机制管理不同的渲染模式。
 * 新的渲染模式只需注册工厂，无需修改此类。
 * 启用状态由本类通过 RenderCategory 统一管理，工厂本身不持有状态。
 */
public class RenderModeManager {
    private static final Logger logger = LogManager.getLogger();
    
    /** 已注册的工厂列表（线程安全） */
    private static final List<IMMDModelFactory> factories = new CopyOnWriteArrayList<>();
    
    /** 各渲染分类的启用状态（线程安全，默认禁用） */
    private static final Map<RenderCategory, Boolean> enabledStates = new ConcurrentHashMap<>();
    
    /** 是否已初始化 */
    private static volatile boolean initialized = false;
    
    /**
     * 注册模型工厂
     */
    public static synchronized void registerFactory(IMMDModelFactory factory) {
        if (factory == null) return;
        
        for (IMMDModelFactory existing : factories) {
            if (existing.getCategory() == factory.getCategory()) {
                logger.warn("渲染分类 {} 已存在，跳过注册", factory.getCategory());
                return;
            }
        }
        
        factories.add(factory);
        // CPU_SKINNING 默认启用（基础回退），其余默认禁用
        enabledStates.putIfAbsent(factory.getCategory(), 
            factory.getCategory() == RenderCategory.CPU_SKINNING);
        
        logger.info("注册渲染工厂: {} (分类: {}, 优先级: {}, 可用: {})", 
            factory.getModeName(), factory.getCategory(), factory.getPriority(), factory.isAvailable());
    }
    
    /**
     * 取消注册工厂
     */
    public static void unregisterFactory(RenderCategory category) {
        factories.removeIf(f -> f.getCategory() == category);
        enabledStates.remove(category);
    }
    
    /**
     * 从配置初始化渲染模式
     */
    public static void init() {
        if (initialized) return;
        
        syncFactoryStates();
        initialized = true;
        logger.info("RenderModeManager 初始化完成 (已注册 {} 个工厂)", factories.size());
        logger.info("当前渲染模式: {}", getCurrentModeDescription());
    }
    
    /**
     * 从配置同步工厂启用状态
     */
    private static void syncFactoryStates() {
        enabledStates.put(RenderCategory.GPU_SKINNING, ConfigManager.isGpuSkinningEnabled());
    }
    
    // ==================== 启用状态管理 ====================
    
    /**
     * 检查指定渲染分类是否启用
     */
    public static boolean isEnabled(RenderCategory category) {
        return Boolean.TRUE.equals(enabledStates.get(category));
    }
    
    /**
     * 设置指定渲染分类的启用状态
     */
    public static void setEnabled(RenderCategory category, boolean enabled) {
        enabledStates.put(category, enabled);
        logger.info("渲染分类 {} : {}", category, enabled ? "启用" : "禁用");
    }
    
    public static void setUseGpuSkinning(boolean enabled) {
        setEnabled(RenderCategory.GPU_SKINNING, enabled);
    }
    
    public static boolean isUseGpuSkinning() {
        return isEnabled(RenderCategory.GPU_SKINNING);
    }
    
    // ==================== 工厂查询 ====================
    
    /**
     * 获取当前渲染模式的描述
     */
    public static String getCurrentModeDescription() {
        List<IMMDModelFactory> candidates = getOrderedFactories(false, false);
        if (!candidates.isEmpty()) {
            return candidates.get(0).getModeName();
        }
        return "无可用渲染模式";
    }
    
    /**
     * 获取按优先级排序的可用工厂列表
     * 
     * @param isPMD 是否需要 PMD 支持（过滤不支持的工厂）
     * @param includeDisabled 是否包含未启用的工厂
     * @return 按优先级降序排列的工厂列表
     */
    private static List<IMMDModelFactory> getOrderedFactories(boolean isPMD, boolean includeDisabled) {
        List<IMMDModelFactory> result = new ArrayList<>();
        
        List<IMMDModelFactory> sorted = new ArrayList<>(factories);
        sorted.sort(Comparator.comparingInt(IMMDModelFactory::getPriority).reversed());
        
        for (IMMDModelFactory factory : sorted) {
            if (!factory.isAvailable()) continue;
            if (isPMD && !factory.supportsPMD()) continue;
            if (!includeDisabled && !isEnabled(factory.getCategory())) continue;
            result.add(factory);
        }
        return result;
    }
    
    // ==================== 模型创建 ====================
    
    /**
     * 根据当前渲染模式创建模型
     */
    public static IMMDModel createModel(String modelFilename, String modelDir, boolean isPMD, long layerCount) {
        syncFactoryStates();
        
        // 一次性获取所有可用工厂，分为已启用和回退两组
        List<IMMDModelFactory> enabled = getOrderedFactories(isPMD, false);
        IMMDModel model = tryCreateWithFactories(enabled, modelFilename, modelDir, isPMD, layerCount);
        if (model != null) return model;
        
        // 回退：尝试可用但未启用的工厂
        List<IMMDModelFactory> all = getOrderedFactories(isPMD, true);
        all.removeAll(enabled);
        model = tryCreateWithFactories(all, modelFilename, modelDir, isPMD, layerCount);
        if (model != null) return model;
        
        logger.error("所有工厂都无法创建模型: {}", modelFilename);
        return null;
    }
    
    /**
     * 根据模型信息创建模型（便捷方法）
     */
    public static IMMDModel createModel(ModelInfo modelInfo, long layerCount) {
        if (modelInfo == null) return null;
        return createModel(
            modelInfo.getModelFilePath(), modelInfo.getFolderPath(),
            modelInfo.isPMD(), layerCount);
    }
    
    /**
     * 从已加载的模型句柄创建渲染实例（Phase 2：GL 资源创建，必须在渲染线程调用）
     */
    public static IMMDModel createModelFromHandle(long modelHandle, String modelDir, boolean isPMD) {
        syncFactoryStates();
        
        // 一次性获取所有可用工厂，分为已启用和回退两组
        List<IMMDModelFactory> enabled = getOrderedFactories(isPMD, false);
        IMMDModel model = tryCreateFromHandle(enabled, modelHandle, modelDir);
        if (model != null) return model;
        
        // 回退：尝试可用但未启用的工厂
        List<IMMDModelFactory> all = getOrderedFactories(isPMD, true);
        all.removeAll(enabled);
        model = tryCreateFromHandle(all, modelHandle, modelDir);
        if (model != null) return model;
        
        logger.error("所有工厂都无法从句柄创建模型");
        return null;
    }
    
    // ==================== 内部方法 ====================
    
    private static IMMDModel tryCreateWithFactories(List<IMMDModelFactory> candidates,
            String modelFilename, String modelDir, boolean isPMD, long layerCount) {
        for (IMMDModelFactory factory : candidates) {
            logger.info("尝试使用 {} 创建模型: {}", factory.getModeName(), modelFilename);
            try {
                IMMDModel model = factory.createModel(modelFilename, modelDir, isPMD, layerCount);
                if (model != null) return model;
                logger.warn("{} 创建失败，尝试下一个工厂", factory.getModeName());
            } catch (Exception e) {
                logger.error("{} 创建异常: {}", factory.getModeName(), e.getMessage());
            }
        }
        return null;
    }
    
    private static IMMDModel tryCreateFromHandle(List<IMMDModelFactory> candidates,
            long modelHandle, String modelDir) {
        for (IMMDModelFactory factory : candidates) {
            logger.info("尝试使用 {} 从句柄创建模型", factory.getModeName());
            try {
                IMMDModel model = factory.createModelFromHandle(modelHandle, modelDir);
                if (model != null) return model;
                logger.warn("{} 从句柄创建失败，尝试下一个工厂", factory.getModeName());
            } catch (Exception e) {
                logger.error("{} 从句柄创建异常: {}", factory.getModeName(), e.getMessage());
            }
        }
        return null;
    }
    
    /**
     * 获取所有已注册的工厂
     */
    public static List<IMMDModelFactory> getFactories() {
        return new ArrayList<>(factories);
    }
}
