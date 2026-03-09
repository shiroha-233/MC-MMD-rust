package com.shiroha.mmdskin.maid.service;

import java.util.List;
import java.util.UUID;

public interface MaidModelSelectionService {
    List<String> loadAvailableModels();

    String getCurrentModel(UUID maidUUID);

    void selectModel(UUID maidUUID, int maidEntityId, String modelName);
}
