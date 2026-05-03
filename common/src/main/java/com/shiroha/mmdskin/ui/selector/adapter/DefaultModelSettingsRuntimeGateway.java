package com.shiroha.mmdskin.ui.selector.adapter;

import com.shiroha.mmdskin.bridge.runtime.NativeScenePort;
import com.shiroha.mmdskin.config.ModelConfigData;
import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.model.runtime.ModelRequestKey;
import com.shiroha.mmdskin.render.bootstrap.ClientRenderRuntime;
import com.shiroha.mmdskin.ui.config.ModelSelectorConfig;
import com.shiroha.mmdskin.ui.selector.port.ModelSettingsRuntimeGateway;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;

/** 文件职责：把模型设置应用到当前已加载的本地玩家模型实例。 */
public class DefaultModelSettingsRuntimeGateway implements ModelSettingsRuntimeGateway {
    private final Supplier<? extends NativeScenePort> nativeScenePortSupplier;

    public DefaultModelSettingsRuntimeGateway(NativeScenePort nativeScenePort) {
        this(() -> nativeScenePort);
    }

    public DefaultModelSettingsRuntimeGateway(Supplier<? extends NativeScenePort> nativeScenePortSupplier) {
        this.nativeScenePortSupplier = nativeScenePortSupplier;
    }

    @Override
    public void applyConfigIfSelected(String modelName, ModelConfigData config) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        String selectedModel = ModelSelectorConfig.getInstance().getSelectedModel();
        if (!modelName.equals(selectedModel)) {
            return;
        }

        ManagedModel model = ClientRenderRuntime.get().modelRepository()
                .acquire(ModelRequestKey.player(minecraft.player, selectedModel));
        if (model == null) {
            return;
        }

        long handle = model.modelInstance().getModelHandle();
        NativeScenePort nativeScenePort = nativeScenePortSupplier.get();
        nativeScenePort.setEyeTrackingEnabled(handle, config.eyeTrackingEnabled);
        nativeScenePort.setEyeMaxAngle(handle, config.eyeMaxAngle);
    }
}
