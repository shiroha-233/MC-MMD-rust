package com.shiroha.mmdskin.maid.service;

import com.shiroha.mmdskin.asset.catalog.ModelInfo;
import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.maid.MaidMMDModelManager;
import com.shiroha.mmdskin.maid.MaidModelNetworkHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

public class DefaultMaidModelSelectionService implements MaidModelSelectionService {
    private final Supplier<List<String>> availableModelsSupplier;
    private final Function<UUID, String> currentModelReader;
    private final MaidModelBindingPort bindingPort;
    private final MaidModelSyncPort syncPort;

    public DefaultMaidModelSelectionService() {
        this(DefaultMaidModelSelectionService::scanAvailableModels, MaidMMDModelManager::getBindingModelName,
                MaidMMDModelManager::bindModel, MaidModelNetworkHandler.getInstance());
    }

    DefaultMaidModelSelectionService(Supplier<List<String>> availableModelsSupplier,
                                     Function<UUID, String> currentModelReader,
                                     MaidModelBindingPort bindingPort,
                                     MaidModelSyncPort syncPort) {
        this.availableModelsSupplier = Objects.requireNonNull(availableModelsSupplier, "availableModelsSupplier");
        this.currentModelReader = Objects.requireNonNull(currentModelReader, "currentModelReader");
        this.bindingPort = Objects.requireNonNull(bindingPort, "bindingPort");
        this.syncPort = Objects.requireNonNull(syncPort, "syncPort");
    }

    @Override
    public List<String> loadAvailableModels() {
        return List.copyOf(availableModelsSupplier.get());
    }

    @Override
    public String getCurrentModel(UUID maidUUID) {
        String currentModel = currentModelReader.apply(maidUUID);
        return currentModel == null || currentModel.isEmpty() ? UIConstants.DEFAULT_MODEL_NAME : currentModel;
    }

    @Override
    public void selectModel(UUID maidUUID, int maidEntityId, String modelName) {
        if (maidUUID == null || modelName == null || modelName.isEmpty()) {
            return;
        }
        bindingPort.bindModel(maidUUID, modelName);
        syncPort.syncMaidModel(maidEntityId, modelName);
    }

    private static List<String> scanAvailableModels() {
        List<String> models = new ArrayList<>();
        models.add(UIConstants.DEFAULT_MODEL_NAME);
        for (ModelInfo info : ModelInfo.scanModels()) {
            models.add(info.getDisplayName());
        }
        return models;
    }

    interface MaidModelBindingPort {
        void bindModel(UUID maidUUID, String modelName);
    }
}
