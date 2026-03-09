package com.shiroha.mmdskin.stage.client.camera.port;

import java.util.UUID;

public interface StageCameraSessionPort {
    boolean isWatchingStage();

    boolean isSessionHost();

    UUID getSessionId();

    UUID getHostPlayerId();

    void stopWatching();

    void stopWatchingStageOnly();

    void notifyMembersStageEnd();

    void closeHostedSession();
}
