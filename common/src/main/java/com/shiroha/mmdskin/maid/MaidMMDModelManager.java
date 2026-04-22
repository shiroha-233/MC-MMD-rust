package com.shiroha.mmdskin.maid;

import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.model.runtime.ModelInstance;
import com.shiroha.mmdskin.model.runtime.ModelRequestKey;
import com.shiroha.mmdskin.render.bootstrap.ClientRenderRuntime;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** 文件职责：管理女仆实体与 MMD 模型绑定关系及已加载模型引用。 */
public class MaidMMDModelManager {
    private static final Logger logger = LogManager.getLogger();

    private static final Map<UUID, String> maidModelBindings = new ConcurrentHashMap<>();
    private static final Map<UUID, ManagedModel> loadedModels = new ConcurrentHashMap<>();

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

        loadedModels.remove(maidUUID);
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

    public static ManagedModel getModel(UUID maidUUID) {
        String modelName = maidModelBindings.get(maidUUID);
        if (modelName == null) {
            return null;
        }

        ManagedModel model = loadedModels.get(maidUUID);
        if (model != null) {
            if (model.modelInstance() != null && model.modelInstance().getModelHandle() != 0) {
                return model;
            }
            loadedModels.remove(maidUUID);
            logger.warn("Maid model handle became invalid: {}", maidUUID);
        }

        model = ClientRenderRuntime.get().modelRepository().acquire(ModelRequestKey.maid(maidUUID, modelName));
        if (model != null) {
            loadedModels.put(maidUUID, model);
        }
        return model;
    }

    public static void playAnimation(UUID maidUUID, String animId) {
        ManagedModel model = getModel(maidUUID);
        if (model == null) {
            logger.warn("Cannot play maid animation, no model bound: {}", maidUUID);
            return;
        }

        ModelInstance modelInstance = model.modelInstance();
        long animation = model.animationLibrary().animation(animId);
        if (animation != 0L) {
            modelInstance.transitionAnim(animation, 0, 0.25f);
        } else {
            logger.warn("Maid animation not found: {} {}", maidUUID, animId);
        }
    }

    public static void onModelDisposed(ManagedModel disposedModel) {
        if (disposedModel == null) {
            return;
        }
        loadedModels.entrySet().removeIf(entry -> entry.getValue() == disposedModel);
    }

    public static void invalidateLoadedModels() {
        loadedModels.clear();
    }

    public static void clearAll() {
        maidModelBindings.clear();
        loadedModels.clear();
    }

    public static int getBindingCount() {
        return maidModelBindings.size();
    }

    public static Collection<ManagedModel> getLoadedMaidModels() {
        return loadedModels.values();
    }
}
