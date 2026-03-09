package com.shiroha.mmdskin.maid.service;

import com.shiroha.mmdskin.maid.MaidActionNetworkHandler;
import com.shiroha.mmdskin.maid.MaidMMDModelManager;
import com.shiroha.mmdskin.ui.config.ActionWheelConfig;
import com.shiroha.mmdskin.ui.wheel.service.ActionOption;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public class DefaultMaidActionService implements MaidActionService {
    private final Supplier<List<ActionWheelConfig.ActionEntry>> actionEntriesSupplier;
    private final MaidActionRuntimePort runtimePort;
    private final MaidActionSyncPort syncPort;

    public DefaultMaidActionService() {
        this(() -> ActionWheelConfig.getInstance().getDisplayedActions(), MaidMMDModelManager::playAnimation,
                MaidActionNetworkHandler.getInstance());
    }

    DefaultMaidActionService(Supplier<List<ActionWheelConfig.ActionEntry>> actionEntriesSupplier,
                             MaidActionRuntimePort runtimePort,
                             MaidActionSyncPort syncPort) {
        this.actionEntriesSupplier = Objects.requireNonNull(actionEntriesSupplier, "actionEntriesSupplier");
        this.runtimePort = Objects.requireNonNull(runtimePort, "runtimePort");
        this.syncPort = Objects.requireNonNull(syncPort, "syncPort");
    }

    @Override
    public List<ActionOption> loadActions() {
        return actionEntriesSupplier.get().stream()
                .map(entry -> new ActionOption(entry.name, entry.animId))
                .toList();
    }

    @Override
    public void selectAction(UUID maidUUID, int maidEntityId, String animId) {
        if (maidUUID == null || animId == null || animId.isEmpty()) {
            return;
        }
        runtimePort.playLocalAction(maidUUID, animId);
        syncPort.syncMaidAction(maidEntityId, animId);
    }

    interface MaidActionRuntimePort {
        void playLocalAction(UUID maidUUID, String animId);
    }
}
