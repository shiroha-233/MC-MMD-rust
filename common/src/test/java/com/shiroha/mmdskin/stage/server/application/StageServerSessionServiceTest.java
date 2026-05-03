package com.shiroha.mmdskin.stage.server.application;

import com.shiroha.mmdskin.stage.domain.model.StageDescriptor;
import com.shiroha.mmdskin.stage.domain.model.StageInviteDecision;
import com.shiroha.mmdskin.stage.protocol.StagePacket;
import com.shiroha.mmdskin.stage.protocol.StagePacketType;
import com.shiroha.mmdskin.stage.server.application.port.StageServerPlatformPort;
import com.shiroha.mmdskin.stage.server.domain.model.StageServerPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 文件职责：验证服务端远端舞台广播的会话鉴权与旁观者可见性。 */
class StageServerSessionServiceTest {
    private final StageServerSessionService service = StageServerSessionService.getInstance();

    @AfterEach
    void tearDown() throws Exception {
        clearInternalState();
    }

    @Test
    void shouldBroadcastRemoteStagePacketsForAuthorizedSessionParticipant() {
        UUID hostId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID spectatorId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        FakePlatform platform = new FakePlatform(
                player(hostId, "Host"),
                player(memberId, "Member"),
                player(spectatorId, "Spectator")
        );

        establishAcceptedSession(platform, hostId, memberId, sessionId);

        StagePacket startPacket = new StagePacket(StagePacketType.REMOTE_STAGE_START);
        startPacket.sessionId = sessionId.toString();
        startPacket.descriptor = new StageDescriptor("pack_a", List.of("dance.vmd"), null, null);
        service.handlePacket(platform, platform.findPlayer(memberId), encode(startPacket));

        StagePacket stopPacket = new StagePacket(StagePacketType.REMOTE_STAGE_STOP);
        stopPacket.sessionId = sessionId.toString();
        service.handlePacket(platform, platform.findPlayer(memberId), encode(stopPacket));

        List<SentPacket> remotePackets = platform.remotePackets();
        assertEquals(4, remotePackets.size());
        assertTrue(remotePackets.stream().anyMatch(packet -> packet.targetPlayerId().equals(hostId)
                && packet.packet().type == StagePacketType.REMOTE_STAGE_START
                && sessionId.toString().equals(packet.packet().sessionId)));
        assertTrue(remotePackets.stream().anyMatch(packet -> packet.targetPlayerId().equals(spectatorId)
                && packet.packet().type == StagePacketType.REMOTE_STAGE_START
                && sessionId.toString().equals(packet.packet().sessionId)));
        assertTrue(remotePackets.stream().anyMatch(packet -> packet.targetPlayerId().equals(hostId)
                && packet.packet().type == StagePacketType.REMOTE_STAGE_STOP
                && sessionId.toString().equals(packet.packet().sessionId)));
        assertTrue(remotePackets.stream().anyMatch(packet -> packet.targetPlayerId().equals(spectatorId)
                && packet.packet().type == StagePacketType.REMOTE_STAGE_STOP
                && sessionId.toString().equals(packet.packet().sessionId)));
    }

    @Test
    void shouldIgnoreUnauthorizedOrMalformedRemoteStagePackets() {
        UUID hostId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID outsiderId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        FakePlatform platform = new FakePlatform(
                player(hostId, "Host"),
                player(memberId, "Member"),
                player(outsiderId, "Outsider")
        );

        establishAcceptedSession(platform, hostId, memberId, sessionId);
        int baselinePackets = platform.remotePackets().size();

        StagePacket unauthorizedPacket = new StagePacket(StagePacketType.REMOTE_STAGE_START);
        unauthorizedPacket.sessionId = sessionId.toString();
        unauthorizedPacket.descriptor = new StageDescriptor("pack_a", List.of("dance.vmd"), null, null);
        service.handlePacket(platform, platform.findPlayer(outsiderId), encode(unauthorizedPacket));

        StagePacket malformedPacket = new StagePacket(StagePacketType.REMOTE_STAGE_STOP);
        malformedPacket.sessionId = "not-a-uuid";
        service.handlePacket(platform, platform.findPlayer(memberId), encode(malformedPacket));

        assertEquals(baselinePackets, platform.remotePackets().size());
    }

    @Test
    void shouldIgnoreRemoteStagePacketsFromInvitedButInactiveMember() {
        UUID hostId = UUID.randomUUID();
        UUID invitedId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        FakePlatform platform = new FakePlatform(
                player(hostId, "Host"),
                player(invitedId, "Invited")
        );

        StagePacket invite = new StagePacket(StagePacketType.INVITE_REQUEST);
        invite.sessionId = sessionId.toString();
        invite.targetPlayerId = invitedId.toString();
        service.handlePacket(platform, platform.findPlayer(hostId), encode(invite));

        int baselinePackets = platform.remotePackets().size();

        StagePacket packet = new StagePacket(StagePacketType.REMOTE_STAGE_START);
        packet.sessionId = sessionId.toString();
        packet.descriptor = new StageDescriptor("pack_a", List.of("dance.vmd"), null, null);
        service.handlePacket(platform, platform.findPlayer(invitedId), encode(packet));

        assertEquals(baselinePackets, platform.remotePackets().size());
    }

    private void establishAcceptedSession(FakePlatform platform, UUID hostId, UUID memberId, UUID sessionId) {
        StagePacket invite = new StagePacket(StagePacketType.INVITE_REQUEST);
        invite.sessionId = sessionId.toString();
        invite.targetPlayerId = memberId.toString();
        service.handlePacket(platform, platform.findPlayer(hostId), encode(invite));

        StagePacket accept = new StagePacket(StagePacketType.INVITE_RESPONSE);
        accept.sessionId = sessionId.toString();
        accept.targetPlayerId = hostId.toString();
        accept.inviteDecision = StageInviteDecision.ACCEPT;
        service.handlePacket(platform, platform.findPlayer(memberId), encode(accept));
    }

    private String encode(StagePacket packet) {
        return com.shiroha.mmdskin.stage.protocol.StagePacketCodec.encode(packet);
    }

    @SuppressWarnings("unchecked")
    private void clearInternalState() throws Exception {
        Field sessionsField = StageServerSessionService.class.getDeclaredField("sessions");
        sessionsField.setAccessible(true);
        ((Map<UUID, ?>) sessionsField.get(service)).clear();

        Field playerSessionsField = StageServerSessionService.class.getDeclaredField("playerSessions");
        playerSessionsField.setAccessible(true);
        ((Map<UUID, ?>) playerSessionsField.get(service)).clear();
    }

    private static StageServerPlayer player(UUID uuid, String name) {
        return new StageServerPlayer(uuid, name);
    }

    private static final class FakePlatform implements StageServerPlatformPort {
        private final Map<UUID, StageServerPlayer> players = new LinkedHashMap<>();
        private final List<SentPacket> packets = new ArrayList<>();

        private FakePlatform(StageServerPlayer... players) {
            for (StageServerPlayer player : players) {
                this.players.put(player.getUuid(), player);
            }
        }

        @Override
        public StageServerPlayer findPlayer(UUID playerId) {
            return players.get(playerId);
        }

        @Override
        public List<StageServerPlayer> getOnlinePlayers() {
            return new ArrayList<>(players.values());
        }

        @Override
        public void sendPacket(UUID targetPlayerId, UUID sourcePlayerId, StagePacket packet) {
            packets.add(new SentPacket(targetPlayerId, sourcePlayerId, copyPacket(packet)));
        }

        private List<SentPacket> remotePackets() {
            return packets.stream()
                    .filter(packet -> packet.packet().type == StagePacketType.REMOTE_STAGE_START
                            || packet.packet().type == StagePacketType.REMOTE_STAGE_STOP)
                    .toList();
        }

        private StagePacket copyPacket(StagePacket packet) {
            StagePacket copy = new StagePacket(packet.type);
            copy.version = packet.version;
            copy.sessionId = packet.sessionId;
            copy.targetPlayerId = packet.targetPlayerId;
            copy.inviteDecision = packet.inviteDecision;
            copy.ready = packet.ready;
            copy.cameraMode = packet.cameraMode;
            copy.frame = packet.frame;
            copy.heightOffset = packet.heightOffset;
            copy.descriptor = packet.descriptor != null ? packet.descriptor.copy() : null;
            copy.motionPackName = packet.motionPackName;
            copy.motionFiles = packet.motionFiles != null ? List.copyOf(packet.motionFiles) : List.of();
            copy.members = packet.members != null ? List.copyOf(packet.members) : List.of();
            return copy;
        }
    }

    private record SentPacket(UUID targetPlayerId, UUID sourcePlayerId, StagePacket packet) {
    }
}
