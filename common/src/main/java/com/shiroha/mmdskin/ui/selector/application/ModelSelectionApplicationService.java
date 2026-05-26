package com.shiroha.mmdskin.ui.selector.application;

import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.ui.selector.port.ModelSelectionGateway;
import com.shiroha.mmdskin.ui.selector.port.ModelSelectionRuntimeGateway;

import java.util.ArrayList;
import java.util.List;

public class ModelSelectionApplicationService {
    private final ModelSelectionGateway gateway;
    private final ModelSelectionRuntimeGateway runtimeGateway;

    public ModelSelectionApplicationService(ModelSelectionGateway gateway,
                                            ModelSelectionRuntimeGateway runtimeGateway) {
        this.gateway = gateway;
        this.runtimeGateway = runtimeGateway;
    }

    public List<ModelCard> loadModelCards() {
        List<ModelCard> cards = new ArrayList<>();
        cards.add(new ModelCard(UIConstants.DEFAULT_MODEL_NAME, false));
        for (String modelName : gateway.loadAvailableModelNames()) {
            cards.add(new ModelCard(modelName, true));
        }
        return cards;
    }

    public String getCurrentModel() {
        return gateway.getSelectedModel();
    }

    public void refreshModelCatalog() {
        gateway.refreshModelCatalog();
    }

    public void selectModel(String modelName) {
        gateway.setSelectedModel(modelName);
        runtimeGateway.afterLocalModelSelection(modelName);
    }

    public QuickSwitchResult switchToSlot(int slot) {
        String modelName = gateway.getQuickSlotModel(slot);
        if (modelName == null || modelName.isEmpty()) {
            return new QuickSwitchResult(QuickSwitchStatus.UNBOUND, null);
        }

        String currentModel = gateway.getSelectedModel();
        if (modelName.equals(currentModel)) {
            selectModel(UIConstants.DEFAULT_MODEL_NAME);
            return new QuickSwitchResult(QuickSwitchStatus.RESET_TO_DEFAULT, UIConstants.DEFAULT_MODEL_NAME);
        }

        selectModel(modelName);
        return new QuickSwitchResult(QuickSwitchStatus.SWITCHED, modelName);
    }

    public record ModelCard(String displayName, boolean configurable) {
    }

    public record QuickSwitchResult(QuickSwitchStatus status, String targetModelName) {
    }

    public enum QuickSwitchStatus {
        UNBOUND,
        RESET_TO_DEFAULT,
        SWITCHED
    }
}
