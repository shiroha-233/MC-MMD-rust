package com.shiroha.mmdskin.render.backend.mode;

import com.shiroha.mmdskin.asset.catalog.ModelInfo;
import com.shiroha.mmdskin.model.runtime.ModelInstance;

/**
 * MMD 模型工厂接口。
 */
public interface ModelInstanceFactory {

    RenderCategory getCategory();

    String getModeName();

    int getPriority();

    boolean isAvailable();

    default boolean isEnabledByDefault() {
        return false;
    }

    default boolean isEnabledInCurrentEnvironment() {
        return isEnabledByDefault();
    }

    default boolean supportsPMD() {
        return true;
    }

    ModelInstance createModel(String modelFilename, String modelDir, boolean isPMD, long layerCount);

    ModelInstance createModelFromHandle(long modelHandle, String modelDir);

    default ModelInstance createModel(ModelInfo modelInfo, long layerCount) {
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
}
