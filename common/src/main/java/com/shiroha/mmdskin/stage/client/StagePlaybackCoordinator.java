package com.shiroha.mmdskin.stage.client;

import com.shiroha.mmdskin.stage.client.playback.StagePlaybackStartRequest;
import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackBroadcastPort;
import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackRuntimePort;
import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackSessionPort;
import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackUiPort;
import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackWatchRequest;

import java.util.Objects;
import java.util.UUID;

/** 文件职责：协调客户端收到的舞台邀请与播放流程。 */
public final class StagePlaybackCoordinator {
    private final StagePlaybackRuntimePort runtime;
    private final StagePlaybackBroadcastPort broadcast;
    private final StagePlaybackUiPort ui;
    private final StagePlaybackSessionPort sessionPort;

    public StagePlaybackCoordinator(StagePlaybackRuntimePort runtime,
                                    StagePlaybackBroadcastPort broadcast,
                                    StagePlaybackUiPort ui,
                                    StagePlaybackSessionPort sessionPort) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.broadcast = Objects.requireNonNull(broadcast, "broadcast");
        this.ui = Objects.requireNonNull(ui, "ui");
        this.sessionPort = Objects.requireNonNull(sessionPort, "sessionPort");
    }

    public void onStageSelectionOpened() {
        runtime.enterStageSelection(sessionPort.isSessionMember());
    }

    public void onStageSelectionClosed(boolean stageStarted) {
        if (stageStarted) {
            return;
        }

        runtime.exitStageSelection();
        if (sessionPort.isSessionMember()) {
            sessionPort.leaveSession();
        } else {
            sessionPort.closeHostedSession();
        }
    }

    public void handleInviteRequest(UUID hostUUID, UUID sessionId) {
        if (sessionPort.onInviteReceived(hostUUID, sessionId)) {
            ui.showInvite(hostUUID);
        }
    }

    public void handleSessionDissolve(UUID hostUUID, UUID sessionId) {
        boolean affectsCurrentSession = sessionPort.matchesCurrentSession(hostUUID, sessionId);
        if (affectsCurrentSession) {
            runtime.stopActivePlaybackForRemoteEnd();
            ui.closeStageSelectionIfOpen();
        }
        sessionPort.onSessionDissolved(hostUUID, sessionId);
    }

    public void handlePlaybackStop(UUID hostUUID, UUID sessionId) {
        if (!sessionPort.matchesCurrentSession(hostUUID, sessionId)) {
            return;
        }
        runtime.stopActivePlaybackForRemoteEnd();
        sessionPort.onPlaybackStopped(hostUUID);
    }

    public void handleFrameSync(UUID hostUUID, UUID sessionId, Float frame) {
        if (frame == null || !sessionPort.matchesCurrentSession(hostUUID, sessionId)) {
            return;
        }
        runtime.applyFrameSync(frame);
    }

    public void handlePlaybackStart(UUID hostUUID, UUID sessionId, StagePlaybackStartRequest request) {
        if (request == null || request.descriptor() == null) {
            return;
        }
        if (!sessionPort.matchesCurrentSession(hostUUID, sessionId)) {
            return;
        }

        if (sessionPort.isSessionHost()) {
            sessionPort.onPlaybackStarted(hostUUID, request.descriptor());
            runtime.applyInitialFrameSync(request.startFrame());
            return;
        }

        StagePlaybackRuntimePort.GuestStartResult result = runtime.startGuestPlayback(
                hostUUID,
                request,
                sessionPort.isUseHostCamera()
        );
        if (!result.started()) {
            return;
        }

        sessionPort.onPlaybackStarted(hostUUID, result.sessionDescriptor());
        runtime.applyInitialFrameSync(request.startFrame());
        ui.markStageSelectionStartedAndClose();

        if (result.remoteDescriptor() != null && sessionPort.getSessionId() != null) {
            broadcast.sendRemoteStageStart(result.remoteDescriptor());
        }
    }

    public void requestWatchFromHost(com.shiroha.mmdskin.stage.domain.model.StageDescriptor descriptor,
                                     float heightOffset,
                                     float startFrame) {
        UUID sessionId = sessionPort.getSessionId();
        UUID hostPlayerId = sessionPort.getHostPlayerId();
        if (sessionId == null || hostPlayerId == null || descriptor == null || !descriptor.isValid()) {
            return;
        }
        broadcast.sendStageWatch(new StagePlaybackWatchRequest(
                hostPlayerId,
                sessionId,
                descriptor,
                heightOffset,
                startFrame
        ));
    }
}
