package com.shiroha.mmdskin.renderer.runtime.mode;

import com.shiroha.mmdskin.asset.catalog.ModelInfo;
import com.shiroha.mmdskin.renderer.api.IMMDModel;

/**
 * MMD 模型工厂接口。
 */
public interface IMMDModelFactory {

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

    IMMDModel createModel(String modelFilename, String modelDir, boolean isPMD, long layerCount);

    IMMDModel createModelFromHandle(long modelHandle, String modelDir);

    default IMMDModel createModel(ModelInfo modelInfo, long layerCount) {
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
