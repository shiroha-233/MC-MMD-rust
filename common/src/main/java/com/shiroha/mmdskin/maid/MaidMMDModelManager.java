package com.shiroha.mmdskin.maid;

import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.renderer.animation.MMDAnimManager;
import com.shiroha.mmdskin.renderer.core.IMMDModel;
import com.shiroha.mmdskin.renderer.model.MMDModelManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 女仆 MMD 模型管理器
 * 
 * 负责管理女仆实体与 MMD 模型的映射关系。
 * 每个女仆可以绑定一个 MMD 模型，用于替代原版渲染。
 */
public class MaidMMDModelManager {
    private static final Logger logger = LogManager.getLogger();
    
    // 女仆 UUID -> 模型名称 的映射
    private static final Map<UUID, String> maidModelBindings = new ConcurrentHashMap<>();
    
    // 女仆 UUID -> 已加载模型 的映射
    private static final Map<UUID, MMDModelManager.Model> loadedModels = new ConcurrentHashMap<>();
    
    /**
     * 初始化管理器
     */
    public static void init() {
    }
    
    /**
     * 为女仆绑定 MMD 模型
     * 
     * @param maidUUID 女仆 UUID
     * @param modelName 模型名称（文件夹名）
     */
    public static void bindModel(UUID maidUUID, String modelName) {
        if (modelName == null || modelName.isEmpty() || UIConstants.DEFAULT_MODEL_NAME.equals(modelName)) {
            // 移除绑定
            unbindModel(maidUUID);
            return;
        }
        
        String oldModel = maidModelBindings.get(maidUUID);
        if (modelName.equals(oldModel)) {
            return; // 相同模型，无需更新
        }
        
        // 清理旧模型
        if (loadedModels.containsKey(maidUUID)) {
            loadedModels.remove(maidUUID);
        }
        
        maidModelBindings.put(maidUUID, modelName);
    }
    
    /**
     * 解除女仆的模型绑定
     * 
     * @param maidUUID 女仆 UUID
     */
    public static void unbindModel(UUID maidUUID) {
        maidModelBindings.remove(maidUUID);
        loadedModels.remove(maidUUID);
    }
    
    /**
     * 获取女仆绑定的模型名称
     * 
     * @param maidUUID 女仆 UUID
     * @return 模型名称，如果没有绑定返回 null
     */
    public static String getBindingModelName(UUID maidUUID) {
        return maidModelBindings.get(maidUUID);
    }
    
    /**
     * 检查女仆是否有绑定的 MMD 模型
     * 
     * @param maidUUID 女仆 UUID
     * @return 是否有绑定
     */
    public static boolean hasMMDModel(UUID maidUUID) {
        return maidModelBindings.containsKey(maidUUID);
    }
    
    /**
     * 获取女仆的 MMD 模型（带懒加载）
     * 
     * @param maidUUID 女仆 UUID
     * @return 模型实例，如果没有绑定或加载失败返回 null
     */
    public static MMDModelManager.Model getModel(UUID maidUUID) {
        String modelName = maidModelBindings.get(maidUUID);
        if (modelName == null) {
            return null;
        }
        
        // 检查是否已加载且句柄仍有效
        MMDModelManager.Model model = loadedModels.get(maidUUID);
        if (model != null) {
            if (model.model != null && model.model.getModelHandle() != 0) {
                return model;
            }
            // 句柄已失效（被 ModelCache GC 回收），移除悬空引用
            loadedModels.remove(maidUUID);
            logger.warn("女仆 {} 模型句柄已失效，将重新加载", maidUUID);
        }
        
        // 懒加载模型（createModelWrapper 已设置 idle 动画，无需重复设置）
        String cacheKey = "maid_" + maidUUID.toString();
        model = MMDModelManager.GetModel(modelName, cacheKey);
        if (model != null) {
            loadedModels.put(maidUUID, model);
        }
        
        return model;
    }
    
    /**
     * 为女仆播放自定义动画
     * 
     * @param maidUUID 女仆 UUID
     * @param animId 动画 ID（文件名）
     */
    public static void playAnimation(UUID maidUUID, String animId) {
        MMDModelManager.Model model = getModel(maidUUID);
        if (model == null) {
            logger.warn("女仆 {} 没有绑定模型，无法播放动画", maidUUID);
            return;
        }
        
        IMMDModel mmdModel = model.model;
        long anim = MMDAnimManager.GetAnimModel(mmdModel, animId);
        if (anim != 0) {
            mmdModel.transitionAnim(anim, 0, 0.25f);
        } else {
            logger.warn("女仆 {} 动画未找到: {}", maidUUID, animId);
        }
    }
    
    /**
     * 模型被 dispose 时的回调，移除 loadedModels 中所有持有该模型的条目
     * 防止悬空引用（访问已释放的 GL 资源）
     */
    public static void onModelDisposed(MMDModelManager.Model disposedModel) {
        if (disposedModel == null) return;
        loadedModels.entrySet().removeIf(entry -> entry.getValue() == disposedModel);
    }
    
    /**
     * 使已加载的模型缓存失效（保留绑定关系）
     * 
     * 当全局模型缓存被清空时调用，避免持有已释放 GL 资源的旧模型引用。
     * 下次渲染时会通过懒加载重新创建模型。
     */
    public static void invalidateLoadedModels() {
        if (!loadedModels.isEmpty()) {
            loadedModels.clear();
        }
    }
    
    /**
     * 清理所有女仆模型绑定
     */
    public static void clearAll() {
        maidModelBindings.clear();
        loadedModels.clear();
    }
    
    /**
     * 获取已绑定模型的女仆数量
     */
    public static int getBindingCount() {
        return maidModelBindings.size();
    }
    
    /**
     * 获取所有已加载的女仆模型快照（供 PerformanceHud 使用）
     */
    public static Collection<MMDModelManager.Model> getLoadedMaidModels() {
        return loadedModels.values();
    }
}
