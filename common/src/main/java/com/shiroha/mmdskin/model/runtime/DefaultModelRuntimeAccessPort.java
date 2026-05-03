package com.shiroha.mmdskin.model.runtime;

import com.shiroha.mmdskin.bridge.runtime.NativeModelLoadPort;
import com.shiroha.mmdskin.bridge.runtime.NativeModelPort;
import com.shiroha.mmdskin.compat.iris.IrisCompat;
import com.shiroha.mmdskin.model.port.ModelRuntimeAccessPort;
import com.shiroha.mmdskin.render.backend.RenderBackendRegistry;
import com.shiroha.mmdskin.texture.runtime.TextureRepository;

/** 文件职责：将模型运行时所需的 native、纹理、渲染与 Iris 能力收口为窄 port。 */
public final class DefaultModelRuntimeAccessPort implements ModelRuntimeAccessPort {
    private final RenderBackendRegistry renderBackendRegistry;
    private final NativeModelLoadPort nativeModelLoadPort;
    private final NativeModelPort nativeModelPort;

    public DefaultModelRuntimeAccessPort(RenderBackendRegistry renderBackendRegistry,
                                         NativeModelLoadPort nativeModelLoadPort,
                                         NativeModelPort nativeModelPort) {
        if (renderBackendRegistry == null) {
            throw new IllegalArgumentException("renderBackendRegistry cannot be null");
        }
        if (nativeModelLoadPort == null) {
            throw new IllegalArgumentException("nativeModelLoadPort cannot be null");
        }
        if (nativeModelPort == null) {
            throw new IllegalArgumentException("nativeModelPort cannot be null");
        }
        this.renderBackendRegistry = renderBackendRegistry;
        this.nativeModelLoadPort = nativeModelLoadPort;
        this.nativeModelPort = nativeModelPort;
    }

    @Override
    public boolean isRenderingShadows() {
        return IrisCompat.isRenderingShadows();
    }

    @Override
    public ModelInstance createModelFromHandle(long modelHandle, String modelDir, boolean isPmd) {
        return renderBackendRegistry.createModelFromHandle(modelHandle, modelDir, isPmd);
    }

    @Override
    public long loadPmxModel(String modelFilePath, String modelDir, long layerCount) {
        return nativeModelLoadPort.loadPmxModel(modelFilePath, modelDir, layerCount);
    }

    @Override
    public long loadPmdModel(String modelFilePath, String modelDir, long layerCount) {
        return nativeModelLoadPort.loadPmdModel(modelFilePath, modelDir, layerCount);
    }

    @Override
    public long loadVrmModel(String modelFilePath, String modelDir, long layerCount) {
        return nativeModelLoadPort.loadVrmModel(modelFilePath, modelDir, layerCount);
    }

    @Override
    public int getMaterialCount(long modelHandle) {
        return nativeModelPort.getMaterialCount(modelHandle);
    }

    @Override
    public String getMaterialTexturePath(long modelHandle, int materialIndex) {
        return nativeModelLoadPort.getMaterialTexturePath(modelHandle, materialIndex);
    }

    @Override
    public void setMaterialVisible(long modelHandle, int materialIndex, boolean visible) {
        nativeModelPort.setMaterialVisible(modelHandle, materialIndex, visible);
    }

    @Override
    public void preloadTexture(String texturePath) {
        TextureRepository.preloadTexture(texturePath);
    }

    @Override
    public void clearPreloadedTextures() {
        TextureRepository.clearPreloaded();
    }

    @Override
    public void tickTextures() {
        TextureRepository.tick();
    }

    @Override
    public void deleteModel(long modelHandle) {
        nativeModelPort.deleteModel(modelHandle);
    }
}
