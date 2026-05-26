package com.shiroha.mmdskin.stage.application;

import com.shiroha.mmdskin.stage.application.port.StageLocalPlayerContextPort;
import com.shiroha.mmdskin.stage.application.port.StagePlaybackPreferencesPort;
import com.shiroha.mmdskin.stage.application.port.StageSessionOutboundPort;
import com.shiroha.mmdskin.stage.domain.model.StageInviteDecision;
import com.shiroha.mmdskin.stage.domain.model.StageMember;
import com.shiroha.mmdskin.stage.domain.model.StageMemberState;
import com.shiroha.mmdskin.stage.domain.model.StageRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageSessionServiceTest {
    private final StageSessionService service = StageSessionService.getInstance();

    @AfterEach
    void tearDown() {
        service.onDisconnect();
        service.resetCollaborators();
    }

    @Test
    void shouldStartHostedSessionWithoutMinecraftDependency() {
        UUID selfId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        FakeLocalPlayerContext localPlayerContext = new FakeLocalPlayerContext(selfId)
                .withName(selfId, "Host")
                .withName(memberId, "Member");
        FakePlaybackPreferences playbackPreferences = new FakePlaybackPreferences();
        FakeOutbound outbound = new FakeOutbound();
        service.setCollaboratorsForTesting(localPlayerContext, playbackPreferences, outbound);

        service.sendInvite(memberId);

        assertEquals(StageRole.HOST, service.getLocalRole());
        assertNotNull(service.getSessionId());
        assertEquals(2, service.getMembers().size());
        assertEquals(StageMemberState.INVITED, service.getMemberState(memberId));
        assertEquals(1, outbound.invites.size());
        assertEquals(memberId, outbound.invites.get(0).targetId());
        assertEquals(service.getSessionId(), outbound.invites.get(0).sessionId());
        assertTrue(playbackPreferences.resetCount >= 1);
    }

    @Test
    void shouldSyncReadyStateWithInjectedPorts() {
        UUID selfId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        FakeLocalPlayerContext localPlayerContext = new FakeLocalPlayerContext(selfId)
                .withName(selfId, "Local")
                .withName(hostId, "Host");
        FakePlaybackPreferences playbackPreferences = new FakePlaybackPreferences()
                .withCustomMotion(true)
                .withPackName("demo_pack")
                .withMotionFiles(List.of("a.vmd", "b.vmd"));
        FakeOutbound outbound = new FakeOutbound();
        service.setCollaboratorsForTesting(localPlayerContext, playbackPreferences, outbound);

        assertTrue(service.onInviteReceived(hostId, sessionId));
        service.acceptInvite();
        playbackPreferences.withCustomMotion(true)
                .withPackName("demo_pack")
                .withMotionFiles(List.of("a.vmd", "b.vmd"));
        service.setUseHostCamera(false);
        service.setLocalReady(true);

        assertEquals(StageRole.MEMBER, service.getLocalRole());
        assertTrue(service.isLocalReady());
        List<StageMember> members = service.getMembers();
        assertEquals(2, members.size());
        ReadyCall readyCall = outbound.lastReadyCall();
        assertNotNull(readyCall);
        assertEquals(hostId, readyCall.hostId());
        assertEquals(sessionId, readyCall.sessionId());
        assertTrue(readyCall.ready());
        assertFalse(readyCall.useHostCamera());
        assertEquals("demo_pack", readyCall.motionPackName());
        assertEquals(List.of("a.vmd", "b.vmd"), readyCall.motionFiles());

        service.onSessionDissolved(hostId, sessionId);

        assertEquals(StageRole.NONE, service.getLocalRole());
        assertNull(service.getSessionId());
        assertTrue(playbackPreferences.resetCount >= 2);
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
            return names.getOrDefault(uuid, uuid == null ? "Unknown" : uuid.toString().substring(0, 8));
        }
    }

    private static final class FakePlaybackPreferences implements StagePlaybackPreferencesPort {
        private boolean customMotionEnabled;
        private String packName;
        private List<String> motionFiles = List.of();
        private int resetCount;

        private FakePlaybackPreferences withCustomMotion(boolean enabled) {
            this.customMotionEnabled = enabled;
            return this;
        }

        private FakePlaybackPreferences withPackName(String packName) {
            this.packName = packName;
            return this;
        }

        private FakePlaybackPreferences withMotionFiles(List<String> motionFiles) {
            this.motionFiles = List.copyOf(motionFiles);
            return this;
        }

        @Override
        public boolean isCustomMotionEnabled() {
            return customMotionEnabled;
        }

        @Override
        public List<String> getSelectedMotionFiles() {
            return motionFiles;
        }

        @Override
        public String getSelectedPackName() {
            return packName;
        }

        @Override
        public void reset() {
            resetCount++;
            customMotionEnabled = false;
            packName = null;
            motionFiles = List.of();
        }
    }

    private static final class FakeOutbound implements StageSessionOutboundPort {
        private final List<InviteCall> invites = new ArrayList<>();
        private final List<ReadyCall> readyCalls = new ArrayList<>();

        @Override
        public void sendReady(UUID hostUUID, UUID sessionId, boolean ready, boolean useHostCamera,
                              String motionPackName, List<String> motionFiles) {
            readyCalls.add(new ReadyCall(hostUUID, sessionId, ready, useHostCamera, motionPackName,
                    motionFiles != null ? List.copyOf(motionFiles) : List.of()));
        }

        @Override
        public void sendStageInvite(UUID targetUUID, UUID sessionId) {
            invites.add(new InviteCall(targetUUID, sessionId));
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

        private ReadyCall lastReadyCall() {
            return readyCalls.isEmpty() ? null : readyCalls.get(readyCalls.size() - 1);
        }
    }

    private record InviteCall(UUID targetId, UUID sessionId) {
    }

    private record ReadyCall(UUID hostId, UUID sessionId, boolean ready, boolean useHostCamera,
                             String motionPackName, List<String> motionFiles) {
    }
}
