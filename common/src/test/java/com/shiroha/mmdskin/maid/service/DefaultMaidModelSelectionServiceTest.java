package com.shiroha.mmdskin.maid.service;

import com.shiroha.mmdskin.config.UIConstants;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultMaidModelSelectionServiceTest {
    @Test
    void shouldReturnDefaultModelWhenNoBindingExists() {
        DefaultMaidModelSelectionService service = new DefaultMaidModelSelectionService(
                List::of,
                maidUUID -> null,
                (maidUUID, modelName) -> {
                },
                (entityId, modelName) -> {
                });

        assertEquals(UIConstants.DEFAULT_MODEL_NAME, service.getCurrentModel(UUID.randomUUID()));
    }

    @Test
    void shouldBindAndSyncSelectedModel() {
        UUID maidUUID = UUID.randomUUID();
        AtomicReference<String> boundModel = new AtomicReference<>();
        AtomicReference<String> syncedModel = new AtomicReference<>();
        AtomicReference<Integer> syncedEntityId = new AtomicReference<>();
        DefaultMaidModelSelectionService service = new DefaultMaidModelSelectionService(
                () -> List.of(UIConstants.DEFAULT_MODEL_NAME, "Alice"),
                uuid -> UIConstants.DEFAULT_MODEL_NAME,
                (uuid, modelName) -> boundModel.set(uuid + ":" + modelName),
                (entityId, modelName) -> {
                    syncedEntityId.set(entityId);
                    syncedModel.set(modelName);
                });

        service.selectModel(maidUUID, 12, "Alice");

        assertEquals(maidUUID + ":Alice", boundModel.get());
        assertEquals(12, syncedEntityId.get());
        assertEquals("Alice", syncedModel.get());
    }
}
