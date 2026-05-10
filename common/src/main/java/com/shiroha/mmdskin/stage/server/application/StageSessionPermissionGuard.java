package com.shiroha.mmdskin.stage.server.application;

import com.shiroha.mmdskin.stage.server.domain.model.StageServerSession;
import com.shiroha.mmdskin.stage.server.domain.model.StageServerSessionMember;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 文件职责：校验服务端舞台会话的操作权限，并提供输入净化工具。 */
final class StageSessionPermissionGuard {

    private final Map<UUID, StageServerSession> sessions;
    private final Map<UUID, UUID> playerSessions;

    StageSessionPermissionGuard(Map<UUID, StageServerSession> sessions,
                                Map<UUID, UUID> playerSessions) {
        this.sessions = sessions;
        this.playerSessions = playerSessions;
    }

    StageServerSession requireHostSession(UUID senderUUID, UUID sessionId) {
        if (sessionId == null) return null;
        StageServerSession session = sessions.get(sessionId);
        if (session == null || !session.getHostId().equals(senderUUID)) return null;
        return session;
    }

    StageServerSession requireMemberSession(UUID senderUUID, UUID sessionId) {
        if (sessionId == null) return null;
        UUID indexed = playerSessions.get(senderUUID);
        if (!Objects.equals(indexed, sessionId)) return null;
        return sessions.get(sessionId);
    }

    StageServerSession requireActiveSessionParticipant(UUID senderUUID, UUID sessionId) {
        StageServerSession session = requireMemberSession(senderUUID, sessionId);
        if (session == null) return null;
        StageServerSessionMember member = session.getMembers().get(senderUUID);
        if (member == null) return null;
        if (senderUUID.equals(session.getHostId())) return session;
        return member.getState().isAcceptedState() ? session : null;
    }

    List<String> sanitizeMotionFiles(List<String> motionFiles) {
        if (motionFiles == null || motionFiles.isEmpty()) return List.of();
        LinkedHashSet<String> sanitized = new LinkedHashSet<>();
        for (String f : motionFiles) {
            if (isSafeName(f)) sanitized.add(f);
        }
        return sanitized.isEmpty() ? List.of() : List.copyOf(sanitized);
    }

    boolean isSafeName(String value) {
        return value != null && !value.isEmpty()
                && !value.contains("..")
                && !value.contains("/")
                && !value.contains("\\");
    }

    UUID parseUUID(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
