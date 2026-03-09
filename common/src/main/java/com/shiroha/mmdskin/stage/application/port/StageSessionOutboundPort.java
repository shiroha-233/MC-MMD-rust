package com.shiroha.mmdskin.stage.application.port;

import com.shiroha.mmdskin.stage.domain.model.StageInviteDecision;

import java.util.List;
import java.util.UUID;

public interface StageSessionOutboundPort {
    void sendReady(UUID hostUUID, UUID sessionId, boolean ready, boolean useHostCamera,
                   String motionPackName, List<String> motionFiles);

    void sendStageInvite(UUID targetUUID, UUID sessionId);

    void sendInviteCancel(UUID targetUUID, UUID sessionId);

    void sendInviteResponse(UUID hostUUID, UUID sessionId, StageInviteDecision decision);

    void sendLeave(UUID hostUUID, UUID sessionId);

    void sendStageWatchEnd(UUID targetUUID, UUID sessionId);

    void sendSessionDissolve(UUID sessionId);
}
