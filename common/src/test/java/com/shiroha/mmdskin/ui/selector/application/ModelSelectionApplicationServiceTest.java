package com.shiroha.mmdskin.ui.selector.application;

import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.ui.selector.port.ModelSelectionGateway;
import com.shiroha.mmdskin.ui.selector.port.ModelSelectionRuntimeGateway;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelSelectionApplicationServiceTest {

    @Test
    void shouldLoadDefaultAndScannedModels() {
        FakeSelectionGateway gateway = new FakeSelectionGateway();
        gateway.availableModels = List.of("alice", "bob");
        ModelSelectionApplicationService service = new ModelSelectionApplicationService(gateway, modelName -> {
        });

        List<ModelSelectionApplicationService.ModelCard> cards = service.loadModelCards();

        assertEquals(3, cards.size());
        assertEquals(UIConstants.DEFAULT_MODEL_NAME, cards.get(0).displayName());
        assertFalse(cards.get(0).configurable());
        assertEquals("alice", cards.get(1).displayName());
        assertTrue(cards.get(1).configurable());
    }

    @Test
    void shouldResetToDefaultWhenQuickSlotMatchesCurrentModel() {
        FakeSelectionGateway gateway = new FakeSelectionGateway();
        gateway.selectedModel = "alice";
        gateway.quickSlots[0] = "alice";
        FakeRuntimeGateway runtimeGateway = new FakeRuntimeGateway();
        ModelSelectionApplicationService service = new ModelSelectionApplicationService(gateway, runtimeGateway);

        ModelSelectionApplicationService.QuickSwitchResult result = service.switchToSlot(0);

        assertEquals(ModelSelectionApplicationService.QuickSwitchStatus.RESET_TO_DEFAULT, result.status());
        assertEquals(UIConstants.DEFAULT_MODEL_NAME, gateway.selectedModel);
        assertEquals(UIConstants.DEFAULT_MODEL_NAME, runtimeGateway.lastAppliedModel);
    }

    @Test
    void shouldReturnUnboundWhenQuickSlotIsEmpty() {
        FakeSelectionGateway gateway = new FakeSelectionGateway();
        ModelSelectionApplicationService service = new ModelSelectionApplicationService(gateway, modelName -> {
        });

        ModelSelectionApplicationService.QuickSwitchResult result = service.switchToSlot(2);

        assertEquals(ModelSelectionApplicationService.QuickSwitchStatus.UNBOUND, result.status());
    }

    private static final class FakeSelectionGateway implements ModelSelectionGateway {
        private List<String> availableModels = List.of();
        private final String[] quickSlots = new String[4];
        private String selectedModel = UIConstants.DEFAULT_MODEL_NAME;

        @Override
        public List<String> loadAvailableModelNames() {
            return availableModels;
        }

        @Override
        public void refreshModelCatalog() {
        }

        @Override
        public String getSelectedModel() {
            return selectedModel;
        }

        @Override
        public void setSelectedModel(String modelName) {
            selectedModel = modelName;
        }

        @Override
        public String getQuickSlotModel(int slot) {
            return quickSlots[slot];
        }
    }

    private static final class FakeRuntimeGateway implements ModelSelectionRuntimeGateway {
        private String lastAppliedModel;

        @Override
        public void afterLocalModelSelection(String modelName) {
            lastAppliedModel = modelName;
        }
    }
}
