package com.shiroha.mmdskin.ui.network;

import com.shiroha.mmdskin.stage.application.port.StageSessionOutboundPort;
import com.shiroha.mmdskin.stage.domain.model.StageInviteDecision;

import java.util.List;
import java.util.UUID;

public final class StageNetworkSessionOutboundAdapter implements StageSessionOutboundPort {
    public static final StageNetworkSessionOutboundAdapter INSTANCE = new StageNetworkSessionOutboundAdapter();

    private StageNetworkSessionOutboundAdapter() {
    }

    @Override
    public void sendReady(UUID hostUUID, UUID sessionId, boolean ready, boolean useHostCamera,
                          String motionPackName, List<String> motionFiles) {
        StageNetworkHandler.sendReady(hostUUID, sessionId, ready, useHostCamera, motionPackName, motionFiles);
    }

    @Override
    public void sendStageInvite(UUID targetUUID, UUID sessionId) {
        StageNetworkHandler.sendStageInvite(targetUUID, sessionId);
    }

    @Override
    public void sendInviteCancel(UUID targetUUID, UUID sessionId) {
        StageNetworkHandler.sendInviteCancel(targetUUID, sessionId);
    }

    @Override
    public void sendInviteResponse(UUID hostUUID, UUID sessionId, StageInviteDecision decision) {
        StageNetworkHandler.sendInviteResponse(hostUUID, sessionId, decision);
    }

    @Override
    public void sendLeave(UUID hostUUID, UUID sessionId) {
        StageNetworkHandler.sendLeave(hostUUID, sessionId);
    }

    @Override
    public void sendStageWatchEnd(UUID targetUUID, UUID sessionId) {
        StageNetworkHandler.sendStageWatchEnd(targetUUID, sessionId);
    }

    @Override
    public void sendSessionDissolve(UUID sessionId) {
        StageNetworkHandler.sendSessionDissolve(sessionId);
    }
}
