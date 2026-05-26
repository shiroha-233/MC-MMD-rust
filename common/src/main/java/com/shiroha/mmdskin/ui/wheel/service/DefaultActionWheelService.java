package com.shiroha.mmdskin.ui.wheel.service;

import com.shiroha.mmdskin.player.runtime.MmdSkinRendererPlayerHelper;
import com.shiroha.mmdskin.ui.config.ActionWheelConfig;
import com.shiroha.mmdskin.ui.network.ActionWheelNetworkHandler;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class DefaultActionWheelService implements ActionWheelService {
    private final Supplier<List<ActionWheelConfig.ActionEntry>> actionEntriesSupplier;
    private final ActionRuntimePort runtimePort;
    private final ActionSyncPort syncPort;

    public DefaultActionWheelService() {
        this(() -> ActionWheelConfig.getInstance().getDisplayedActions(), new MinecraftActionRuntimePort(),
                ActionWheelNetworkHandler.getInstance());
    }

    DefaultActionWheelService(Supplier<List<ActionWheelConfig.ActionEntry>> actionEntriesSupplier,
                              ActionRuntimePort runtimePort,
                              ActionSyncPort syncPort) {
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
    public void selectAction(String animId) {
        if (animId == null || animId.isEmpty()) {
            return;
        }
        if (runtimePort.playLocalAction(animId)) {
            syncPort.syncAction(animId);
        }
    }

    interface ActionRuntimePort {
        boolean playLocalAction(String animId);
    }

    private static final class MinecraftActionRuntimePort implements ActionRuntimePort {
        @Override
        public boolean playLocalAction(String animId) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                return false;
            }
            MmdSkinRendererPlayerHelper.CustomAnim(mc.player, animId);
            return true;
        }
    }
}
