package com.shiroha.mmdskin.stage.client.playback;

import com.shiroha.mmdskin.stage.application.StageSessionService;
import com.shiroha.mmdskin.stage.application.port.StageLocalPlayerContextPort;
import com.shiroha.mmdskin.stage.application.port.StagePlaybackPreferencesPort;
import com.shiroha.mmdskin.stage.application.port.StageSessionOutboundPort;
import com.shiroha.mmdskin.stage.client.DefaultStageCameraSessionPort;
import com.shiroha.mmdskin.stage.client.DefaultStageLocalPlayerContext;
import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackBroadcastPort;
import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackRuntimePort;
import com.shiroha.mmdskin.stage.domain.model.StageDescriptor;
import com.shiroha.mmdskin.stage.domain.model.StageInviteDecision;
import com.shiroha.mmdskin.ui.network.StageNetworkSessionOutboundAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageHostPlaybackServiceTest {
    private final StageSessionService sessionService = StageSessionService.getInstance();
    private final StageHostPlaybackService hostPlaybackService = StageHostPlaybackService.getInstance();

    @AfterEach
    void tearDown() {
        sessionService.onDisconnect();
        sessionService.configureRuntimeCollaborators(
                DefaultStageLocalPlayerContext.INSTANCE,
                DefaultStagePlaybackPreferencesPort.INSTANCE,
                StageNetworkSessionOutboundAdapter.INSTANCE
        );
        hostPlaybackService.resetCollaborators();
    }

    @Test
    void shouldNotifyAcceptedMembersThroughPorts() {
        UUID hostId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        sessionService.configureRuntimeCollaborators(
                new FakeLocalPlayerContext(hostId).withName(hostId, "Host").withName(memberId, "Member"),
                new FakePlaybackPreferences(),
                new FakeSessionOutbound()
        );

        sessionService.sendInvite(memberId);
        UUID sessionId = sessionService.getSessionId();
        sessionService.onInviteReply(memberId, sessionId, StageInviteDecision.ACCEPT);

        FakeRuntime runtime = new FakeRuntime(StagePlaybackRuntimePort.HostStartResult.success(
                new StageDescriptor("host_pack", List.of("dance.vmd"), "camera.vmd", "song.ogg"),
                new StageDescriptor("host_pack", List.of("dance.vmd"), null, null)
        ));
        FakeBroadcast broadcast = new FakeBroadcast();
        hostPlaybackService.setCollaboratorsForTesting(runtime, broadcast, DefaultStageCameraSessionPort.INSTANCE);

        assertTrue(hostPlaybackService.startPack(null, true, 1.25f, "dance.vmd"));
        assertEquals(1, runtime.hostStartCalls);
        assertEquals(1, broadcast.stageWatchCalls.size());
        assertEquals(memberId, broadcast.stageWatchCalls.get(0).targetUUID());
        assertEquals(sessionId, broadcast.stageWatchCalls.get(0).sessionId());
        assertEquals("host_pack", broadcast.stageWatchCalls.get(0).descriptor().getPackName());
        assertEquals(List.of("dance.vmd"), broadcast.stageWatchCalls.get(0).descriptor().getMotionFiles());
        assertEquals(1, broadcast.remoteStarts.size());
        assertEquals("host_pack", broadcast.remoteStarts.get(0).getPackName());
    }

    private static final class FakeLocalPlayerContext implements StageLocalPlayerContextPort {
        private final UUID localPlayerId;
        private final Map<UUID, String> names = new HashMap<>();

        private FakeLocalPlayerContext(UUID localPlayerId) {
            this.localPlayerId = localPlayerId;
        }

        private FakeLocalPlayerContext withName(UUID playerId, String name) {
            names.put(playerId, name);
            return this;
        }

        @Override
        public UUID getLocalPlayerUUID() {
            return localPlayerId;
        }

        @Override
        public String resolvePlayerName(UUID uuid) {
            return names.getOrDefault(uuid, uuid == null ? "Unknown" : uuid.toString());
        }
    }

    private static final class FakePlaybackPreferences implements StagePlaybackPreferencesPort {
        @Override
        public boolean isCustomMotionEnabled() {
            return false;
        }

        @Override
        public List<String> getSelectedMotionFiles() {
            return List.of();
        }

        @Override
        public String getSelectedPackName() {
            return null;
        }

        @Override
        public void reset() {
        }
    }

    private static final class FakeSessionOutbound implements StageSessionOutboundPort {
        @Override
        public void sendReady(UUID hostUUID, UUID sessionId, boolean ready, boolean useHostCamera,
                              String motionPackName, List<String> motionFiles) {
        }

        @Override
        public void sendStageInvite(UUID targetUUID, UUID sessionId) {
        }

        @Override
        public void sendInviteCancel(UUID targetUUID, UUID sessionId) {
        }

        @Override
        public void sendInviteResponse(UUID hostUUID, UUID sessionId, StageInviteDecision decision) {
        }

        @Override
        public void sendLeave(UUID hostUUID, UUID sessionId) {
        }

        @Override
        public void sendStageWatchEnd(UUID targetUUID, UUID sessionId) {
        }

        @Override
        public void sendSessionDissolve(UUID sessionId) {
        }
    }

    private static final class FakeRuntime implements StagePlaybackRuntimePort {
        private final HostStartResult hostStartResult;
        private int hostStartCalls;

        private FakeRuntime(HostStartResult hostStartResult) {
            this.hostStartResult = hostStartResult;
        }

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
            hostStartCalls++;
            return hostStartResult;
        }

        @Override
        public GuestStartResult startGuestPlayback(UUID hostUUID,
                                                    StagePlaybackStartRequest request,
                                                    boolean useHostCamera) {
            return GuestStartResult.failed();
        }
    }

    private static final class FakeBroadcast implements StagePlaybackBroadcastPort {
        private final List<StageWatchCall> stageWatchCalls = new ArrayList<>();
        private final List<StageDescriptor> remoteStarts = new ArrayList<>();

        @Override
        public void sendStageWatch(UUID targetUUID, UUID sessionId, StageDescriptor descriptor,
                                   float heightOffset, float startFrame) {
            stageWatchCalls.add(new StageWatchCall(targetUUID, sessionId, descriptor, heightOffset, startFrame));
        }

        @Override
        public void sendRemoteStageStart(StageDescriptor descriptor) {
            remoteStarts.add(descriptor);
        }
    }

    private record StageWatchCall(UUID targetUUID, UUID sessionId, StageDescriptor descriptor,
                                  float heightOffset, float startFrame) {
    }
}
