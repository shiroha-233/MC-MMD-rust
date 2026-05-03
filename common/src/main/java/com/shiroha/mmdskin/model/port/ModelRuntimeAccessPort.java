package com.shiroha.mmdskin.model.port;

import com.shiroha.mmdskin.model.runtime.ModelInstance;

/** 文件职责：收口模型仓储与加载流程需要的运行时能力边界。 */
public interface ModelRuntimeAccessPort {

    boolean isRenderingShadows();

    ModelInstance createModelFromHandle(long modelHandle, String modelDir, boolean isPmd);

    long loadPmxModel(String modelFilePath, String modelDir, long layerCount);

    long loadPmdModel(String modelFilePath, String modelDir, long layerCount);

    long loadVrmModel(String modelFilePath, String modelDir, long layerCount);

    int getMaterialCount(long modelHandle);

    String getMaterialTexturePath(long modelHandle, int materialIndex);

    void setMaterialVisible(long modelHandle, int materialIndex, boolean visible);

    void preloadTexture(String texturePath);

    void clearPreloadedTextures();

    void tickTextures();

    void deleteModel(long modelHandle);
}
