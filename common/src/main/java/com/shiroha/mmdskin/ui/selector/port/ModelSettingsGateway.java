package com.shiroha.mmdskin.ui.selector.port;

import com.shiroha.mmdskin.config.ModelConfigData;

public interface ModelSettingsGateway {
    ModelConfigData loadConfig(String modelName);

    void saveConfig(String modelName, ModelConfigData config);

    String getQuickSlotModel(int slot);

    void setQuickSlotModel(int slot, String modelName);
}
