package com.shiroha.mmdskin.stage.application.port;

import com.shiroha.mmdskin.stage.domain.model.StageInviteDecision;

import java.util.UUID;

/** 定义舞台会话对外网络发送边界。 */
public interface StageSessionOutboundPort {
    void sendReady(StageSessionReadyCommand command);

    void sendStageInvite(UUID targetUUID, UUID sessionId);

    void sendInviteCancel(UUID targetUUID, UUID sessionId);

    void sendInviteResponse(UUID hostUUID, UUID sessionId, StageInviteDecision decision);

    void sendLeave(UUID hostUUID, UUID sessionId);

    void sendStageWatchEnd(UUID targetUUID, UUID sessionId);

    void sendSessionDissolve(UUID sessionId);
}
