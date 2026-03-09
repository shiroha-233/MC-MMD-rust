package com.shiroha.mmdskin.ui.selector.port;

import com.shiroha.mmdskin.config.ModelConfigData;

public interface ModelSettingsRuntimeGateway {
    void applyConfigIfSelected(String modelName, ModelConfigData config);
}
