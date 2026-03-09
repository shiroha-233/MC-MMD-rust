package com.shiroha.mmdskin.stage.server.application;

import com.shiroha.mmdskin.stage.domain.model.StageCameraMode;
import com.shiroha.mmdskin.stage.domain.model.StageDescriptor;
import com.shiroha.mmdskin.stage.domain.model.StageMemberState;
import com.shiroha.mmdskin.stage.protocol.StageMemberSnapshot;
import com.shiroha.mmdskin.stage.protocol.StagePacket;
import com.shiroha.mmdskin.stage.protocol.StagePacketCodec;
import com.shiroha.mmdskin.stage.protocol.StagePacketType;
import com.shiroha.mmdskin.stage.server.application.port.StageServerPlatformPort;
import com.shiroha.mmdskin.stage.server.domain.model.StageServerPlayer;
import com.shiroha.mmdskin.stage.server.domain.model.StageServerSession;
import com.shiroha.mmdskin.stage.server.domain.model.StageServerSessionMember;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StageServerSessionService {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final StageServerSessionService INSTANCE = new StageServerSessionService();

    private final Map<UUID, StageServerSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerSessions = new ConcurrentHashMap<>();

    private StageServerSessionService() {
    }

    public static StageServerSessionService getInstance() {
        return INSTANCE;
    }

    public synchronized void handlePacket(StageServerPlatformPort platform, StageServerPlayer sender, String rawData) {
        StagePacket packet = StagePacketCodec.decode(rawData);
        if (packet == null) {
            LOGGER.warn("[多人舞台] 服务端收到无效协议数据包");
            return;
        }

        switch (packet.type) {
            case INVITE_REQUEST -> handleInviteRequest(platform, sender, packet);
            case INVITE_CANCEL -> handleInviteCancel(platform, sender, packet);
            case INVITE_RESPONSE -> handleInviteResponse(platform, sender, packet);
            case READY_UPDATE -> handleReadyUpdate(platform, sender, packet);
            case MEMBER_LEAVE -> handleMemberLeave(platform, sender, packet);
            case SESSION_DISSOLVE -> handleSessionDissolve(platform, sender, packet);
            case PLAYBACK_START -> handlePlaybackStart(platform, sender, packet);
            case PLAYBACK_STOP -> handlePlaybackStop(platform, sender, packet);
            case FRAME_SYNC -> handleFrameSync(platform, sender, packet);
            case REMOTE_STAGE_START -> handleRemoteStageStart(platform, sender, packet);
            case REMOTE_STAGE_STOP -> handleRemoteStageStop(platform, sender, packet);
            case SESSION_STATE -> LOGGER.warn("[多人舞台] 客户端不应主动发送 SESSION_STATE");
            default -> LOGGER.warn("[多人舞台] 未处理的数据包类型: {}", packet.type);
        }
    }

    public synchronized void onPlayerDisconnect(StageServerPlatformPort platform, UUID playerUUID) {
        UUID sessionId = playerSessions.remove(playerUUID);
        if (sessionId == null) {
            return;
        }

        StageServerSession session = sessions.get(sessionId);
        if (session == null) {
            return;
        }

        if (playerUUID.equals(session.getHostId())) {
            dissolveSession(platform, session, true);
            return;
        }

        session.getMembers().remove(playerUUID);
        broadcastSessionState(platform, session);
        cleanupIfEmpty(session);
    }

    private void handleInviteRequest(StageServerPlatformPort platform, StageServerPlayer sender, StagePacket packet) {
        UUID sessionId = parseUUID(packet.sessionId);
        UUID targetUUID = parseUUID(packet.targetPlayerId);
        if (sessionId == null || targetUUID == null) {
            return;
        }

        StageServerSession session = ensureHostSession(platform, sender, sessionId);
        if (session == null) {
            return;
        }

        StageServerPlayer target = platform.findPlayer(targetUUID);
        if (target == null) {
            return;
        }

        session.getMembers().put(targetUUID, new StageServerSessionMember(
                targetUUID,
                target.getName(),
                StageMemberState.INVITED,
                StageCameraMode.HOST_CAMERA
        ));
        platform.sendPacket(targetUUID, sender.getUuid(), packet);
        broadcastSessionState(platform, session);
    }

    private void handleInviteCancel(StageServerPlatformPort platform, StageServerPlayer sender, StagePacket packet) {
        StageServerSession session = requireHostSession(sender.getUuid(), parseUUID(packet.sessionId));
        UUID targetUUID = parseUUID(packet.targetPlayerId);
        if (session == null || targetUUID == null) {
            return;
        }

        StageServerSessionMember member = session.getMembers().get(targetUUID);
        if (member == null || member.getState() != StageMemberState.INVITED) {
            return;
        }

        session.getMembers().remove(targetUUID);
        if (platform.findPlayer(targetUUID) != null) {
            platform.sendPacket(targetUUID, sender.getUuid(), packet);
        }
        broadcastSessionState(platform, session);
    }

    private void handleInviteResponse(StageServerPlatformPort platform, StageServerPlayer sender, StagePacket packet) {
        UUID sessionId = parseUUID(packet.sessionId);
        UUID hostUUID = parseUUID(packet.targetPlayerId);
        if (sessionId == null || hostUUID == null || packet.inviteDecision == null) {
            return;
        }

        StageServerSession session = sessions.get(sessionId);
        if (session == null || !session.getHostId().equals(hostUUID)) {
            return;
        }

        StageServerSessionMember member = session.getMembers().get(sender.getUuid());
        if (member == null) {
            member = new StageServerSessionMember(sender.getUuid(), sender.getName(),
                    StageMemberState.INVITED, StageCameraMode.HOST_CAMERA);
            session.getMembers().put(sender.getUuid(), member);
        }

        switch (packet.inviteDecision) {
            case ACCEPT -> {
                member.setState(StageMemberState.ACCEPTED);
                member.setCameraMode(StageCameraMode.HOST_CAMERA);
                playerSessions.put(sender.getUuid(), sessionId);
            }
            case DECLINE -> {
                member.setState(StageMemberState.DECLINED);
                playerSessions.remove(sender.getUuid());
            }
            case BUSY -> {
                member.setState(StageMemberState.BUSY);
                playerSessions.remove(sender.getUuid());
            }
        }

        broadcastSessionState(platform, session);
    }

    private void handleReadyUpdate(StageServerPlatformPort platform, StageServerPlayer sender, StagePacket packet) {
        UUID sessionId = parseUUID(packet.sessionId);
        StageServerSession session = requireMemberSession(sender.getUuid(), sessionId);
        if (session == null) {
            return;
        }

        StageServerSessionMember member = session.getMembers().get(sender.getUuid());
        if (member == null || !member.getState().isAcceptedState()) {
            return;
        }

        member.setCameraMode(packet.cameraMode != null ? packet.cameraMode : StageCameraMode.HOST_CAMERA);
        member.setMotionPackName(isSafeName(packet.motionPackName) ? packet.motionPackName : null);
        member.setMotionFiles(sanitizeMotionFiles(packet.motionFiles));
        member.setState(Boolean.TRUE.equals(packet.ready) ? StageMemberState.READY : StageMemberState.ACCEPTED);
        broadcastSessionState(platform, session);
    }

    private void handleMemberLeave(StageServerPlatformPort platform, StageServerPlayer sender, StagePacket packet) {
        UUID sessionId = parseUUID(packet.sessionId);
        StageServerSession session = requireMemberSession(sender.getUuid(), sessionId);
        if (session == null) {
            return;
        }

        session.getMembers().remove(sender.getUuid());
        playerSessions.remove(sender.getUuid());
        broadcastSessionState(platform, session);
        cleanupIfEmpty(session);
    }

    private void handleSessionDissolve(StageServerPlatformPort platform, StageServerPlayer sender, StagePacket packet) {
        StageServerSession session = requireHostSession(sender.getUuid(), parseUUID(packet.sessionId));
        if (session == null) {
            return;
        }
        dissolveSession(platform, session, true);
    }

    private void handlePlaybackStart(StageServerPlatformPort platform, StageServerPlayer sender, StagePacket packet) {
        StageServerSession session = requireHostSession(sender.getUuid(), parseUUID(packet.sessionId));
        if (session == null || packet.descriptor == null || !packet.descriptor.isValid()) {
            return;
        }

        UUID targetUUID = parseUUID(packet.targetPlayerId);
        if (targetUUID != null) {
            StageServerSessionMember member = session.getMembers().get(targetUUID);
            if (member == null || !member.getState().isAcceptedState()) {
                return;
            }
            if (platform.findPlayer(targetUUID) != null) {
                platform.sendPacket(targetUUID, sender.getUuid(), resolveMemberPlaybackPacket(packet, member));
            }
            return;
        }

        for (StageServerSessionMember member : session.getMembers().values()) {
            if (member.getUuid().equals(session.getHostId()) || !member.getState().isAcceptedState()) {
                continue;
            }
            if (platform.findPlayer(member.getUuid()) != null) {
                platform.sendPacket(member.getUuid(), sender.getUuid(), resolveMemberPlaybackPacket(packet, member));
            }
        }
    }

    private void handlePlaybackStop(StageServerPlatformPort platform, StageServerPlayer sender, StagePacket packet) {
        StageServerSession session = requireHostSession(sender.getUuid(), parseUUID(packet.sessionId));
        if (session == null) {
            return;
        }

        UUID targetUUID = parseUUID(packet.targetPlayerId);
        if (targetUUID != null) {
            if (platform.findPlayer(targetUUID) != null) {
                platform.sendPacket(targetUUID, sender.getUuid(), packet);
            }
            return;
        }

        for (StageServerSessionMember member : session.getMembers().values()) {
            if (member.getUuid().equals(session.getHostId()) || !member.getState().isAcceptedState()) {
                continue;
            }
            if (platform.findPlayer(member.getUuid()) != null) {
                platform.sendPacket(member.getUuid(), sender.getUuid(), packet);
            }
        }
    }

    private void handleRemoteStageStart(StageServerPlatformPort platform, StageServerPlayer sender, StagePacket packet) {
        if (packet.descriptor == null || !packet.descriptor.isValid()) {
            return;
        }
        broadcastRemotePacket(platform, sender.getUuid(), packet);
    }

    private void handleRemoteStageStop(StageServerPlatformPort platform, StageServerPlayer sender, StagePacket packet) {
        broadcastRemotePacket(platform, sender.getUuid(), packet);
    }

    private void handleFrameSync(StageServerPlatformPort platform, StageServerPlayer sender, StagePacket packet) {
        StageServerSession session = requireHostSession(sender.getUuid(), parseUUID(packet.sessionId));
        if (session == null || packet.frame == null) {
            return;
        }

        for (StageServerSessionMember member : session.getMembers().values()) {
            if (member.getUuid().equals(session.getHostId()) || !member.getState().isAcceptedState()) {
                continue;
            }
            if (platform.findPlayer(member.getUuid()) != null) {
                platform.sendPacket(member.getUuid(), sender.getUuid(), packet);
            }
        }
    }

    private StageServerSession ensureHostSession(StageServerPlatformPort platform, StageServerPlayer sender, UUID sessionId) {
        UUID senderUUID = sender.getUuid();
        UUID existingSessionId = playerSessions.get(senderUUID);
        if (existingSessionId != null && !existingSessionId.equals(sessionId)) {
            StageServerSession existingSession = sessions.get(existingSessionId);
            if (existingSession != null && senderUUID.equals(existingSession.getHostId())) {
                dissolveSession(platform, existingSession, true);
            } else {
                return null;
            }
        }

        StageServerSession session = sessions.computeIfAbsent(sessionId, id -> new StageServerSession(id, senderUUID));
        if (!session.getHostId().equals(senderUUID)) {
            return null;
        }

        playerSessions.put(senderUUID, sessionId);
        session.getMembers().put(senderUUID, new StageServerSessionMember(
                senderUUID,
                sender.getName(),
                StageMemberState.HOST,
                StageCameraMode.HOST_CAMERA
        ));
        return session;
    }

    private StageServerSession requireHostSession(UUID senderUUID, UUID sessionId) {
        if (sessionId == null) {
            return null;
        }
        StageServerSession session = sessions.get(sessionId);
        if (session == null || !session.getHostId().equals(senderUUID)) {
            return null;
        }
        return session;
    }

    private StageServerSession requireMemberSession(UUID senderUUID, UUID sessionId) {
        if (sessionId == null) {
            return null;
        }
        UUID indexedSession = playerSessions.get(senderUUID);
        if (!Objects.equals(indexedSession, sessionId)) {
            return null;
        }
        return sessions.get(sessionId);
    }

    private void broadcastSessionState(StageServerPlatformPort platform, StageServerSession session) {
        StagePacket packet = new StagePacket(StagePacketType.SESSION_STATE);
        packet.sessionId = session.getSessionId().toString();
        packet.members = buildSnapshots(session);

        if (platform.findPlayer(session.getHostId()) != null) {
            platform.sendPacket(session.getHostId(), session.getHostId(), packet);
        }

        for (StageServerSessionMember member : session.getMembers().values()) {
            if (member.getUuid().equals(session.getHostId()) || !member.getState().isAcceptedState()) {
                continue;
            }
            if (platform.findPlayer(member.getUuid()) != null) {
                platform.sendPacket(member.getUuid(), session.getHostId(), packet);
            }
        }
    }

    private List<StageMemberSnapshot> buildSnapshots(StageServerSession session) {
        List<StageServerSessionMember> ordered = new ArrayList<>(session.getMembers().values());
        ordered.sort(Comparator
                .comparingInt((StageServerSessionMember member) -> member.getState() == StageMemberState.HOST ? 0 : 1)
                .thenComparing(member -> member.getName(), String.CASE_INSENSITIVE_ORDER));

        List<StageMemberSnapshot> snapshots = new ArrayList<>();
        for (StageServerSessionMember member : ordered) {
            snapshots.add(new StageMemberSnapshot(
                    member.getUuid().toString(),
                    member.getName(),
                    member.getState().name(),
                    member.getCameraMode().name()
            ));
        }
        return snapshots;
    }

    private void dissolveSession(StageServerPlatformPort platform, StageServerSession session, boolean notifyMembers) {
        if (notifyMembers) {
            StagePacket packet = new StagePacket(StagePacketType.SESSION_DISSOLVE);
            packet.sessionId = session.getSessionId().toString();
            for (StageServerSessionMember member : session.getMembers().values()) {
                if (member.getUuid().equals(session.getHostId())) {
                    continue;
                }
                if (platform.findPlayer(member.getUuid()) != null) {
                    platform.sendPacket(member.getUuid(), session.getHostId(), packet);
                }
            }
        }

        for (StageServerSessionMember member : session.getMembers().values()) {
            playerSessions.remove(member.getUuid());
        }
        sessions.remove(session.getSessionId());
    }

    private void cleanupIfEmpty(StageServerSession session) {
        boolean hasAnyGuest = session.getMembers().values().stream()
                .anyMatch(member -> !member.getUuid().equals(session.getHostId()));
        if (!hasAnyGuest) {
            sessions.remove(session.getSessionId());
            playerSessions.remove(session.getHostId());
        }
    }

    private void broadcastRemotePacket(StageServerPlatformPort platform, UUID senderUUID, StagePacket packet) {
        for (StageServerPlayer target : platform.getOnlinePlayers()) {
            if (!target.getUuid().equals(senderUUID)) {
                platform.sendPacket(target.getUuid(), senderUUID, packet);
            }
        }
    }

    private StagePacket resolveMemberPlaybackPacket(StagePacket packet, StageServerSessionMember member) {
        if (packet.descriptor == null || member.getMotionFiles().isEmpty()) {
            return packet;
        }

        StagePacket resolvedPacket = copyPacket(packet);
        StageDescriptor descriptor = packet.descriptor.copy();
        descriptor.setMotionFiles(member.getMotionFiles());
        resolvedPacket.descriptor = descriptor;
        resolvedPacket.motionPackName = member.getMotionPackName();
        return resolvedPacket;
    }

    private StagePacket copyPacket(StagePacket source) {
        StagePacket copy = new StagePacket(source.type);
        copy.version = source.version;
        copy.sessionId = source.sessionId;
        copy.targetPlayerId = source.targetPlayerId;
        copy.inviteDecision = source.inviteDecision;
        copy.ready = source.ready;
        copy.cameraMode = source.cameraMode;
        copy.frame = source.frame;
        copy.heightOffset = source.heightOffset;
        copy.descriptor = source.descriptor != null ? source.descriptor.copy() : null;
        copy.motionPackName = source.motionPackName;
        copy.motionFiles = source.motionFiles != null ? List.copyOf(source.motionFiles) : List.of();
        copy.members = source.members != null ? List.copyOf(source.members) : List.of();
        return copy;
    }

    private List<String> sanitizeMotionFiles(List<String> motionFiles) {
        if (motionFiles == null || motionFiles.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> sanitized = new LinkedHashSet<>();
        for (String motionFile : motionFiles) {
            if (isSafeName(motionFile)) {
                sanitized.add(motionFile);
            }
        }
        return sanitized.isEmpty() ? List.of() : List.copyOf(sanitized);
    }

    private boolean isSafeName(String value) {
        return value != null && !value.isEmpty()
                && !value.contains("..")
                && !value.contains("/")
                && !value.contains("\\");
    }

    private UUID parseUUID(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
