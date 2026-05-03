package com.shiroha.mmdskin.stage.client.network;

import com.shiroha.mmdskin.stage.application.StageSessionService;
import com.shiroha.mmdskin.stage.application.port.StageLocalPlayerContextPort;
import com.shiroha.mmdskin.stage.application.port.StagePlaybackPreferencesPort;
import com.shiroha.mmdskin.stage.application.port.StageSessionOutboundPort;
import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackWatchRequest;
import com.shiroha.mmdskin.stage.domain.model.StageDescriptor;
import com.shiroha.mmdskin.stage.domain.model.StageInviteDecision;
import com.shiroha.mmdskin.stage.protocol.StagePacket;
import com.shiroha.mmdskin.stage.protocol.StagePacketCodec;
import com.shiroha.mmdskin.stage.protocol.StagePacketType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StageNetworkPlaybackBroadcastAdapterTest {
    @AfterEach
    void tearDown() {
        StageNetworkHandler.setStageMultiSender(null);
    }

    @Test
    void shouldForwardLeaveWithExplicitSessionId() {
        AtomicReference<String> payloadRef = new AtomicReference<>();
        StageNetworkHandler.setStageMultiSender(payloadRef::set);

        StageNetworkPlaybackBroadcastAdapter adapter = new StageNetworkPlaybackBroadcastAdapter(
                new StageSessionService(new FakeLocalPlayerContext(UUID.randomUUID()), new FakePlaybackPreferences(), new FakeOutbound())
        );
        UUID hostId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        adapter.sendLeave(hostId, sessionId);

        StagePacket packet = StagePacketCodec.decode(payloadRef.get());
        assertNotNull(packet);
        assertEquals(StagePacketType.MEMBER_LEAVE, packet.type);
        assertEquals(hostId.toString(), packet.targetPlayerId);
        assertEquals(sessionId.toString(), packet.sessionId);
    }

    @Test
    void shouldForwardStageWatchRequestWithoutLeakingMutableDescriptor() {
        AtomicReference<String> payloadRef = new AtomicReference<>();
        StageNetworkHandler.setStageMultiSender(payloadRef::set);

        StageNetworkPlaybackBroadcastAdapter adapter = new StageNetworkPlaybackBroadcastAdapter(
                new StageSessionService(new FakeLocalPlayerContext(UUID.randomUUID()), new FakePlaybackPreferences(), new FakeOutbound())
        );
        UUID targetId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        StageDescriptor descriptor = new StageDescriptor("pack_a", List.of("dance.vmd"), "camera.vmd", null);

        adapter.sendStageWatch(new StagePlaybackWatchRequest(targetId, sessionId, descriptor, 1.25f, 42.0f));
        descriptor.setPackName("mutated");

        StagePacket packet = StagePacketCodec.decode(payloadRef.get());
        assertNotNull(packet);
        assertEquals(StagePacketType.PLAYBACK_START, packet.type);
        assertEquals(targetId.toString(), packet.targetPlayerId);
        assertEquals(sessionId.toString(), packet.sessionId);
        assertEquals("pack_a", packet.descriptor.getPackName());
        assertEquals(List.of("dance.vmd"), packet.descriptor.getMotionFiles());
        assertEquals(1.25f, packet.heightOffset);
        assertEquals(42.0f, packet.frame);
    }

    @Test
    void shouldForwardRemoteStageStartWithCurrentSessionId() {
        AtomicReference<String> payloadRef = new AtomicReference<>();
        StageNetworkHandler.setStageMultiSender(payloadRef::set);
        StageSessionService sessionService = prepareHostedSession();
        StageNetworkPlaybackBroadcastAdapter adapter = new StageNetworkPlaybackBroadcastAdapter(sessionService);
        UUID sessionId = sessionService.getSessionId();

        adapter.sendRemoteStageStart(new StageDescriptor("pack_a", List.of("dance.vmd"), null, null));

        StagePacket packet = StagePacketCodec.decode(payloadRef.get());
        assertNotNull(packet);
        assertEquals(StagePacketType.REMOTE_STAGE_START, packet.type);
        assertEquals(sessionId.toString(), packet.sessionId);
        assertEquals("pack_a", packet.descriptor.getPackName());
    }

    @Test
    void shouldForwardRemoteStageStopWithCurrentSessionId() {
        AtomicReference<String> payloadRef = new AtomicReference<>();
        StageNetworkHandler.setStageMultiSender(payloadRef::set);
        StageSessionService sessionService = prepareHostedSession();
        StageNetworkPlaybackBroadcastAdapter adapter = new StageNetworkPlaybackBroadcastAdapter(sessionService);
        UUID sessionId = sessionService.getSessionId();

        adapter.sendRemoteStageStop();

        StagePacket packet = StagePacketCodec.decode(payloadRef.get());
        assertNotNull(packet);
        assertEquals(StagePacketType.REMOTE_STAGE_STOP, packet.type);
        assertEquals(sessionId.toString(), packet.sessionId);
    }

    private StageSessionService prepareHostedSession() {
        UUID hostId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        StageSessionService sessionService = new StageSessionService(
                new FakeLocalPlayerContext(hostId),
                new FakePlaybackPreferences(),
                new FakeOutbound()
        );
        sessionService.sendInvite(memberId);
        return sessionService;
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
}
