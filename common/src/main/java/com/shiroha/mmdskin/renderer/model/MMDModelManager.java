package com.shiroha.mmdskin.renderer.model;

import com.shiroha.mmdskin.MmdSkinClient;
import com.shiroha.mmdskin.renderer.animation.MMDAnimManager;
import com.shiroha.mmdskin.renderer.core.EntityAnimState;
import com.shiroha.mmdskin.renderer.core.IMMDModel;
import com.shiroha.mmdskin.renderer.core.IrisCompat;
import com.shiroha.mmdskin.renderer.core.ModelCache;
import com.shiroha.mmdskin.renderer.core.RenderModeManager;
import com.shiroha.mmdskin.renderer.model.factory.ModelFactoryRegistry;
import com.shiroha.mmdskin.maid.MaidMMDModelManager;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * MMD 模型管理器 (SRP - 单一职责原则)
 * 
 * 负责模型的加载和高层管理。
 * 
 * 职责拆分：
 * - 缓存管理：委托给 {@link ModelCache}
 * - 渲染模式：委托给 {@link RenderModeManager}
 * - 实体状态：使用 {@link EntityAnimState}
 * - 模型创建：委托给已注册的 {@link com.shiroha.mmdskin.renderer.core.IMMDModelFactory}
 */
public class MMDModelManager {
    static final Logger logger = LogManager.getLogger();
    
    private static ModelCache<Model> modelCache;

    public static void Init() {
        // 先注册工厂，再初始化 RenderModeManager
        ModelFactoryRegistry.registerAll();
        
        modelCache = new ModelCache<>("MMDModel");
        RenderModeManager.init();
        logger.info("MMDModelManager 初始化完成");
    }

    /**
     * 获取模型（带缓存和自动清理）
     */
    public static Model GetModel(String modelName, String cacheKey) {
        String fullCacheKey = modelName + "_" + cacheKey;
        
        ModelCache.CacheEntry<Model> entry = modelCache.get(fullCacheKey);
        if (entry != null) {
            return entry.value;
        }
        
        // 阴影 pass 期间不创建新模型：Iris 阴影渲染时 OpenGL 帧缓冲区状态特殊，
        // 模型创建中的 glTexImage2D 等纹理操作会导致 AMD 驱动崩溃 (atio6axx.dll EXCEPTION_ACCESS_VIOLATION)。
        // 返回 null 后，模型会在下一次主渲染 pass 中正常创建。
        if (IrisCompat.isRenderingShadows()) {
            return null;
        }
        
        modelCache.checkAndClean(MMDModelManager::disposeModel);
        
        ModelInfo modelInfo = ModelInfo.findByFolderName(modelName);
        if (modelInfo == null) {
            logger.warn("模型未找到: " + modelName);
            return null;
        }
        
        IMMDModel m = RenderModeManager.createModel(modelInfo, 3);
        if (m == null) {
            return null;
        }
        
        try {
            MMDAnimManager.AddModel(m);
            Model model = createModelWrapper(fullCacheKey, m, modelName);
            modelCache.put(fullCacheKey, model);
            logger.info("模型已加载: {} (缓存: {})", fullCacheKey, modelCache.size());
            return model;
        } catch (Exception e) {
            logger.error("添加模型失败: " + fullCacheKey, e);
            return null;
        }
    }

    public static Model GetModel(String modelName) {
        return GetModel(modelName, "default");
    }
    
    /**
     * 记录模型切换事件，触发延迟清理
     */
    public static void onModelSwitch() {
        modelCache.onSwitch();
    }
    
    /**
     * 强制重载指定模型（立即清除缓存并重新加载）
     * 适用于模型切换时需要立即释放旧模型资源的场景
     * 
     * @param modelName 模型名称
     */
    public static void forceReloadModel(String modelName) {
        // 收集所有与该模型相关的缓存键
        java.util.List<String> keysToRemove = new java.util.ArrayList<>();
        modelCache.forEach((key, entry) -> {
            if (key.startsWith(modelName + "_")) {
                keysToRemove.add(key);
            }
        });
        
        // 释放并移除
        for (String key : keysToRemove) {
            ModelCache.CacheEntry<Model> entry = modelCache.remove(key);
            if (entry != null) {
                disposeModel(entry.value);
                logger.info("强制释放模型: {}", key);
            }
        }
    }
    
    /**
     * 强制重载所有模型（立即清除所有缓存）
     * 适用于渲染模式切换（CPU/GPU）时需要完全重建所有模型的场景
     */
    public static void forceReloadAllModels() {
        modelCache.clear(MMDModelManager::disposeModel);
        MaidMMDModelManager.invalidateLoadedModels();
        logger.info("强制重载所有模型完成");
    }
    
    /**
     * 定期检查，在渲染循环中调用
     */
    public static void tick() {
        modelCache.tick(MMDModelManager::disposeModel);
    }
    
    /**
     * 创建模型包装器
     */
    private static Model createModelWrapper(String name, IMMDModel model, String modelName) {
        ModelWithEntityData m = new ModelWithEntityData();
        m.entityName = name;
        m.model = model;
        m.modelName = modelName;
        m.entityData = new EntityAnimState(3);
        
        model.ResetPhysics();
        model.ChangeAnim(MMDAnimManager.GetAnimModel(model, "idle"), 0);
        return m;
    }
    
    public static void ReloadModel() {
        modelCache.clear(MMDModelManager::disposeModel);
        MaidMMDModelManager.invalidateLoadedModels();
        logger.info("模型已重载");
    }

    /**
     * 释放模型资源
     */
    private static void disposeModel(Model model) {
        try {
            // 使用多态调用 dispose()，无需 instanceof 判断
            model.model.dispose();
            MMDAnimManager.DeleteModel(model.model);
            
            // 释放 EntityAnimState 资源
            if (model instanceof ModelWithEntityData med && med.entityData != null) {
                med.entityData.dispose();
            }
        } catch (Exception e) {
            logger.error("删除模型失败", e);
        }
    }
    
    public static class Model {
        public IMMDModel model;
        String entityName;
        String modelName;
        public Properties properties = new Properties();
        boolean isPropertiesLoaded = false;

        public void loadModelProperties(boolean forceReload) {
            if (isPropertiesLoaded && !forceReload) {
                return;
            }
            
            ModelInfo info = ModelInfo.findByFolderName(modelName);
            if (info == null) {
                logger.warn("模型属性加载失败，模型未找到: {}", modelName);
                isPropertiesLoaded = true;
                return;
            }
            
            String path2Properties = info.getFolderPath() + "/model.properties";
            try (InputStream istream = new FileInputStream(path2Properties)) {
                properties.load(istream);
                logger.debug("模型属性加载成功: {}", modelName);
            } catch (IOException e) {
                logger.debug("模型属性文件未找到: {}", modelName);
            }
            isPropertiesLoaded = true;
            MmdSkinClient.reloadProperties = false;
        } 
    }

    public static class ModelWithEntityData extends Model {
        public EntityAnimState entityData;
    }
}
