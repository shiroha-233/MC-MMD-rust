package com.shiroha.mmdskin.stage.client.playback;

import com.shiroha.mmdskin.config.StagePack;
import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackBroadcastPort;
import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackRuntimePort;
import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackSessionPort;
import com.shiroha.mmdskin.stage.domain.model.StageDescriptor;

import java.util.Objects;
import java.util.UUID;

public final class StageHostPlaybackService {
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
        public HostStartResult startHostPlayback(StagePack pack, boolean cinematicMode,
                                                 float cameraHeightOffset, String selectedMotionFileName) {
            return HostStartResult.failed();
        }

        @Override
        public GuestStartResult startGuestPlayback(UUID hostUUID, StagePlaybackStartRequest request,
                                                    boolean useHostCamera) {
            return GuestStartResult.failed();
        }
    };

    private static volatile StagePlaybackBroadcastPort defaultBroadcast = new StagePlaybackBroadcastPort() {
        @Override
        public void sendStageWatch(UUID targetUUID, UUID sessionId, StageDescriptor descriptor,
                                   float heightOffset, float startFrame) {
        }

        @Override
        public void sendRemoteStageStart(StageDescriptor descriptor) {
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
        public void onPlaybackStarted(UUID hostUUID, StageDescriptor descriptor) {
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

    private static final StageHostPlaybackService INSTANCE = new StageHostPlaybackService();

    private volatile StagePlaybackRuntimePort runtime;
    private volatile StagePlaybackBroadcastPort broadcast;
    private volatile StagePlaybackSessionPort sessionPort;

    private StageHostPlaybackService() {
        resetCollaborators();
    }

    public static StageHostPlaybackService getInstance() {
        return INSTANCE;
    }

    public synchronized void configureRuntimeCollaborators(StagePlaybackRuntimePort runtime,
                                                           StagePlaybackBroadcastPort broadcast,
                                                           StagePlaybackSessionPort sessionPort) {
        defaultRuntime = Objects.requireNonNull(runtime, "runtime");
        defaultBroadcast = Objects.requireNonNull(broadcast, "broadcast");
        defaultSessionPort = Objects.requireNonNull(sessionPort, "sessionPort");
        resetCollaborators();
    }

    synchronized void setCollaboratorsForTesting(StagePlaybackRuntimePort runtime,
                                                 StagePlaybackBroadcastPort broadcast,
                                                 StagePlaybackSessionPort sessionPort) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.broadcast = Objects.requireNonNull(broadcast, "broadcast");
        this.sessionPort = Objects.requireNonNull(sessionPort, "sessionPort");
    }

    synchronized void resetCollaborators() {
        this.runtime = defaultRuntime;
        this.broadcast = defaultBroadcast;
        this.sessionPort = defaultSessionPort;
    }

    public boolean startPack(StagePack pack, boolean cinematicMode, float cameraHeightOffset,
                             String selectedMotionFileName) {
        StagePlaybackRuntimePort runtime = this.runtime;
        StagePlaybackBroadcastPort broadcast = this.broadcast;
        StagePlaybackRuntimePort.HostStartResult result = runtime.startHostPlayback(
                pack,
                cinematicMode,
                cameraHeightOffset,
                selectedMotionFileName
        );
        if (!result.started()) {
            return false;
        }

        notifyMembers(broadcast, result.sessionDescriptor(), cameraHeightOffset);
        if (result.remoteDescriptor() != null) {
            broadcast.sendRemoteStageStart(result.remoteDescriptor());
        }
        return true;
    }

    private void notifyMembers(StagePlaybackBroadcastPort broadcast, StageDescriptor descriptor, float heightOffset) {
        UUID sessionId = sessionPort.getSessionId();
        if (sessionId == null || descriptor == null || !descriptor.isValid()) {
            return;
        }
        for (UUID memberUUID : sessionPort.getAcceptedMembers()) {
            broadcast.sendStageWatch(memberUUID, sessionId, descriptor, heightOffset, 0.0f);
        }
    }
}
