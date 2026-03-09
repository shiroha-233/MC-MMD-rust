package com.shiroha.mmdskin.stage.client;

import com.shiroha.mmdskin.stage.application.StageSessionService;
import com.shiroha.mmdskin.stage.application.port.StageLocalPlayerContextPort;
import com.shiroha.mmdskin.stage.application.port.StagePlaybackPreferencesPort;
import com.shiroha.mmdskin.stage.application.port.StageSessionOutboundPort;
import com.shiroha.mmdskin.stage.client.DefaultStageLocalPlayerContext;
import com.shiroha.mmdskin.stage.client.playback.StagePlaybackStartRequest;
import com.shiroha.mmdskin.stage.client.playback.DefaultStagePlaybackPreferencesPort;
import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackBroadcastPort;
import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackRuntimePort;
import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackUiPort;
import com.shiroha.mmdskin.stage.domain.model.StageDescriptor;
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

class StagePlaybackCoordinatorTest {
    private final StageSessionService sessionService = StageSessionService.getInstance();
    private final StagePlaybackCoordinator coordinator = StagePlaybackCoordinator.getInstance();

    @AfterEach
    void tearDown() {
        sessionService.onDisconnect();
        sessionService.configureRuntimeCollaborators(
                DefaultStageLocalPlayerContext.INSTANCE,
                DefaultStagePlaybackPreferencesPort.INSTANCE,
                StageNetworkSessionOutboundAdapter.INSTANCE
        );
        coordinator.resetCollaborators();
    }

    @Test
    void shouldStartGuestPlaybackThroughPorts() {
        UUID localId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        FakeSessionOutbound outbound = new FakeSessionOutbound();

        sessionService.configureRuntimeCollaborators(
                new FakeLocalPlayerContext(localId).withName(localId, "Local").withName(hostId, "Host"),
                new FakePlaybackPreferences(),
                outbound
        );

        assertTrue(sessionService.onInviteReceived(hostId, sessionId));
        sessionService.acceptInvite();

        FakeRuntime runtime = new FakeRuntime();
        runtime.guestStartResult = StagePlaybackRuntimePort.GuestStartResult.success(
                new StageDescriptor("host_pack", List.of("dance.vmd"), null, null),
                new StageDescriptor("motion_pack", List.of("dance.vmd"), null, null)
        );
        FakeBroadcast broadcast = new FakeBroadcast();
        FakeUi ui = new FakeUi();
        coordinator.setCollaboratorsForTesting(runtime, broadcast, ui, DefaultStageCameraSessionPort.INSTANCE);

        StagePlaybackStartRequest request = new StagePlaybackStartRequest(
                new StageDescriptor("host_pack", List.of("dance.vmd"), null, null),
                42.0f,
                null,
                "motion_pack"
        );

        coordinator.handlePlaybackStart(hostId, sessionId, request);

        assertEquals(1, ui.startedAndClosedCount);
        assertEquals(List.of(false), runtime.enterSelectionWaitingFlags);
        assertEquals(1, runtime.guestStartCalls.size());
        assertTrue(runtime.guestStartCalls.get(0).useHostCamera());
        assertEquals(1, broadcast.remoteStarts.size());
        assertEquals("motion_pack", broadcast.remoteStarts.get(0).getPackName());
        assertEquals(List.of(42.0f), runtime.initialFrameSyncs);
        assertTrue(sessionService.isWatchingStage());
        assertEquals("host_pack", sessionService.getWatchingDescriptor().getPackName());
    }

    @Test
    void shouldLeaveSessionWhenClosingSelectionBeforeStart() {
        UUID localId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        FakeSessionOutbound outbound = new FakeSessionOutbound();

        sessionService.configureRuntimeCollaborators(
                new FakeLocalPlayerContext(localId).withName(localId, "Local").withName(hostId, "Host"),
                new FakePlaybackPreferences(),
                outbound
        );

        assertTrue(sessionService.onInviteReceived(hostId, sessionId));
        sessionService.acceptInvite();

        FakeRuntime runtime = new FakeRuntime();
        coordinator.setCollaboratorsForTesting(runtime, new FakeBroadcast(), new FakeUi(), DefaultStageCameraSessionPort.INSTANCE);

        coordinator.onStageSelectionClosed(false);

        assertEquals(1, runtime.exitSelectionCalls);
        assertEquals(1, outbound.leaveCalls.size());
        assertEquals(hostId, outbound.leaveCalls.get(0).hostUUID());
        assertEquals(sessionId, outbound.leaveCalls.get(0).sessionId());
        assertEquals(com.shiroha.mmdskin.stage.domain.model.StageRole.NONE, sessionService.getLocalRole());
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
        private final List<LeaveCall> leaveCalls = new ArrayList<>();

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
        public void sendInviteResponse(UUID hostUUID, UUID sessionId,
                                       com.shiroha.mmdskin.stage.domain.model.StageInviteDecision decision) {
        }

        @Override
        public void sendLeave(UUID hostUUID, UUID sessionId) {
            leaveCalls.add(new LeaveCall(hostUUID, sessionId));
        }

        @Override
        public void sendStageWatchEnd(UUID targetUUID, UUID sessionId) {
        }

        @Override
        public void sendSessionDissolve(UUID sessionId) {
        }
    }

    private static final class FakeRuntime implements StagePlaybackRuntimePort {
        private final List<Boolean> enterSelectionWaitingFlags = new ArrayList<>();
        private final List<Float> initialFrameSyncs = new ArrayList<>();
        private final List<GuestStartCall> guestStartCalls = new ArrayList<>();
        private int exitSelectionCalls;
        private GuestStartResult guestStartResult = GuestStartResult.failed();

        @Override
        public void enterStageSelection(boolean waitingForHost) {
            enterSelectionWaitingFlags.add(waitingForHost);
        }

        @Override
        public void setWaitingForHost(boolean waitingForHost) {
        }

        @Override
        public void exitStageSelection() {
            exitSelectionCalls++;
        }

        @Override
        public void stopActivePlaybackForRemoteEnd() {
        }

        @Override
        public void applyFrameSync(float frame) {
        }

        @Override
        public void applyInitialFrameSync(float frame) {
            initialFrameSyncs.add(frame);
        }

        @Override
        public HostStartResult startHostPlayback(com.shiroha.mmdskin.config.StagePack pack, boolean cinematicMode,
                                                 float cameraHeightOffset, String selectedMotionFileName) {
            return HostStartResult.failed();
        }

        @Override
        public GuestStartResult startGuestPlayback(UUID hostUUID,
                                                    StagePlaybackStartRequest request,
                                                    boolean useHostCamera) {
            guestStartCalls.add(new GuestStartCall(hostUUID, request, useHostCamera));
            return guestStartResult;
        }
    }

    private static final class FakeBroadcast implements StagePlaybackBroadcastPort {
        private final List<StageDescriptor> remoteStarts = new ArrayList<>();

        @Override
        public void sendStageWatch(UUID targetUUID, UUID sessionId, StageDescriptor descriptor,
                                   float heightOffset, float startFrame) {
        }

        @Override
        public void sendRemoteStageStart(StageDescriptor descriptor) {
            remoteStarts.add(descriptor);
        }
    }

    private static final class FakeUi implements StagePlaybackUiPort {
        private int startedAndClosedCount;

        @Override
        public void showInvite(UUID hostUUID) {
        }

        @Override
        public void markStageSelectionStartedAndClose() {
            startedAndClosedCount++;
        }

        @Override
        public void openStageSelection() {
        }

        @Override
        public void closeStageSelectionIfOpen() {
        }
    }

    private record LeaveCall(UUID hostUUID, UUID sessionId) {
    }

    private record GuestStartCall(UUID hostUUID, StagePlaybackStartRequest request,
                                   boolean useHostCamera) {
    }
}
