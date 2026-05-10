package com.shiroha.mmdskin.ui.selector.adapter;

import com.shiroha.mmdskin.asset.catalog.ModelCatalogEntry;
import com.shiroha.mmdskin.ui.config.ModelSelectorConfig;
import com.shiroha.mmdskin.ui.selector.port.ModelSelectionGateway;

import java.util.List;

public class DefaultModelSelectionGateway implements ModelSelectionGateway {
    @Override
    public List<String> loadAvailableModelNames() {
        return ModelCatalogEntry.scanModels().stream()
                .map(ModelCatalogEntry::getDisplayName)
                .toList();
    }

    @Override
    public void refreshModelCatalog() {
        ModelCatalogEntry.invalidateCache();
    }

    @Override
    public String getSelectedModel() {
        return ModelSelectorConfig.getInstance().getSelectedModel();
    }

    @Override
    public void setSelectedModel(String modelName) {
        ModelSelectorConfig.getInstance().setSelectedModel(modelName);
    }

    @Override
    public String getQuickSlotModel(int slot) {
        return ModelSelectorConfig.getInstance().getQuickSlotModel(slot);
    }
}
