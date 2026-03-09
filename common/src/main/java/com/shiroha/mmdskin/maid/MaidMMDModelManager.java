package com.shiroha.mmdskin.maid;

import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.renderer.runtime.animation.MMDAnimManager;
import com.shiroha.mmdskin.renderer.api.IMMDModel;
import com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 女仆 MMD 模型管理器
 */

public class MaidMMDModelManager {
    private static final Logger logger = LogManager.getLogger();

    private static final Map<UUID, String> maidModelBindings = new ConcurrentHashMap<>();

    private static final Map<UUID, MMDModelManager.Model> loadedModels = new ConcurrentHashMap<>();

    public static void init() {
    }

    public static void bindModel(UUID maidUUID, String modelName) {
        if (modelName == null || modelName.isEmpty() || UIConstants.DEFAULT_MODEL_NAME.equals(modelName)) {

            unbindModel(maidUUID);
            return;
        }

        String oldModel = maidModelBindings.get(maidUUID);
        if (modelName.equals(oldModel)) {
            return;
        }

        if (loadedModels.containsKey(maidUUID)) {
            loadedModels.remove(maidUUID);
        }

        maidModelBindings.put(maidUUID, modelName);
    }

    public static void unbindModel(UUID maidUUID) {
        maidModelBindings.remove(maidUUID);
        loadedModels.remove(maidUUID);
    }

    public static String getBindingModelName(UUID maidUUID) {
        return maidModelBindings.get(maidUUID);
    }

    public static boolean hasMMDModel(UUID maidUUID) {
        return maidModelBindings.containsKey(maidUUID);
    }

    public static MMDModelManager.Model getModel(UUID maidUUID) {
        String modelName = maidModelBindings.get(maidUUID);
        if (modelName == null) {
            return null;
        }

        MMDModelManager.Model model = loadedModels.get(maidUUID);
        if (model != null) {
            if (model.model != null && model.model.getModelHandle() != 0) {
                return model;
            }

            loadedModels.remove(maidUUID);
            logger.warn("女仆 {} 模型句柄已失效，将重新加载", maidUUID);
        }

        String cacheKey = "maid_" + maidUUID.toString();
        model = MMDModelManager.GetModel(modelName, cacheKey);
        if (model != null) {
            loadedModels.put(maidUUID, model);
        }

        return model;
    }

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

    public static void onModelDisposed(MMDModelManager.Model disposedModel) {
        if (disposedModel == null) return;
        loadedModels.entrySet().removeIf(entry -> entry.getValue() == disposedModel);
    }

    public static void invalidateLoadedModels() {
        if (!loadedModels.isEmpty()) {
            loadedModels.clear();
        }
    }

    public static void clearAll() {
        maidModelBindings.clear();
        loadedModels.clear();
    }

    public static int getBindingCount() {
        return maidModelBindings.size();
    }

    public static Collection<MMDModelManager.Model> getLoadedMaidModels() {
        return loadedModels.values();
    }
}
