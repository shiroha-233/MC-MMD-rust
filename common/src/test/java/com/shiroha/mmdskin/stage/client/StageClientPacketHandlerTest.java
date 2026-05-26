package com.shiroha.mmdskin.stage.client;

import com.shiroha.mmdskin.stage.application.StageSessionService;
import com.shiroha.mmdskin.stage.application.port.StageLocalPlayerContextPort;
import com.shiroha.mmdskin.stage.application.port.StagePlaybackPreferencesPort;
import com.shiroha.mmdskin.stage.application.port.StageSessionOutboundPort;
import com.shiroha.mmdskin.stage.client.playback.DefaultStagePlaybackPreferencesPort;
import com.shiroha.mmdskin.stage.domain.model.StageInviteDecision;
import com.shiroha.mmdskin.ui.network.StageNetworkSessionOutboundAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 文件职责：验证客户端远端舞台包只按当前会话过滤，不强制要求发送者必须是 host。 */
class StageClientPacketHandlerTest {
    private final StageSessionService sessionService = StageSessionService.getInstance();

    @AfterEach
    void tearDown() {
        sessionService.onDisconnect();
        sessionService.configureRuntimeCollaborators(
                DefaultStageLocalPlayerContext.INSTANCE,
                DefaultStagePlaybackPreferencesPort.INSTANCE,
                StageNetworkSessionOutboundAdapter.INSTANCE
        );
    }

    @Test
    void shouldHandleRemoteStagePacketsForCurrentSessionMember() {
        UUID localId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        sessionService.configureRuntimeCollaborators(
                new FakeLocalPlayerContext(localId),
                new FakePlaybackPreferences(),
                new FakeOutbound()
        );
        StageClientPacketHandler handler = StageClientPacketHandler.getInstance();

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

        sessionService.configureRuntimeCollaborators(
                new FakeLocalPlayerContext(localId),
                new FakePlaybackPreferences(),
                new FakeOutbound()
        );
        StageClientPacketHandler handler = StageClientPacketHandler.getInstance();

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
}
