package com.shiroha.mmdskin.ui.selector.port;

import java.util.List;

public interface ModelSelectionGateway {
    List<String> loadAvailableModelNames();

    void refreshModelCatalog();

    String getSelectedModel();

    void setSelectedModel(String modelName);

    String getQuickSlotModel(int slot);
}
