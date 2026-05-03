package com.shiroha.mmdskin.stage.client;

import com.shiroha.mmdskin.stage.application.StageSessionService;
import com.shiroha.mmdskin.stage.client.camera.port.StageCameraSessionPort;
import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackSessionPort;
import com.shiroha.mmdskin.stage.domain.model.StageDescriptor;

import java.util.Objects;
import java.util.UUID;

/** 文件职责：把舞台会话服务适配为相机与播放层可用的 session port。 */
public final class DefaultStageCameraSessionPort implements StageCameraSessionPort, StagePlaybackSessionPort {
    private final StageSessionService delegate;

    public DefaultStageCameraSessionPort(StageSessionService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public boolean isWatchingStage() {
        return delegate.isWatchingStage();
    }

    @Override
    public boolean isSessionHost() {
        return delegate.isSessionHost();
    }

    @Override
    public boolean isSessionMember() {
        return delegate.isSessionMember();
    }

    @Override
    public boolean isUseHostCamera() {
        return delegate.isUseHostCamera();
    }

    @Override
    public UUID getSessionId() {
        return delegate.getSessionId();
    }

    @Override
    public UUID getHostPlayerId() {
        return delegate.getHostPlayerId();
    }

    @Override
    public void stopWatching() {
        delegate.stopWatching();
    }

    @Override
    public void stopWatchingStageOnly() {
        delegate.stopWatchingStageOnly();
    }

    @Override
    public void notifyMembersStageEnd() {
        delegate.notifyMembersStageEnd();
    }

    @Override
    public void closeHostedSession() {
        delegate.closeHostedSession();
    }

    @Override
    public java.util.Set<UUID> getAcceptedMembers() {
        return delegate.getAcceptedMembers();
    }

    @Override
    public boolean matchesCurrentSession(UUID hostUUID, UUID incomingSessionId) {
        return delegate.matchesCurrentSession(hostUUID, incomingSessionId);
    }

    @Override
    public boolean onInviteReceived(UUID hostUUID, UUID incomingSessionId) {
        return delegate.onInviteReceived(hostUUID, incomingSessionId);
    }

    @Override
    public void onSessionDissolved(UUID hostUUID, UUID incomingSessionId) {
        delegate.onSessionDissolved(hostUUID, incomingSessionId);
    }

    @Override
    public void onPlaybackStopped(UUID hostUUID) {
        delegate.onPlaybackStopped(hostUUID);
    }

    @Override
    public void onPlaybackStarted(UUID hostUUID, StageDescriptor descriptor) {
        delegate.onPlaybackStarted(hostUUID, descriptor);
    }

    @Override
    public void leaveSession() {
        delegate.leaveSession();
    }
}
