package com.shiroha.mmdskin.stage.client;

import com.shiroha.mmdskin.bridge.runtime.NativeAnimationPort;
import com.shiroha.mmdskin.stage.application.StageSessionService;
import com.shiroha.mmdskin.stage.application.port.StageLocalPlayerContextPort;
import com.shiroha.mmdskin.stage.application.port.StagePlaybackPreferencesPort;
import com.shiroha.mmdskin.stage.application.port.StageSessionOutboundPort;
import com.shiroha.mmdskin.stage.client.sync.StageAnimSyncHelper;
import com.shiroha.mmdskin.stage.domain.model.StageInviteDecision;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 文件职责：验证客户端远端舞台包只在当前会话上下文内生效。 */
class StageClientPacketHandlerTest {
    @Test
    void shouldHandleRemoteStagePacketsOnlyForCurrentSession() {
        UUID localId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        StageSessionService sessionService = new StageSessionService(
                new FakeLocalPlayerContext(localId),
                new FakePlaybackPreferences(),
                new FakeOutbound()
        );
        StageClientPacketHandler handler = new StageClientPacketHandler(
                sessionService,
                new StagePlaybackCoordinator(
                        new NoopRuntime(),
                        new NoopBroadcast(),
                        new NoopUi(),
                        new DefaultStageCameraSessionPort(sessionService)
                ),
                new StageAnimSyncHelper(new NoopFrameQueryPort(), NoopAnimationPort.INSTANCE)
        );

        assertTrue(sessionService.onInviteReceived(hostId, sessionId));
        sessionService.acceptInvite();

        assertTrue(handler.shouldHandleRemoteStagePacket(localId, memberId, sessionId));
        assertFalse(handler.shouldHandleRemoteStagePacket(localId, memberId, UUID.randomUUID()));
        assertFalse(handler.shouldHandleRemoteStagePacket(localId, localId, sessionId));
        assertFalse(handler.shouldHandleRemoteStagePacket(localId, memberId, null));
    }

    @Test
    void shouldIgnoreRemoteStagePacketsWhenLocalPlayerIsNotInSession() {
        UUID localId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        StageSessionService sessionService = new StageSessionService(
                new FakeLocalPlayerContext(localId),
                new FakePlaybackPreferences(),
                new FakeOutbound()
        );
        StageClientPacketHandler handler = new StageClientPacketHandler(
                sessionService,
                new StagePlaybackCoordinator(
                        new NoopRuntime(),
                        new NoopBroadcast(),
                        new NoopUi(),
                        new DefaultStageCameraSessionPort(sessionService)
                ),
                new StageAnimSyncHelper(new NoopFrameQueryPort(), NoopAnimationPort.INSTANCE)
        );

        assertFalse(handler.shouldHandleRemoteStagePacket(localId, senderId, sessionId));
    }

    private static final class FakeLocalPlayerContext implements StageLocalPlayerContextPort {
        private final UUID localPlayerId;

        private FakeLocalPlayerContext(UUID localPlayerId) {
            this.localPlayerId = localPlayerId;
        }

        @Override
        public UUID getLocalPlayerUUID() {
            return localPlayerId;
        }

        @Override
        public String resolvePlayerName(UUID uuid) {
            return uuid == null ? "Unknown" : uuid.toString();
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

    private static final class FakeOutbound implements StageSessionOutboundPort {
        @Override
        public void sendReady(com.shiroha.mmdskin.stage.application.port.StageSessionReadyCommand command) {
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

    private static final class NoopRuntime implements com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackRuntimePort {
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
        public HostStartResult startHostPlayback(com.shiroha.mmdskin.config.StagePack pack, boolean cinematicMode, float cameraHeightOffset, String selectedMotionFileName) {
            return HostStartResult.failed();
        }

        @Override
        public GuestStartResult startGuestPlayback(UUID hostUUID, com.shiroha.mmdskin.stage.client.playback.StagePlaybackStartRequest request, boolean useHostCamera) {
            return GuestStartResult.failed();
        }
    }

    private static final class NoopBroadcast implements com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackBroadcastPort {
        @Override
        public void sendStageWatch(com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackWatchRequest request) {
        }

        @Override
        public void sendRemoteStageStart(com.shiroha.mmdskin.stage.domain.model.StageDescriptor descriptor) {
        }
    }

    private static final class NoopUi implements com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackUiPort {
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
    }

    private static final class NoopFrameQueryPort implements com.shiroha.mmdskin.stage.client.camera.port.StageFrameQueryPort {
        @Override
        public boolean isStagePresentationActive() {
            return false;
        }

        @Override
        public float getCurrentFrame() {
            return 0;
        }
    }

    private enum NoopAnimationPort implements NativeAnimationPort {
        INSTANCE;

        @Override
        public long loadAnimation(long modelHandle, String animationPath) {
            return 0L;
        }

        @Override
        public void deleteAnimation(long animationHandle) {
        }

        @Override
        public void mergeAnimation(long mergedAnimationHandle, long sourceAnimationHandle) {
        }

        @Override
        public boolean hasCameraData(long animationHandle) {
            return false;
        }

        @Override
        public boolean hasBoneData(long animationHandle) {
            return false;
        }

        @Override
        public boolean hasMorphData(long animationHandle) {
            return false;
        }

        @Override
        public float getAnimationMaxFrame(long animationHandle) {
            return 0;
        }

        @Override
        public void seekLayer(long modelHandle, long layer, float frame) {
        }

        @Override
        public void getCameraTransform(long animationHandle, float frame, java.nio.ByteBuffer targetBuffer) {
        }
    }
}
