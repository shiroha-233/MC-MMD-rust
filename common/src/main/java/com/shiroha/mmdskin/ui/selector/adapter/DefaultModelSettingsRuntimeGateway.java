package com.shiroha.mmdskin.ui.selector.adapter;

import com.shiroha.mmdskin.bridge.runtime.NativeRuntimeBridgeHolder;
import com.shiroha.mmdskin.config.ModelConfigData;
import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.model.runtime.ModelRequestKey;
import com.shiroha.mmdskin.render.bootstrap.ClientRenderRuntime;
import com.shiroha.mmdskin.ui.config.ModelSelectorConfig;
import com.shiroha.mmdskin.ui.selector.port.ModelSettingsRuntimeGateway;
import net.minecraft.client.Minecraft;

/** 文件职责：把模型设置应用到当前已加载的本地玩家模型实例。 */
public class DefaultModelSettingsRuntimeGateway implements ModelSettingsRuntimeGateway {
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
        var nativeBridge = NativeRuntimeBridgeHolder.get();
        nativeBridge.setEyeTrackingEnabled(handle, config.eyeTrackingEnabled);
        nativeBridge.setEyeMaxAngle(handle, config.eyeMaxAngle);
    }
}
