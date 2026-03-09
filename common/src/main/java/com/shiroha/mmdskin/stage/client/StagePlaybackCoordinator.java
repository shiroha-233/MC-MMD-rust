package com.shiroha.mmdskin.stage.client;

import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackBroadcastPort;
import com.shiroha.mmdskin.stage.client.playback.StagePlaybackStartRequest;
import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackRuntimePort;
import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackSessionPort;
import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackUiPort;

import java.util.Objects;
import java.util.UUID;

public final class StagePlaybackCoordinator {
    private static volatile StagePlaybackRuntimePort defaultRuntime = new StagePlaybackRuntimePort() {
        @Override
        public void enterStageSelection(boolean waitingForHost) {
        }

        @Override
        public void setWaitingForHost(boolean waitingForHost) {
        }

        @Override
        public void exitStageSelection() {
        }

        @Override
        public void stopActivePlaybackForRemoteEnd() {
        }

        @Override
        public void applyFrameSync(float frame) {
        }

        @Override
        public void applyInitialFrameSync(float frame) {
        }

        @Override
        public HostStartResult startHostPlayback(com.shiroha.mmdskin.config.StagePack pack, boolean cinematicMode,
                                                 float cameraHeightOffset, String selectedMotionFileName) {
            return HostStartResult.failed();
        }

        @Override
        public GuestStartResult startGuestPlayback(UUID hostUUID, StagePlaybackStartRequest request, boolean useHostCamera) {
            return GuestStartResult.failed();
        }
    };

    private static volatile StagePlaybackBroadcastPort defaultBroadcast = new StagePlaybackBroadcastPort() {
        @Override
        public void sendStageWatch(UUID targetUUID, UUID sessionId,
                                   com.shiroha.mmdskin.stage.domain.model.StageDescriptor descriptor,
                                   float heightOffset, float startFrame) {
        }

        @Override
        public void sendRemoteStageStart(com.shiroha.mmdskin.stage.domain.model.StageDescriptor descriptor) {
        }
    };

    private static volatile StagePlaybackUiPort defaultUi = new StagePlaybackUiPort() {
        @Override
        public void showInvite(UUID hostUUID) {
        }

        @Override
        public void markStageSelectionStartedAndClose() {
        }

        @Override
        public void openStageSelection() {
        }

        @Override
        public void closeStageSelectionIfOpen() {
        }
    };

    private static volatile StagePlaybackSessionPort defaultSessionPort = new StagePlaybackSessionPort() {
        @Override
        public boolean isSessionMember() {
            return false;
        }

        @Override
        public boolean isSessionHost() {
            return false;
        }

        @Override
        public boolean isUseHostCamera() {
            return true;
        }

        @Override
        public UUID getSessionId() {
            return null;
        }

        @Override
        public UUID getHostPlayerId() {
            return null;
        }

        @Override
        public java.util.Set<UUID> getAcceptedMembers() {
            return java.util.Collections.emptySet();
        }

        @Override
        public boolean matchesCurrentSession(UUID hostUUID, UUID incomingSessionId) {
            return false;
        }

        @Override
        public boolean onInviteReceived(UUID hostUUID, UUID incomingSessionId) {
            return false;
        }

        @Override
        public void onSessionDissolved(UUID hostUUID, UUID incomingSessionId) {
        }

        @Override
        public void onPlaybackStopped(UUID hostUUID) {
        }

        @Override
        public void onPlaybackStarted(UUID hostUUID, com.shiroha.mmdskin.stage.domain.model.StageDescriptor descriptor) {
        }

        @Override
        public void stopWatchingStageOnly() {
        }

        @Override
        public void leaveSession() {
        }

        @Override
        public void closeHostedSession() {
        }
    };

    private static final StagePlaybackCoordinator INSTANCE = new StagePlaybackCoordinator();

    private volatile StagePlaybackRuntimePort runtime;
    private volatile StagePlaybackBroadcastPort broadcast;
    private volatile StagePlaybackUiPort ui;
    private volatile StagePlaybackSessionPort sessionPort;

    private StagePlaybackCoordinator() {
        resetCollaborators();
    }

    public static StagePlaybackCoordinator getInstance() {
        return INSTANCE;
    }

    public synchronized void configureRuntimeCollaborators(StagePlaybackRuntimePort runtime,
                                                           StagePlaybackBroadcastPort broadcast,
                                                           StagePlaybackUiPort ui,
                                                           StagePlaybackSessionPort sessionPort) {
        defaultRuntime = Objects.requireNonNull(runtime, "runtime");
        defaultBroadcast = Objects.requireNonNull(broadcast, "broadcast");
        defaultUi = Objects.requireNonNull(ui, "ui");
        defaultSessionPort = Objects.requireNonNull(sessionPort, "sessionPort");
        resetCollaborators();
    }

    synchronized void setCollaboratorsForTesting(StagePlaybackRuntimePort runtime,
                                                 StagePlaybackBroadcastPort broadcast,
                                                 StagePlaybackUiPort ui,
                                                 StagePlaybackSessionPort sessionPort) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.broadcast = Objects.requireNonNull(broadcast, "broadcast");
        this.ui = Objects.requireNonNull(ui, "ui");
        this.sessionPort = Objects.requireNonNull(sessionPort, "sessionPort");
    }

    synchronized void resetCollaborators() {
        this.runtime = defaultRuntime;
        this.broadcast = defaultBroadcast;
        this.ui = defaultUi;
        this.sessionPort = defaultSessionPort;
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

        ui.markStageSelectionStartedAndClose();
        runtime.enterStageSelection(false);

        StagePlaybackRuntimePort.GuestStartResult result = runtime.startGuestPlayback(
                hostUUID,
                request,
                sessionPort.isUseHostCamera()
        );
        if (!result.started()) {
            sessionPort.stopWatchingStageOnly();
            runtime.setWaitingForHost(true);
            ui.openStageSelection();
            return;
        }

        sessionPort.onPlaybackStarted(hostUUID, result.sessionDescriptor());
        if (result.remoteDescriptor() != null) {
            broadcast.sendRemoteStageStart(result.remoteDescriptor());
        }

        float startFrame = request.startFrame() != null ? request.startFrame() : 0.0f;
        if (startFrame > 0.0f) {
            runtime.applyInitialFrameSync(startFrame);
        }
    }
}
