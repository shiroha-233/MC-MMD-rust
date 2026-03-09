package com.shiroha.mmdskin.ui.selector.adapter;

import com.shiroha.mmdskin.config.ModelConfigData;
import com.shiroha.mmdskin.config.ModelConfigManager;
import com.shiroha.mmdskin.ui.config.ModelSelectorConfig;
import com.shiroha.mmdskin.ui.selector.port.ModelSettingsGateway;

public class DefaultModelSettingsGateway implements ModelSettingsGateway {
    @Override
    public ModelConfigData loadConfig(String modelName) {
        return ModelConfigManager.getConfig(modelName);
    }

    @Override
    public void saveConfig(String modelName, ModelConfigData config) {
        ModelConfigManager.saveConfig(modelName, config);
    }

    @Override
    public String getQuickSlotModel(int slot) {
        return ModelSelectorConfig.getInstance().getQuickSlotModel(slot);
    }

    @Override
    public void setQuickSlotModel(int slot, String modelName) {
        ModelSelectorConfig.getInstance().setQuickSlotModel(slot, modelName);
    }
}
