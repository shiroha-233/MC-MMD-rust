package com.shiroha.mmdskin.ui.selector.adapter;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.config.ModelConfigData;
import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager;
import com.shiroha.mmdskin.ui.config.ModelSelectorConfig;
import com.shiroha.mmdskin.ui.selector.port.ModelSettingsRuntimeGateway;
import net.minecraft.client.Minecraft;

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

        MMDModelManager.Model model = MMDModelManager.GetModel(
                selectedModel,
                PlayerModelResolver.getCacheKey(minecraft.player)
        );
        if (model == null) {
            return;
        }

        long handle = model.model.getModelHandle();
        NativeFunc nativeFunc = NativeFunc.GetInst();
        nativeFunc.SetEyeTrackingEnabled(handle, config.eyeTrackingEnabled);
        nativeFunc.SetEyeMaxAngle(handle, config.eyeMaxAngle);
    }
}
