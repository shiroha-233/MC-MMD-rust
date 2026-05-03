package com.shiroha.mmdskin.stage.client.network;

import com.shiroha.mmdskin.stage.application.port.StageSessionOutboundPort;
import com.shiroha.mmdskin.stage.application.port.StageSessionReadyCommand;
import com.shiroha.mmdskin.stage.domain.model.StageInviteDecision;

import java.util.UUID;

/** 文件职责：把舞台会话出站命令适配到网络层。 */
public enum StageNetworkSessionOutboundAdapter implements StageSessionOutboundPort {
    INSTANCE;

    @Override
    public void sendReady(StageSessionReadyCommand command) {
        if (command == null) {
            return;
        }
        StageNetworkHandler.sendReady(
                command.hostUUID(),
                command.sessionId(),
                command.ready(),
                command.useHostCamera(),
                command.motionPackName(),
                command.motionFiles()
        );
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
