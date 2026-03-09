package com.shiroha.mmdskin.ui.selector.application;

import com.shiroha.mmdskin.config.ModelConfigData;
import com.shiroha.mmdskin.ui.config.ModelSelectorConfig;
import com.shiroha.mmdskin.ui.selector.port.ModelSettingsGateway;
import com.shiroha.mmdskin.ui.selector.port.ModelSettingsRuntimeGateway;

import java.util.ArrayList;
import java.util.List;

public class ModelSettingsApplicationService {
    private final ModelSettingsGateway gateway;
    private final ModelSettingsRuntimeGateway runtimeGateway;

    public ModelSettingsApplicationService(ModelSettingsGateway gateway,
                                           ModelSettingsRuntimeGateway runtimeGateway) {
        this.gateway = gateway;
        this.runtimeGateway = runtimeGateway;
    }

    public ModelConfigData loadEditableConfig(String modelName) {
        return gateway.loadConfig(modelName).copy();
    }

    public void save(String modelName, ModelConfigData config) {
        gateway.saveConfig(modelName, config);
        runtimeGateway.applyConfigIfSelected(modelName, config);
    }

    public ModelConfigData resetToDefaults() {
        return new ModelConfigData();
    }

    public List<QuickSlotBinding> getQuickSlotBindings(String modelName) {
        List<QuickSlotBinding> bindings = new ArrayList<>();
        for (int i = 0; i < ModelSelectorConfig.QUICK_SLOT_COUNT; i++) {
            String boundModel = gateway.getQuickSlotModel(i);
            bindings.add(new QuickSlotBinding(i, boundModel, modelName.equals(boundModel)));
        }
        return bindings;
    }

    public void toggleQuickSlot(String modelName, int slot) {
        String currentBound = gateway.getQuickSlotModel(slot);
        if (modelName.equals(currentBound)) {
            gateway.setQuickSlotModel(slot, null);
            return;
        }
        gateway.setQuickSlotModel(slot, modelName);
    }

    public record QuickSlotBinding(int slot, String boundModel, boolean boundToCurrentModel) {
    }
}
