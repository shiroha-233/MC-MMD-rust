package com.shiroha.mmdskin.stage.client.playback.port;

import com.shiroha.mmdskin.stage.domain.model.StageDescriptor;

import java.util.Set;
import java.util.UUID;

public interface StagePlaybackSessionPort {
    boolean isSessionMember();

    boolean isSessionHost();

    boolean isUseHostCamera();

    UUID getSessionId();

    UUID getHostPlayerId();

    Set<UUID> getAcceptedMembers();

    boolean matchesCurrentSession(UUID hostUUID, UUID incomingSessionId);

    boolean onInviteReceived(UUID hostUUID, UUID incomingSessionId);

    void onSessionDissolved(UUID hostUUID, UUID incomingSessionId);

    void onPlaybackStopped(UUID hostUUID);

    void onPlaybackStarted(UUID hostUUID, StageDescriptor descriptor);

    void stopWatchingStageOnly();

    void leaveSession();

    void closeHostedSession();
}
